from dataclasses import dataclass, field
import importlib
import importlib.util
from pathlib import Path
import sys
import threading
import time
from types import ModuleType
from typing import Any

from api import ApiClient
from junitxml import JUnitReport
from messagetests import MessageTestResult, run_message_tests_with_report

CHANNEL_START_TIMEOUT_SECONDS = 15
HOOK_TIMEOUT_SECONDS = 15


@dataclass(frozen=True)
class ChannelFixture:
    test_dir: Path
    channel_dir: Path
    channel_file: Path
    channel_id: str
    channel_name: str


@dataclass(frozen=True)
class ProvisionedChannel:
    fixture: ChannelFixture
    channel_id: str


@dataclass(frozen=True)
class TestRun:
    test_dir: Path
    channels: list[ChannelFixture] = field(default_factory=list)


@dataclass
class TestRunContext:
    test_run: TestRun
    provisioned_channels: list[ProvisionedChannel] = field(default_factory=list)
    message_results: list[MessageTestResult] = field(default_factory=list)


def resolve_tests_root(workspace: str, tests_root: str) -> Path:
    candidate = Path(tests_root)
    if candidate.is_absolute():
        return candidate
    return Path(workspace) / candidate


def parse_channel_fixture(channel_file: Path) -> ChannelFixture:
    channel_id = parse_channel_id(channel_file)
    return ChannelFixture(
        test_dir=channel_file.parents[2],
        channel_dir=channel_file.parent,
        channel_file=channel_file,
        channel_id=channel_id,
        channel_name=channel_file.parent.name,
    )


def parse_channel_id(channel_file: Path) -> str:
    etree = importlib.import_module("lxml.etree")
    document = etree.parse(str(channel_file))
    channel_id = document.getroot().findtext("id")
    if channel_id is None or not channel_id.strip():
        raise RuntimeError(f"Channel fixture is missing id: {channel_file}")
    return channel_id.strip()


def test_runs_for_configuration(test_dir: Path, configuration: str) -> bool:
    configurations_file = test_dir / "configurations"
    if not configurations_file.exists():
        return True

    configurations = [line.strip() for line in configurations_file.read_text(encoding="utf-8").splitlines() if line.strip()]
    return configuration in configurations


def discover_channels(tests_root: Path, configuration: str) -> list[ChannelFixture]:
    if not tests_root.exists():
        return []

    fixtures: list[ChannelFixture] = []
    for channel_file in sorted(tests_root.glob("**/channels/*/channel.xml")):
        fixture = parse_channel_fixture(channel_file)
        if test_runs_for_configuration(fixture.test_dir, configuration):
            fixtures.append(fixture)
    return fixtures


def discover_test_runs(tests_root: Path, configuration: str) -> list[TestRun]:
    runs_by_dir: dict[Path, list[ChannelFixture]] = {}
    for fixture in discover_channels(tests_root, configuration):
        runs_by_dir.setdefault(fixture.test_dir, []).append(fixture)

    return [TestRun(test_dir=test_dir, channels=sorted(fixtures, key=lambda fixture: fixture.channel_dir)) for test_dir, fixtures in sorted(runs_by_dir.items())]


def run_channel_tests(
    client: ApiClient,
    test_runs: list[TestRun],
    timeout_seconds: int,
    report: JUnitReport,
    keep_alive: bool = False,
) -> list[MessageTestResult]:
    all_message_results: list[MessageTestResult] = []

    for test_run in test_runs:
        context = TestRunContext(test_run=test_run)
        hooks = load_test_hooks(test_run.test_dir)
        print(f"Running test {test_run.test_dir.name}", flush=True)
        try:
            run_reported_hook(report, test_run, "startup", hooks, client, context)
            context.provisioned_channels = deploy_channel_fixtures_with_report(report, test_run, client, test_run.channels)
            run_reported_hook(report, test_run, "postDeploy", hooks, client, context)
            context.message_results = run_message_tests_with_report(client, context.provisioned_channels, timeout_seconds, report)
            run_reported_hook(report, test_run, "postRun", hooks, client, context)
            all_message_results.extend(context.message_results)
        finally:
            if keep_alive:
                print(f"Preserving deployed test state for {test_run.test_dir.name} because keep-alive is enabled.", flush=True)
            else:
                try:
                    run_reported_hook(report, test_run, "teardown", hooks, client, context)
                finally:
                    cleanup_channel_tests(client, context.provisioned_channels)

    return all_message_results


def deploy_channel_fixtures(client: ApiClient, channel_fixtures: list[ChannelFixture]) -> list[ProvisionedChannel]:
    provisioned_channels: list[ProvisionedChannel] = []

    for fixture in channel_fixtures:
        print(f"Creating channel {fixture.channel_name} from {fixture.channel_file}", flush=True)
        client.create_channel(fixture.channel_file.read_bytes())
        print(f"Deploying channel {fixture.channel_name} ({fixture.channel_id})", flush=True)
        client.deploy_channel(fixture.channel_id)
        wait_for_channel_started(client, fixture)
        provisioned_channels.append(ProvisionedChannel(fixture=fixture, channel_id=fixture.channel_id))

    return provisioned_channels


def deploy_channel_fixtures_with_report(
    report: JUnitReport,
    test_run: TestRun,
    client: ApiClient,
    channel_fixtures: list[ChannelFixture],
) -> list[ProvisionedChannel]:
    provisioned_channels: list[ProvisionedChannel] = []

    for fixture in channel_fixtures:
        testcase_name = f"{test_run.test_dir.name}/{fixture.channel_name}/deploy"
        classname = f"{test_run.test_dir.name}.{fixture.channel_name}"
        provisioned_channels.append(
            report.run_case(
                testcase_name,
                classname,
                lambda fixture=fixture: deploy_single_channel_fixture(client, fixture),
            )
        )

    return provisioned_channels


def deploy_single_channel_fixture(client: ApiClient, fixture: ChannelFixture) -> ProvisionedChannel:
    print(f"Creating channel {fixture.channel_name} from {fixture.channel_file}", flush=True)
    client.create_channel(fixture.channel_file.read_bytes())
    print(f"Deploying channel {fixture.channel_name} ({fixture.channel_id})", flush=True)
    client.deploy_channel(fixture.channel_id)
    wait_for_channel_started(client, fixture)
    return ProvisionedChannel(fixture=fixture, channel_id=fixture.channel_id)


def wait_for_channel_started(client: ApiClient, fixture: ChannelFixture, timeout_seconds: int = CHANNEL_START_TIMEOUT_SECONDS) -> None:
    deadline = time.monotonic() + timeout_seconds
    last_state: str | None = None

    while time.monotonic() < deadline:
        status_xml = client.get_channel_status(fixture.channel_id)
        state = dashboard_status_state(status_xml)
        if state == "STARTED":
            return
        last_state = state
        time.sleep(1)

    raise RuntimeError(
        f"Timed out waiting for channel {fixture.channel_name} ({fixture.channel_id}) to start; last state was {last_state}"
    )


def dashboard_status_state(status_xml) -> str | None:
    values = status_xml.xpath("./state/text()")
    if not values:
        return None
    return str(values[0]).strip()


def deploy_channel_tests(client: ApiClient, channel_fixtures: list[ChannelFixture]) -> list[ProvisionedChannel]:
    return deploy_channel_fixtures(client, channel_fixtures)


def cleanup_channel_tests(client: ApiClient, provisioned_channels: list[ProvisionedChannel]) -> None:
    for provisioned in reversed(provisioned_channels):
        safe_cleanup_channel("undeploy", undeploy_channel, client, provisioned)

    for provisioned in reversed(provisioned_channels):
        safe_cleanup_channel("remove", remove_channel, client, provisioned)


def undeploy_channel(client: ApiClient, provisioned: ProvisionedChannel) -> None:
    print(f"Undeploying channel {provisioned.fixture.channel_name} ({provisioned.channel_id})", flush=True)
    client.undeploy_channel(provisioned.channel_id)


def remove_channel(client: ApiClient, provisioned: ProvisionedChannel) -> None:
    print(f"Removing channel {provisioned.fixture.channel_name} ({provisioned.channel_id})", flush=True)
    client.remove_channel(provisioned.channel_id)


def safe_cleanup_channel(action: str, cleanup, client: ApiClient, provisioned: ProvisionedChannel) -> None:
    try:
        cleanup(client, provisioned)
    except Exception as error:
        print(f"Ignoring {action} failure for {provisioned.fixture.channel_name}: {error}", file=sys.stderr, flush=True)


def load_test_hooks(test_dir: Path) -> Any:
    hook_file = test_dir / "test.py"
    if not hook_file.exists():
        return None

    module = load_module_from_file(hook_file, f"test_hooks_{test_dir.name}")
    hook_class = getattr(module, "Hooks", None) or getattr(module, "TestHooks", None)
    if hook_class is not None:
        return hook_class()
    return module


def invoke_hook(hooks: Any, hook_name: str, client: ApiClient, context: TestRunContext) -> None:
    if hooks is None:
        return

    hook = getattr(hooks, hook_name, None)
    if callable(hook):
        invoke_callable(hook, client, context)


def run_reported_hook(
    report: JUnitReport,
    test_run: TestRun,
    hook_name: str,
    hooks: Any,
    client: ApiClient,
    context: TestRunContext,
) -> None:
    if hooks is None:
        return

    hook = getattr(hooks, hook_name, None)
    if not callable(hook):
        return

    testcase_name = f"{test_run.test_dir.name}/{hook_name}"
    classname = f"{test_run.test_dir.name}.hooks"
    report.run_case(testcase_name, classname, lambda: invoke_callable(hook, client, context))


def invoke_callable(callable_obj: Any, client: ApiClient, context: TestRunContext) -> None:
    result: dict[str, BaseException | None] = {"error": None}

    def run_hook() -> None:
        try:
            try:
                callable_obj(client, context)
            except TypeError:
                try:
                    callable_obj(client)
                except TypeError:
                    callable_obj()
        except BaseException as error:
            result["error"] = error

    thread = threading.Thread(target=run_hook, daemon=True)
    thread.start()
    thread.join(HOOK_TIMEOUT_SECONDS)

    if thread.is_alive():
        raise RuntimeError(f"Hook timed out after {HOOK_TIMEOUT_SECONDS} seconds")

    if result["error"] is not None:
        raise result["error"]


def load_module_from_file(module_file: Path, module_name: str) -> ModuleType:
    spec = importlib.util.spec_from_file_location(module_name, module_file)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load module from {module_file}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

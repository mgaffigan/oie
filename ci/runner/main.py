import argparse
import json
import os
import sys
from pathlib import Path

from api import login_or_fail
from channeltests import (
    ChannelFixture,
    discover_channels,
    discover_test_runs,
    resolve_tests_root,
    run_channel_tests,
)
from compose import compose_down, compose_up, sanitize_project_name
from junitxml import JUnitReport

MAX_OPERATION_TIMEOUT_SECONDS = 90
DEFAULT_TIMEOUT_SECONDS = MAX_OPERATION_TIMEOUT_SECONDS
DEFAULT_BASE_URL = "https://host.docker.internal:8443"
DEFAULT_USERNAME = "admin"
DEFAULT_PASSWORD = "admin"
DEFAULT_TESTS_ROOT = "ci/tests"
DEFAULT_RESULTS_ROOT = "ci/test-results"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Boot and tear down an OIE docker-compose test configuration.")
    parser.add_argument("--workspace", default="/workspace", help="Workspace root containing ci/configurations.")
    parser.add_argument("--configuration", help="Configuration name mapped to ci/configurations/<name>.compose.yml.")
    parser.add_argument("--compose-file", help="Explicit compose file path. Overrides --configuration.")
    parser.add_argument("--server-image", required=True, help="Server image tag to inject into the compose environment.")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help="Base URL used for readiness and login checks.")
    parser.add_argument("--username", default=DEFAULT_USERNAME, help="REST username.")
    parser.add_argument("--password", default=DEFAULT_PASSWORD, help="REST password.")
    parser.add_argument("--tests-root", default=DEFAULT_TESTS_ROOT, help="Root directory containing test fixtures.")
    parser.add_argument("--results-root", default=DEFAULT_RESULTS_ROOT, help="Directory where JUnit XML results are written.")
    parser.add_argument("--timeout", type=int, default=DEFAULT_TIMEOUT_SECONDS, help="Timeout in seconds for any harness operation. Capped at 90 seconds.")
    parser.add_argument("--keep-alive", action="store_true", help="Leave the compose stack running after a successful check.")
    args = parser.parse_args()
    args.timeout = normalize_timeout(args.timeout)
    return args


def normalize_timeout(timeout_seconds: int) -> int:
    return max(1, min(timeout_seconds, MAX_OPERATION_TIMEOUT_SECONDS))


def resolve_compose_file(args: argparse.Namespace) -> Path:
    if args.compose_file:
        compose_file = Path(args.compose_file)
        if not compose_file.is_absolute():
            compose_file = Path(args.workspace) / compose_file
        return compose_file

    if not args.configuration:
        raise ValueError("Either --configuration or --compose-file must be provided.")

    return Path(args.workspace) / "ci" / "configurations" / f"{args.configuration}.compose.yml"


def build_run_summary(
    compose_file: Path,
    config_name: str,
    project_name: str,
    server_image: str,
    tests_root: Path,
    channel_fixtures: list[ChannelFixture],
    base_url: str,
    keep_alive: bool,
) -> dict[str, object]:
    return {
        "compose_file": str(compose_file),
        "configuration": config_name,
        "project_name": project_name,
        "server_image": server_image,
        "tests_root": str(tests_root),
        "channels": [str(fixture.channel_file.relative_to(tests_root)) for fixture in channel_fixtures],
        "base_url": base_url,
        "keep_alive": keep_alive,
    }


def main() -> int:
    args = parse_args()
    compose_file = resolve_compose_file(args)
    tests_root = resolve_tests_root(args.workspace, args.tests_root)
    results_root = resolve_tests_root(args.workspace, args.results_root)
    if not compose_file.exists():
        raise FileNotFoundError(f"Compose file not found: {compose_file}")

    config_name = args.configuration or compose_file.stem.replace(".compose", "")
    test_runs = discover_test_runs(tests_root, config_name)
    channel_fixtures = discover_channels(tests_root, config_name)
    project_name = sanitize_project_name(config_name)
    env = os.environ.copy()
    env["OIE_IMAGE"] = args.server_image
    report = JUnitReport(suite_name=config_name)
    results_file = results_root / f"{config_name}.xml"

    print(
        json.dumps(
            build_run_summary(
                compose_file,
                config_name,
                project_name,
                args.server_image,
                tests_root,
                channel_fixtures,
                args.base_url,
                args.keep_alive,
            ),
            indent=2,
        ),
        flush=True,
    )

    compose_attempted = False
    client = None
    message_results = []
    teardown_error = None
    try:
        compose_attempted = True
        report.run_case(
            f"{config_name}/setup",
            config_name,
            lambda: compose_up(compose_file, project_name, env, args.timeout),
        )
        client = report.run_case(
            f"{config_name}/login",
            config_name,
            lambda: login_or_fail(args.base_url, args.username, args.password, args.timeout),
        )
        message_results = run_channel_tests(client, test_runs, args.timeout, report, keep_alive=args.keep_alive)

        print(
            f"Configuration boot completed. Ran {len(test_runs)} test(s) and validated {len(message_results)} message(s).",
            flush=True,
        )
        return 0
    finally:
        if compose_attempted and not args.keep_alive:
            try:
                report.run_case(
                    f"{config_name}/teardown",
                    config_name,
                    lambda: compose_down(compose_file, project_name, env),
                )
            except Exception as error:
                print(f"Teardown failed: {error}", file=sys.stderr, flush=True)
                teardown_error = error

        report.write_xml(results_file)
        print(f"Wrote JUnit test results to {results_file}", flush=True)

        if teardown_error is not None:
            raise RuntimeError("Compose teardown failed") from teardown_error

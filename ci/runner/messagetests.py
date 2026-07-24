from dataclasses import dataclass
import importlib.util
from pathlib import Path
import re
import time
from types import ModuleType
from typing import TYPE_CHECKING, Any

from api import ApiClient
from junitxml import JUnitReport

if TYPE_CHECKING:
    from channeltests import ProvisionedChannel

ANY_WILDCARD = b"((ANY))"
PENDING_STATUSES = {"PENDING", "QUEUED"}


@dataclass(frozen=True)
class MessageFixture:
    message_dir: Path
    name: str
    source_file: Path
    source_sourcemap_file: Path | None


@dataclass(frozen=True)
class MessageTestResult:
    provisioned_channel: "ProvisionedChannel"
    fixture: MessageFixture
    message_id: int
    message_xml: Any

    def connector_message(self, meta_data_id: int):
        connector_message = self.message_xml.xpath(
            f"./connectorMessages/entry[int = '{meta_data_id}']/connectorMessage"
        )
        if not connector_message:
            raise AssertionError(
                f"Message {self.message_id} is missing connector metadata id {meta_data_id} for {self.fixture.name}"
            )
        return connector_message[0]

    def source_connector(self):
        return self.connector_message(0)

    def destination_connector(self, destination_number: int):
        return self.connector_message(destination_number)

    def xml_text(self) -> str:
        etree = importlib.import_module("lxml.etree")
        return etree.tostring(self.message_xml, encoding="unicode", pretty_print=True)


def run_message_tests(client: ApiClient, provisioned_channels: list["ProvisionedChannel"], timeout_seconds: int) -> list[MessageTestResult]:
    results: list[MessageTestResult] = []

    for provisioned_channel in provisioned_channels:
        message_fixtures = discover_message_fixtures(provisioned_channel.fixture.channel_dir)
        for fixture in message_fixtures:
            results.append(run_message_test(client, provisioned_channel, fixture, timeout_seconds))

    return results


def run_message_tests_with_report(
    client: ApiClient,
    provisioned_channels: list["ProvisionedChannel"],
    timeout_seconds: int,
    report: JUnitReport,
) -> list[MessageTestResult]:
    results: list[MessageTestResult] = []

    for provisioned_channel in provisioned_channels:
        message_fixtures = discover_message_fixtures(provisioned_channel.fixture.channel_dir)
        for fixture in message_fixtures:
            testcase_name = f"{provisioned_channel.fixture.test_dir.name}/{provisioned_channel.fixture.channel_name}/{fixture.name}"
            classname = f"{provisioned_channel.fixture.test_dir.name}.{provisioned_channel.fixture.channel_name}"
            results.append(
                report.run_case(
                    testcase_name,
                    classname,
                    lambda provisioned_channel=provisioned_channel, fixture=fixture: run_message_test(
                        client,
                        provisioned_channel,
                        fixture,
                        timeout_seconds,
                    ),
                )
            )

    return results


def discover_message_fixtures(channel_dir: Path) -> list[MessageFixture]:
    messages_dir = channel_dir / "messages"
    if not messages_dir.exists():
        return []

    fixtures: list[MessageFixture] = []
    for message_dir in sorted(path for path in messages_dir.iterdir() if path.is_dir()):
        source_file = message_dir / "source"
        if not source_file.exists():
            raise RuntimeError(f"Message fixture is missing source payload: {message_dir}")

        source_sourcemap_file = message_dir / "source_sourcemap.yml"
        fixtures.append(
            MessageFixture(
                message_dir=message_dir,
                name=message_dir.name,
                source_file=source_file,
                source_sourcemap_file=source_sourcemap_file if source_sourcemap_file.exists() else None,
            )
        )

    return fixtures


def run_message_test(
    client: ApiClient,
    provisioned_channel: "ProvisionedChannel",
    fixture: MessageFixture,
    timeout_seconds: int,
) -> MessageTestResult:
    print(f"Sending message {fixture.name} to channel {provisioned_channel.fixture.channel_name}", flush=True)
    source_payload = fixture.source_file.read_text(encoding="utf-8")
    source_sourcemap = load_yaml_mapping(fixture.source_sourcemap_file)
    message_id = client.process_message(provisioned_channel.channel_id, source_payload, source_sourcemap)
    result = wait_for_message_result(client, provisioned_channel, fixture, message_id, timeout_seconds)
    print(f"Validated message {fixture.name} ({message_id})", flush=True)
    return result


def wait_for_message_result(
    client: ApiClient,
    provisioned_channel: "ProvisionedChannel",
    fixture: MessageFixture,
    message_id: int,
    timeout_seconds: int,
) -> MessageTestResult:
    deadline = time.monotonic() + timeout_seconds
    last_error: AssertionError | None = None
    last_result: MessageTestResult | None = None

    while time.monotonic() < deadline:
        message = message_element(client.search_message(provisioned_channel.channel_id, message_id))
        result = MessageTestResult(
            provisioned_channel=provisioned_channel,
            fixture=fixture,
            message_id=message_id,
            message_xml=message,
        )
        last_result = result

        try:
            validate_message_result(client, result)
            return result
        except AssertionError as error:
            last_error = error
            if is_terminal_message(result):
                break
            time.sleep(1)

    if last_error is not None:
        detail = format_message_failure_detail(last_result)
        raise AssertionError(f"Message fixture {fixture.message_dir} failed: {last_error}\n\n{detail}") from last_error

    detail = format_message_failure_detail(last_result)
    if last_result is not None:
        raise RuntimeError(
            f"Timed out waiting for message {message_id} for fixture {fixture.message_dir}\n\n{detail}"
        )
    raise RuntimeError(f"Timed out waiting for message {message_id} for fixture {fixture.message_dir}")


def validate_message_result(client: ApiClient, result: MessageTestResult) -> None:
    validate_source_assertions(result)
    validate_destination_assertions(result)
    run_custom_assertions(client, result)


def validate_source_assertions(result: MessageTestResult) -> None:
    source_connector = result.source_connector()

    source_metadata_file = result.fixture.message_dir / "source_metadata.yml"
    if source_metadata_file.exists():
        expected_metadata = load_yaml_mapping(source_metadata_file)
        actual_metadata = parse_metadata_assertion_map(source_connector)
        assert_mapping_subset("source_metadata.yml", expected_metadata, actual_metadata)

    source_status_file = result.fixture.message_dir / "source_status"
    if source_status_file.exists():
        expected_status = source_status_file.read_text(encoding="utf-8").strip()
        actual_status = xpath_text(source_connector, "./status")
        if actual_status != expected_status:
            raise AssertionError(f"Expected source status {expected_status}, found {actual_status}")

    source_response_file = result.fixture.message_dir / "source_response"
    if source_response_file.exists():
        assert_content_matches(
            "source response",
            source_response_file.read_bytes(),
            response_payload_text(message_content_text(source_connector, "response")),
        )

    source_transformed_file = result.fixture.message_dir / "source_transformed"
    if source_transformed_file.exists():
        assert_content_matches(
            "source transformed",
            source_transformed_file.read_bytes(),
            message_content_text(source_connector, "transformed"),
        )


def validate_destination_assertions(result: MessageTestResult) -> None:
    for path in sorted(result.fixture.message_dir.iterdir()):
        name = path.name
        if not path.is_file() or not name.startswith("dest"):
            continue

        match = re.fullmatch(r"dest(\d+)(?:(_transformed|_response|_status|_metadata\.yml))?", name)
        if not match:
            continue

        destination_number = int(match.group(1))
        suffix = match.group(2) or ""
        connector = result.destination_connector(destination_number)

        if suffix == "":
            assert_content_matches(name, path.read_bytes(), message_content_text(connector, "sent"))
        elif suffix == "_transformed":
            assert_content_matches(name, path.read_bytes(), message_content_text(connector, "transformed"))
        elif suffix == "_response":
            assert_content_matches(name, path.read_bytes(), response_payload_text(message_content_text(connector, "response")))
        elif suffix == "_status":
            expected_status = path.read_text(encoding="utf-8").strip()
            actual_status = xpath_text(connector, "./status")
            if actual_status != expected_status:
                raise AssertionError(f"Expected {name} to be {expected_status}, found {actual_status}")
        elif suffix == "_metadata.yml":
            expected_metadata = load_yaml_mapping(path)
            actual_metadata = parse_metadata_assertion_map(connector)
            assert_mapping_subset(name, expected_metadata, actual_metadata)


def run_custom_assertions(client: ApiClient, result: MessageTestResult) -> None:
    assertion_file = result.fixture.message_dir / "assertion.py"
    if not assertion_file.exists():
        return

    module = load_module_from_file(assertion_file, f"message_assertions_{result.fixture.name}")
    for attribute_name in sorted(dir(module)):
        if not attribute_name.startswith("test_"):
            continue
        attribute = getattr(module, attribute_name)
        if callable(attribute):
            attribute(client, result)


def message_content_text(connector, field_name: str) -> str | None:
    return xpath_text(connector, f"./{field_name}/content")


def response_payload_text(response_content: str | None) -> str | None:
    if response_content is None:
        return None

    stripped = response_content.strip()
    if not stripped.startswith("<response"):
        return response_content

    etree = importlib.import_module("lxml.etree")
    response_xml = etree.fromstring(stripped.encode("utf-8"))
    message = response_xml.findtext("message")
    if message is None:
        return None
    return message.replace("\r\n", "\n").replace("\r", "\n")


def is_terminal_message(result: MessageTestResult) -> bool:
    if xpath_text(result.message_xml, "./processed") != "true":
        return False

    for connector_message in result.message_xml.xpath("./connectorMessages/entry/connectorMessage"):
        status = xpath_text(connector_message, "./status")
        if status in PENDING_STATUSES:
            return False
    return True


def message_element(xml_element):
    if xml_element.tag == "message":
        return xml_element
    if xml_element.tag == "list":
        message_nodes = xml_element.xpath("./message")
        if message_nodes:
            return message_nodes[0]
    raise RuntimeError("Unexpected XML message response")


def format_message_failure_detail(result: MessageTestResult | None) -> str:
    if result is None:
        return "No message result was captured."
    return f"Actual message result XML:\n{result.xml_text()}"


def xpath_text(element, expression: str) -> str | None:
    values = element.xpath(expression)
    if not values:
        return None
    value = values[0]
    if hasattr(value, "text"):
        text = value.text
    else:
        text = str(value)
    return text.strip() if text is not None else None


def first_xpath(element, expression: str):
    values = element.xpath(expression)
    return values[0] if values else None


def parse_metadata_assertion_map(connector) -> dict[str, Any]:
    connector_metadata = parse_map_element(first_xpath(connector, "./connectorMapContent/content"))
    message_metadata = parse_map_element(first_xpath(connector, "./metaDataMap"))
    return connector_metadata | message_metadata


def parse_map_element(map_element) -> dict[str, Any]:
    if map_element is None:
        return {}

    entries_parent = first_xpath(map_element, "./m") or map_element
    result: dict[str, Any] = {}
    for entry_element in entries_parent.xpath("./entry"):
        children = list(entry_element)
        if len(children) < 2:
            continue
        key = parse_scalar_element(children[0])
        value = parse_scalar_element(children[1])
        if key is not None:
            result[str(key)] = value
    return result


def parse_scalar_element(element):
    if element is None:
        return None
    if len(element) == 0:
        return (element.text or "").strip()
    if element.tag in {"linked-hash-map", "map", "m", "content", "metaDataMap"}:
        return parse_map_element(element)
    if element.tag in {"linked-hash-set", "set"}:
        return [parse_scalar_element(child) for child in element]
    return parse_map_element(element)


def assert_content_matches(label: str, expected_bytes: bytes, actual_content: str | None) -> None:
    if actual_content is None:
        raise AssertionError(f"Expected {label} content but none was stored")

    actual_bytes = actual_content.encode("utf-8")
    pattern = re.escape(expected_bytes)
    pattern = pattern.replace(re.escape(ANY_WILDCARD), b".*?")
    if not re.fullmatch(pattern, actual_bytes, flags=re.DOTALL):
        raise AssertionError(
            f"Content mismatch for {label}. Expected {expected_bytes!r}, found {actual_bytes!r}"
        )


def assert_mapping_subset(label: str, expected: dict[str, Any], actual: dict[str, Any], path: str = "") -> None:
    for key, expected_value in expected.items():
        key_path = f"{path}.{key}" if path else str(key)
        if key not in actual:
            raise AssertionError(f"Metadata mismatch for {label}: missing key {key_path}")

        actual_value = actual[key]
        if isinstance(expected_value, dict):
            if not isinstance(actual_value, dict):
                raise AssertionError(f"Metadata mismatch for {label}: expected mapping at {key_path}")
            assert_mapping_subset(label, expected_value, actual_value, key_path)
        elif expected_value != actual_value:
            raise AssertionError(
                f"Metadata mismatch for {label} at {key_path}: expected {expected_value!r}, found {actual_value!r}"
            )


def load_yaml_mapping(path: Path | None) -> dict[str, Any]:
    if path is None or not path.exists():
        return {}

    yaml = import_yaml_module()
    data = yaml.safe_load(path.read_text(encoding="utf-8"))
    if data is None:
        return {}
    if not isinstance(data, dict):
        raise RuntimeError(f"Expected YAML mapping in {path}")
    return data


def import_yaml_module() -> Any:
    spec = importlib.util.find_spec("yaml")
    if spec is None or spec.loader is None:
        raise RuntimeError("PyYAML is not installed in the runner image")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def load_module_from_file(module_file: Path, module_name: str) -> ModuleType:
    spec = importlib.util.spec_from_file_location(module_name, module_file)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load module from {module_file}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module

import importlib
import ssl
import urllib.parse
import urllib.error
import urllib.request
from http.cookiejar import CookieJar
from xml.etree.ElementTree import Element, SubElement, tostring

REQUESTED_WITH_HEADER = "OpenIntegrationEngine-CI"
MAX_REQUEST_TIMEOUT_SECONDS = 15


class ApiClient:
    def __init__(self, base_url: str):
        self.base_url = base_url.rstrip("/")
        self.opener = build_opener()

    def request(
        self,
        path: str,
        method: str = "GET",
        data: bytes | None = None,
        content_type: str | None = None,
        accept: str = "application/xml",
        timeout: int = MAX_REQUEST_TIMEOUT_SECONDS,
    ) -> tuple[int, str]:
        headers = {
            "Accept": accept,
            "X-Requested-With": REQUESTED_WITH_HEADER,
        }
        if content_type is not None:
            headers["Content-Type"] = content_type

        request = urllib.request.Request(
            f"{self.base_url}{path}",
            data=data,
            method=method,
            headers=headers,
        )

        try:
            with self.opener.open(request, timeout=timeout) as response:
                body = response.read().decode("utf-8", errors="replace")
                return response.status, body
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", errors="replace")
            raise RuntimeError(f"HTTP {error.code} for {method} {path}: {body}") from error
        except urllib.error.URLError as error:
            raise RuntimeError(f"Request failed for {method} {path}: {error}") from error

    def create_channel(self, channel_xml: bytes) -> None:
        self.request(
            "/api/channels/",
            method="POST",
            data=channel_xml,
            content_type="application/xml",
            accept="*/*",
        )

    def deploy_channel(self, channel_id: str) -> None:
        self.request(f"/api/channels/{channel_id}/_deploy", method="POST", accept="*/*")

    def get_channel_status(self, channel_id: str):
        _, body = self.request(
            f"/api/channels/{channel_id}/status",
            accept="application/xml",
        )
        return parse_xml(body)

    def undeploy_channel(self, channel_id: str) -> None:
        self.request(f"/api/channels/{channel_id}/_undeploy", method="POST", accept="*/*")

    def remove_channel(self, channel_id: str) -> None:
        self.request(f"/api/channels/{channel_id}", method="DELETE", accept="*/*")

    def process_message(self, channel_id: str, raw_data: str, source_map: dict[str, object] | None = None) -> int:
        raw_message_xml = build_raw_message_xml(raw_data, source_map or {})
        _, body = self.request(
            f"/api/channels/{channel_id}/messagesWithObj",
            method="POST",
            data=raw_message_xml,
            content_type="application/xml",
            accept="application/xml",
            timeout=MAX_REQUEST_TIMEOUT_SECONDS,
        )
        return parse_xml(body)

    def get_message_content(self, channel_id: str, message_id: int, meta_data_ids: list[int] | None = None):
        query = ""
        if meta_data_ids:
            query = "?" + urllib.parse.urlencode([("metaDataId", meta_data_id) for meta_data_id in meta_data_ids])
        _, body = self.request(
            f"/api/channels/{channel_id}/messages/{message_id}{query}",
            accept="application/xml",
            timeout=MAX_REQUEST_TIMEOUT_SECONDS,
        )
        return parse_xml(body)

    def search_message(self, channel_id: str, message_id: int):
        query = urllib.parse.urlencode(
            {
                "minMessageId": message_id,
                "maxMessageId": message_id,
                "includeContent": "true",
                "offset": 0,
                "limit": 1,
            }
        )
        _, body = self.request(
            f"/api/channels/{channel_id}/messages?{query}",
            accept="application/xml",
            timeout=MAX_REQUEST_TIMEOUT_SECONDS,
        )
        return parse_xml(body)


def parse_xml(body: str):
    etree = importlib.import_module("lxml.etree")
    return etree.fromstring(body.encode("utf-8"))


def build_raw_message_xml(raw_data: str, source_map: dict[str, object]) -> bytes:
    raw_message = Element("com.mirth.connect.donkey.model.message.RawMessage")
    SubElement(raw_message, "overwrite").text = "false"
    SubElement(raw_message, "imported").text = "false"
    SubElement(raw_message, "rawData").text = raw_data

    source_map_element = SubElement(raw_message, "sourceMap")
    source_map_element.set("class", "linked-hash-map")
    for key, value in source_map.items():
        if isinstance(value, dict):
            raise RuntimeError(f"Nested source metadata is not supported for message submission: {key}")
        entry = SubElement(source_map_element, "entry")
        SubElement(entry, "string").text = str(key)
        SubElement(entry, "string").text = str(value)

    SubElement(raw_message, "binary").text = "false"
    return tostring(raw_message, encoding="utf-8", xml_declaration=True)


def build_opener() -> urllib.request.OpenerDirector:
    cookie_jar = CookieJar()
    ssl_context = ssl.create_default_context()
    ssl_context.check_hostname = False
    ssl_context.verify_mode = ssl.CERT_NONE
    https_handler = urllib.request.HTTPSHandler(context=ssl_context)
    return urllib.request.build_opener(https_handler, urllib.request.HTTPCookieProcessor(cookie_jar))


def login_or_fail(base_url: str, username: str, password: str, timeout: int) -> ApiClient:
    client = ApiClient(base_url)
    payload = urllib.parse.urlencode({"username": username, "password": password}).encode("utf-8")
    status, body = client.request(
        "/api/users/_login",
        method="POST",
        data=payload,
        content_type="application/x-www-form-urlencoded",
        timeout=min(timeout, 15),
    )
    if status == 200 and "SUCCESS" in body:
        print("Authenticated successfully.", flush=True)
        return client
    raise RuntimeError(f"Unexpected login response: HTTP {status} body={body}")

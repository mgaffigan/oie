from dataclasses import dataclass, field
from pathlib import Path
import time
import traceback
from typing import Callable, TypeVar
from xml.etree.ElementTree import Element, ElementTree, SubElement

T = TypeVar("T")


@dataclass
class TestCaseResult:
    name: str
    classname: str
    elapsed_seconds: float
    failure_message: str | None = None
    failure_text: str | None = None

    @property
    def failed(self) -> bool:
        return self.failure_message is not None


@dataclass
class JUnitReport:
    suite_name: str
    test_cases: list[TestCaseResult] = field(default_factory=list)

    def run_case(self, name: str, classname: str, func: Callable[[], T]) -> T:
        started_at = time.monotonic()
        try:
            result = func()
        except Exception as error:
            self.test_cases.append(
                TestCaseResult(
                    name=name,
                    classname=classname,
                    elapsed_seconds=time.monotonic() - started_at,
                    failure_message=str(error),
                    failure_text=traceback.format_exc(),
                )
            )
            raise

        self.test_cases.append(
            TestCaseResult(
                name=name,
                classname=classname,
                elapsed_seconds=time.monotonic() - started_at,
            )
        )
        return result

    def write_xml(self, results_file: Path) -> None:
        results_file.parent.mkdir(parents=True, exist_ok=True)

        tests = len(self.test_cases)
        failures = sum(1 for case in self.test_cases if case.failed)
        elapsed = sum(case.elapsed_seconds for case in self.test_cases)

        testsuite = Element(
            "testsuite",
            {
                "name": self.suite_name,
                "tests": str(tests),
                "failures": str(failures),
                "errors": "0",
                "skipped": "0",
                "time": format_seconds(elapsed),
            },
        )

        for case in self.test_cases:
            testcase = SubElement(
                testsuite,
                "testcase",
                {
                    "name": case.name,
                    "classname": case.classname,
                    "time": format_seconds(case.elapsed_seconds),
                },
            )
            if case.failed:
                failure = SubElement(
                    testcase,
                    "failure",
                    {"message": case.failure_message or "Test failed"},
                )
                failure.text = case.failure_text or case.failure_message or "Test failed"

        ElementTree(testsuite).write(results_file, encoding="utf-8", xml_declaration=True)


def format_seconds(value: float) -> str:
    return f"{value:.3f}"
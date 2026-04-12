import subprocess
import uuid
from pathlib import Path

MAX_COMMAND_TIMEOUT_SECONDS = 90


def sanitize_project_name(name: str) -> str:
    filtered = "".join(character if character.isalnum() else "-" for character in name.lower())
    filtered = filtered.strip("-") or "oie-ci"
    return f"oie-ci-{filtered}-{uuid.uuid4().hex[:8]}"


def run_command(command: list[str], env: dict[str, str], timeout_seconds: int = MAX_COMMAND_TIMEOUT_SECONDS) -> subprocess.CompletedProcess:
    print(f"+ {' '.join(command)}", flush=True)
    try:
        return subprocess.run(command, env=env, check=False, timeout=timeout_seconds)
    except subprocess.TimeoutExpired as error:
        raise RuntimeError(f"Command timed out after {timeout_seconds} seconds: {' '.join(command)}") from error


def compose_up(compose_file: Path, project_name: str, env: dict[str, str], timeout: int) -> None:
    command = [
        "docker",
        "compose",
        "-f",
        str(compose_file),
        "-p",
        project_name,
        "up",
        "-d",
        "--wait",
        "--wait-timeout",
        str(timeout),
    ]
    result = run_command(command, env, timeout_seconds=timeout)
    if result.returncode != 0:
        raise RuntimeError("docker compose up failed")


def compose_down(compose_file: Path, project_name: str, env: dict[str, str]) -> None:
    command = [
        "docker",
        "compose",
        "-f",
        str(compose_file),
        "-p",
        project_name,
        "down",
        "-v",
        "--remove-orphans",
    ]
    result = run_command(command, env, timeout_seconds=MAX_COMMAND_TIMEOUT_SECONDS)
    if result.returncode != 0:
        raise RuntimeError("docker compose down failed")

#!/usr/bin/env bash
# Runs the Docker smoke tests: boot one configuration, run the harness against it,
# tear it down. Used both locally and by the docker_smoke job in
# .github/workflows/build.yaml, so the two stay in step.
#
# The harness is built once and reused for every configuration. Locally this script
# builds it; in CI it is downloaded as an artifact and passed with --harness.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESULTS_DIR="$ROOT_DIR/ci/test-results"
ALPINE_SERVER_IMAGE="${ALPINE_SERVER_IMAGE:-oie-ci-server:local-alpine-temurin21}"
UBUNTU_SERVER_IMAGE="${UBUNTU_SERVER_IMAGE:-oie-ci-server:local-ubuntu-temurin21}"

configuration="all"
harness_dir=""
keep_alive="false"
gradle_build_args=""
disable_unit_tests="false"
needs_alpine="false"
needs_ubuntu="false"

usage() {
    cat >&2 <<'EOF'
Usage: ci/runtests.sh [options]

  --configuration <name|all>  Configuration to run; "all" runs every file in
                              ci/configurations (default: all).
  --harness <dir>             Use a prebuilt harness directory instead of building
                              one. This is the CI path; the directory is what
                              :smoketest:harnessDist produces.
  --keep-alive                Leave the stack running after the tests, for debugging.
  --gradle-build-args "..."   Extra arguments for the server image build.
  --disable-unit-tests        Skip unit tests and signing when building the images.
  -h, --help                  Show this message.
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --configuration) configuration="$2"; shift 2 ;;
        --harness) harness_dir="$2"; shift 2 ;;
        --keep-alive) keep_alive="true"; shift ;;
        --gradle-build-args) gradle_build_args="$2"; shift 2 ;;
        --disable-unit-tests) disable_unit_tests="true"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
    esac
done

# Builds only the base images the selected configurations actually need, so running
# one alpine configuration does not also build the ubuntu image.
build_images() {
    # Merge the caller's args with the disable flags into one GRADLE_BUILD_ARGS:
    # passing --build-arg twice keeps only the last, silently dropping the others.
    local effective_args="$gradle_build_args"
    if [[ "$disable_unit_tests" == "true" ]]; then
        effective_args="$effective_args -PdisableTests=true -PdisableSigning=true"
    fi

    local build_args=()
    if [[ -n "${effective_args// /}" ]]; then
        build_args+=(--build-arg "GRADLE_BUILD_ARGS=$effective_args")
    fi

    local name
    for name in "$@"; do
        case "$name" in
            ubuntu-*) needs_ubuntu="true" ;;
            *) needs_alpine="true" ;;
        esac
    done

    if [[ "$needs_alpine" == "true" ]]; then
        echo "==> Building Alpine server image ($ALPINE_SERVER_IMAGE)"
        docker build "${build_args[@]}" --target jre-run -t "$ALPINE_SERVER_IMAGE" "$ROOT_DIR"
    fi
    if [[ "$needs_ubuntu" == "true" ]]; then
        echo "==> Building Ubuntu server image ($UBUNTU_SERVER_IMAGE)"
        docker build "${build_args[@]}" --target jdk-run -t "$UBUNTU_SERVER_IMAGE" "$ROOT_DIR"
    fi
}

build_harness() {
    echo "==> Building the test harness"
    (cd "$ROOT_DIR" && ./gradlew --no-daemon :smoketest:harnessDist)
    harness_dir="$ROOT_DIR/smoketest/build/harness"
}

server_image_for() {
    case "$1" in
        ubuntu-*) echo "$UBUNTU_SERVER_IMAGE" ;;
        *) echo "$ALPINE_SERVER_IMAGE" ;;
    esac
}

run_configuration() {
    local name="$1"
    local compose_file="$ROOT_DIR/ci/configurations/$name.compose.yml"
    if [[ ! -f "$compose_file" ]]; then
        echo "No such configuration: $name ($compose_file)" >&2
        return 2
    fi

    # A unique project name keeps concurrent runs of different configurations from
    # colliding on container and network names.
    local project="oie-ci-${name//[^a-z0-9-]/-}-$$"

    export OIE_IMAGE OIE_CONFIGURATION HARNESS_DIR WORKSPACE HOST_UID HOST_GID
    OIE_IMAGE="${OIE_IMAGE_OVERRIDE:-$(server_image_for "$name")}"
    OIE_CONFIGURATION="$name"
    HARNESS_DIR="$harness_dir"
    WORKSPACE="$ROOT_DIR"
    # The harness writes its report to a bind mount, so it has to run as the user
    # that owns the checkout rather than as the image's `engine` user.
    HOST_UID="$(id -u)"
    HOST_GID="$(id -g)"

    local compose=(docker compose -f "$compose_file" -f "$ROOT_DIR/ci/harness.compose.yml" -p "$project")

    # Failures are handled explicitly: `set -e` does not apply inside a function
    # invoked in a `||` list, and the stack has to come down either way.
    local rc=0
    echo "==> $name: starting stack (image $OIE_IMAGE)"
    # Start only the server and its dependencies; the harness is run separately
    # below so that its exit status is the result of the test run.
    if "${compose[@]}" up -d --wait oie; then
        echo "==> $name: running the harness"
        "${compose[@]}" run --rm harness || rc=$?
    else
        rc=1
        echo "==> $name: the stack never became healthy; server log follows" >&2
        "${compose[@]}" logs --no-color oie >&2 || true
    fi

    if [[ "$keep_alive" == "true" ]]; then
        echo "    --keep-alive: leaving the stack running as project $project"
    else
        "${compose[@]}" down -v --remove-orphans || true
    fi
    return "$rc"
}

configurations=()
if [[ "$configuration" == "all" ]]; then
    for compose_file in "$ROOT_DIR"/ci/configurations/*.compose.yml; do
        configurations+=("$(basename "$compose_file" .compose.yml)")
    done
else
    configurations=("$configuration")
fi

mkdir -p "$RESULTS_DIR"

if [[ -z "$harness_dir" ]]; then
    build_images "${configurations[@]}"
    build_harness
elif [[ ! -d "$harness_dir" ]]; then
    echo "Harness directory does not exist: $harness_dir" >&2
    exit 2
else
    harness_dir="$(cd "$harness_dir" && pwd)"
fi

status=0
for name in "${configurations[@]}"; do
    run_configuration "$name" || status=$?
done

exit "$status"

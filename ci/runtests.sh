#!/usr/bin/env bash
# Runs the Docker smoke-test pipeline.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

ALPINE_IMAGE="oie-ci-server:local-alpine-temurin21"
UBUNTU_IMAGE="oie-ci-server:local-ubuntu-temurin21"

if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [configuration]" >&2
    exit 2
fi

configuration="${1:-all}"

echo "==> Building server images"
if [[ "$configuration" == "all" || "$configuration" != ubuntu-* ]]; then
    docker build --target jre-run -t "$ALPINE_IMAGE" .
fi
if [[ "$configuration" == "all" || "$configuration" == ubuntu-* ]]; then
    docker build --target jdk-run -t "$UBUNTU_IMAGE" .
fi

echo "==> Building harness image"
docker build --target smoketest-harness -t oie-ci-harness:local .

run_configuration() {
    case "$1" in
        ci/configurations/ubuntu-*) ci/run-configuration.sh "$1" "$UBUNTU_IMAGE" ;;
        *) ci/run-configuration.sh "$1" "$ALPINE_IMAGE" ;;
    esac
}

if [[ "$configuration" == "all" ]]; then
    for compose_file in ci/configurations/*.compose.yml; do
        run_configuration "$compose_file"
    done
else
    run_configuration "ci/configurations/$configuration.compose.yml"
fi

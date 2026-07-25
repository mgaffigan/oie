#!/usr/bin/env bash
# Entrypoint of the harness container (see ci/harness.compose.yml).
#
# The container is the server image, so the OIE client library and everything it
# needs are already installed under /opt/engine. This script only has to turn that
# into a classpath and hand off to the standard JUnit launcher, which discovers the
# tests and writes the JUnit XML that CI publishes.
set -euo pipefail

ENGINE_HOME="${ENGINE_HOME:-/opt/engine}"
HARNESS_DIR="${HARNESS_DIR:-/harness}"
RESULTS_DIR="${RESULTS_DIR:-/results}"
TESTS_ROOT="${TESTS_ROOT:-/ci/tests}"

# server-lib holds mirth-client-core.jar and its dependencies (with donkey-model.jar
# in a subdirectory); extensions holds the *-shared.jar files that carry the
# connector and data type property classes named by exported channels. Both need a
# recursive scan, which `-cp 'dir/*'` cannot do.
classpath=$(find "$ENGINE_HOME/server-lib" "$ENGINE_HOME/extensions" "$HARNESS_DIR" \
    -name '*.jar' -type f | sort | paste -sd: -)

if [[ -z "$classpath" ]]; then
    echo "No jars found under $ENGINE_HOME or $HARNESS_DIR" >&2
    exit 1
fi

report="$RESULTS_DIR/$OIE_CONFIGURATION/smoketest.xml"
mkdir -p "$(dirname "$report")"

# SmokeTestReport (registered via META-INF/services) writes the report to
# oie.reportFile, naming each test after its fixture directory. The platform's own
# --reports-dir output is not used: it names dynamic tests by index, which makes a
# CI failure impossible to identify without reading the log.
status=0
java \
    -Doie.baseUrl="$OIE_BASE_URL" \
    -Doie.configuration="$OIE_CONFIGURATION" \
    -Doie.testsRoot="$TESTS_ROOT" \
    -Doie.reportFile="$report" \
    ${OIE_HARNESS_OPTS:-} \
    -cp "$classpath" \
    org.junit.platform.console.ConsoleLauncher execute \
    --select-package=org.openintegrationengine.smoketest \
    --details=tree \
    --disable-ansi-colors \
    --fail-if-no-tests || status=$?

# A missing report would let CI publish a green check for a run that never reported,
# so treat it as a failure in its own right.
if [[ ! -s "$report" ]]; then
    echo "The harness produced no report at $report" >&2
    exit 1
fi

exit "$status"

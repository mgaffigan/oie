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

results="$RESULTS_DIR/$OIE_CONFIGURATION"
mkdir -p "$results"

# --include-engine keeps the launcher from also writing the empty junit-vintage and
# junit-platform-suite reports that the standalone jar's other engines produce, which
# CI would otherwise publish as extra (always-passing) test suites.
status=0
java \
    -Doie.baseUrl="$OIE_BASE_URL" \
    -Doie.configuration="$OIE_CONFIGURATION" \
    -Doie.testsRoot="$TESTS_ROOT" \
    ${OIE_HARNESS_OPTS:-} \
    -cp "$classpath" \
    org.junit.platform.console.ConsoleLauncher execute \
    --select-package=org.openintegrationengine.smoketest \
    --include-engine=junit-jupiter \
    --details=tree \
    --disable-ansi-colors \
    --fail-if-no-tests \
    --reports-dir="$results" || status=$?

# A missing report would let CI publish a green check for a run that never reported,
# so treat it as a failure in its own right.
if ! compgen -G "$results/*.xml" > /dev/null; then
    echo "The harness produced no report in $results" >&2
    exit 1
fi

exit "$status"

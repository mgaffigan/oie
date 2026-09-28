#!/usr/bin/env bash
#
# Authenticode-signs one Windows executable with the code-signing key in
# Google Cloud KMS. install4j runs this once per executable it generates; see
# the codeSigning element in tools/install4j/oie-installer-config.install4j.
#
# Needs JSIGN_JAR pointing at a jsign release jar, and Google Cloud
# credentials gcloud can mint an access token from.
#
set -euo pipefail

executable="${1:?usage: authenticode-sign.sh <executable>}"
here=$(cd "$(dirname "$0")" && pwd)
: "${JSIGN_JAR:?JSIGN_JAR must point at the jsign jar}"

# Minted per invocation: tokens last an hour, a full media build can take
# longer, and each call is cheap.
exec java -jar "$JSIGN_JAR" \
    --storetype GOOGLECLOUD \
    --keystore projects/itt-misc/locations/us/keyRings/itt \
    --alias itt-2026-ev/cryptoKeyVersions/1 \
    --storepass "$(gcloud auth print-access-token)" \
    --certfile "$here/oie-codesign-chain.pem" \
    --tsaurl http://timestamp.digicert.com \
    --tsmode RFC3161 \
    "$executable"

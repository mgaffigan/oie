# CI Test Design

This directory defines a minimal integration test system for the Docker image produced by this repository.

## Goals

- Build the OIE server image from the in-tree `Dockerfile`.
- Build the test harness once and reuse it for every configuration.
- Run one or more docker compose configurations in parallel in CI.
- Wait for Docker healthchecks instead of scraping logs.
- Authenticate to the server over REST using `admin` / `admin`.
- Discover tests from the filesystem, not from a registry.
- Execute tests in lexicographic order.
- Keep the authoring model slim enough that most tests are just fixture files.
- Assert on client-API operations rather than on HTTP payloads.

## Directory Layout

```text
.github/workflows/       CI entrypoints
smoketest/               the JUnit 5 harness (a Gradle module)
ci/
  README.md              this design
  configurations/        compose files keyed by configuration name
  harness.compose.yml    overlay that adds the harness to any configuration
  run-harness.sh         harness container entrypoint
  runtests.sh            local and CI entrypoint
  tests/                 filesystem-discovered test cases
```

The harness is a Gradle module at the repository root rather than under `ci/`
because `.dockerignore` excludes all of `ci/` from the image build context, and
`settings.gradle` has to be able to include it during the in-image build.

Expected configuration names are the compose basenames without the file suffix, for example:

- `alpine-temurin21-derby`
- `alpine-temurin21-mysql`
- `alpine-temurin21-postgres`
- `alpine-temurin21-mssql`

Those names map directly to compose files under `ci/configurations/`, for example:

- `ci/configurations/alpine-temurin21-derby.compose.yml`
- `ci/configurations/alpine-temurin21-postgres.compose.yml`

No additional registry of configurations is planned.

## CI Model

The workflow does three things:

1. Build the server image from the repository `Dockerfile` and tag it with a CI-unique tag.
2. Build the harness once, in the same Gradle build, and publish it as an artifact.
3. Launch one job per configuration so configurations can run in parallel.

Each smoke job downloads the harness artifact and hands it to `ci/runtests.sh`
together with the configuration name and the server image tag. The harness is a
consumer of images, not the component that builds them.

## Compose Contract

Each compose file under `ci/configurations/` defines one test environment.

Conventions are preferred over options:

- The OIE service is named `oie`.
- The image comes from `${OIE_IMAGE}`, which `ci/runtests.sh` exports.
- Dependencies such as MySQL or PostgreSQL declare Docker healthchecks.
- The OIE service declares a Docker healthcheck that represents API readiness, not just process start.

`ci/runtests.sh` layers `ci/harness.compose.yml` on top of the selected
configuration, then relies on `docker compose up -d --wait oie` plus Compose health
state. It does not parse container logs for readiness.

If the current image does not already expose a suitable health endpoint, we will add one in the server later. No server changes are part of this design doc.

## Harness Contract

The harness is a JUnit 5 module in `smoketest/`, run by the JUnit Platform Console
Launcher inside a container started from the server image itself. Deriving the
harness container from the server image means the whole OIE distribution is already
on its classpath under `/opt/engine`, so the harness ships only its own jar and the
JUnit platform. Running inside the compose network is what lets it reach the server
as `https://oie:8443` by service name, with no Docker socket and no host-gateway
plumbing.

Responsibilities are split deliberately:

- `ci/runtests.sh` owns the compose lifecycle: boot one configuration, run the
  harness, always tear the stack down unless `--keep-alive` was passed.
- The harness owns only the tests. It is handed a base URL and asserts against it.

Because the harness talks to the server through `com.mirth.connect.client.core.Client`
— the same client the CLI and the Administrator use — it does not implement any part
of the API itself. Self-signed certificates, the mandatory `X-Requested-With` header,
session cookies and model serialization are all handled by that client, and
assertions run against typed `Message` and `ConnectorMessage` objects rather than
scraped XML.

Non-goals for the first iteration:

- pluggable auth strategies
- dynamic service-name discovery
- per-test custom compose overrides
- a large assertion DSL
- server-side test helpers unless proven necessary

## Test Discovery

Tests live under `ci/tests/` and are discovered recursively.

A test directory is any directory containing `channels/<channel_name>/channel.xml`.

Tests run in lexicographic order by relative path. This keeps execution deterministic without extra metadata.

## Configuration Filtering

A test directory may contain a file named `configurations`.

Rules:

- The file contains newline-separated configuration names.
- Blank lines are ignored.
- A missing `configurations` file means the test runs in all configurations.

This is the only planned per-test targeting mechanism. When the harness is run
without `-Doie.configuration` — pointed at a server by hand, for instance — the
filter is not applied and every test runs.

## Authentication

The harness authenticates using the existing REST login endpoint.

Initial contract:

- username: `admin`
- password: `admin`
- transport: HTTPS

Override with `-Doie.username` / `-Doie.password` if needed.

## Test Execution Phases

Within each channel, the harness runs:

1. deploy the channel from `channels/<channel_name>/channel.xml` and wait for it to start
2. send messages from fixtures, in order, asserting results after each message
3. undeploy and remove the channel

Each of those steps is a separate test case in the report, so a failure names the
step it happened in. Channels are also removed on shutdown as a safety net.

Fixtures are pure data; there is no per-fixture scripting hook. A test that needs
real logic is an ordinary JUnit test in
`smoketest/src/test/java/org/openintegrationengine/smoketest/`, using the same
`OieServer` helper the fixture runner uses. That keeps the fixture format small and
puts custom logic somewhere it can be compiled, refactored and debugged like any
other test.

## Message Fixtures

Within a test channel directory, message fixtures are grouped by message name.

Example:

```text
channels/
  01-gives-response/
    channel.xml
      messages/
        01-verify-date/
          source
          source_sourcemap.yml
          source_status
          dest01
          dest01_metadata.yml
          dest01_status
        02-verify-foo/
          ...
```

Test fixtures are split into the original data:

- `source` is the payload sent into the deployed channel.
- `source_sourcemap.yml` is the source map sent with the source payload. (Optional)

And the optional assertions:

- `source_metadata.yml` is a dictionary of assertions for the source message metadata after submission.
- `source_status` is the asserted status enumeration text for the source message after submission.
- `source_response` is the asserted response payload if the channel gives a response to the source submission.
- `source_transformed` is the asserted transformed source payload if the channel transforms the message before delivery.
- `dest01_transformed` asserts byte-identical transformed content for destination 1 if the channel transforms the message before delivery.
- `dest01` asserts byte-identical sent content for destination 1.
- `dest01_response` asserts byte-identical response content if destination 1 gives a response to the message delivery.
- `dest01_metadata.yml` is a dictionary of assertions for destination 1 metadata.
- `dest01_status` asserts the status of the message at destination 1.
- `dest02*` repeats the same pattern for destination 2, and so on.

The number in `destNN` is the connector's metadata id, so `dest01` is the first
destination. Metadata assertions are a subset check against the connector map and
the message metadata map: list only the keys the test cares about.

The assertions may include the byte string `((ANY))` as a wildcard to ignore content that
is not relevant to the test case. This is useful for fields like timestamps or IDs that
are expected to change on each run. `((ANY))` behaves as regex `.*?`.

Because messages are written asynchronously, assertions are retried until they pass
or the message reaches a terminal state, so a fixture never has to encode a delay.
When a fixture does fail, the failure includes every connector's status, content and
maps, which is normally enough to fix the fixture without re-running anything.

## Channel Fixtures

`channels/` contains exported channel XML files that the harness deploys before message execution.

Initial assumptions:

- files are deployed in lexicographic order
- deployment errors fail the current test immediately
- the harness undeploys and removes test channels during teardown

## Local Developer Flow

Local development mirrors CI closely: `ci/runtests.sh` is the same entrypoint the
`docker_smoke` job uses.

```sh
# Build the images and the harness, then run every configuration.
ci/runtests.sh

# One configuration, skipping unit tests and signing for a faster image build.
ci/runtests.sh --configuration alpine-temurin21-derby --disable-unit-tests

# Leave the stack up afterwards to poke at it.
ci/runtests.sh --configuration alpine-temurin21-postgres --keep-alive
```

Results are written to `ci/test-results/<configuration>/` as JUnit XML.

To iterate on the tests themselves without rebuilding images, point the module at an
already-running server:

```sh
./gradlew :smoketest:test -Doie.baseUrl=https://localhost:8443
```

The script layer stays thin and delegates all real logic to the harness.

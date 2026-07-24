# CI Test Design

This directory defines a minimal integration test system for the Docker image produced by this repository.

## Goals

- Build the OIE server image from the in-tree `Dockerfile`.
- Build a dedicated CI runner image from `ci/runner/`.
- Run one or more docker compose configurations in parallel in CI.
- Wait for Docker healthchecks instead of scraping logs.
- Authenticate to the server over REST using `admin` / `admin`.
- Discover tests from the filesystem, not from a registry.
- Execute tests in lexicographic order.
- Keep the authoring model slim enough that most tests are just fixture files.

## Directory Layout

Suggested layout:

```text
.github/workflows/       CI entrypoints
ci/
  README.md              this design
  runner/                dockerized Python runner
  configurations/        compose files keyed by configuration name
  tests/                 filesystem-discovered test cases
```

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

The workflow is expected to do three things:

1. Build the server image from the repository `Dockerfile` and tag it with a CI-unique tag.
2. Build the runner image from `ci/runner/Dockerfile`.
3. Launch one job per configuration so configurations can run in parallel.

The workflow passes these inputs into the runner container:

- the server image tag to inject into the selected compose file
- the configuration name to execute
- the tests root, defaulting to `ci/tests`

The runner is a consumer of images, not the component that builds them.

## Compose Contract

Each compose file under `ci/configurations/` defines one test environment.

Conventions are preferred over options:

- The OIE service is named `oie`.
- The compose file references a server image that the runner rewrites to the CI-built image tag before startup.
- Dependencies such as MySQL or PostgreSQL declare Docker healthchecks.
- The OIE service declares a Docker healthcheck that represents API readiness, not just process start.

The runner relies on `docker compose up -d` plus Compose health state. It should not parse container logs for readiness.

If the current image does not already expose a suitable health endpoint, we will add one in the server later. No server changes are part of this design doc.

## Runner Contract

The runner is a small Python application packaged as a Docker image.

Responsibilities:

- select one configuration
- materialize the effective compose file with the CI-built OIE image tag
- boot the compose stack
- wait until all required services are healthy
- authenticate to the OIE REST API with `admin` / `admin`
- discover applicable tests under `ci/tests`
- run them in lexicographic order
- collect failures with enough detail to debug fixture mismatches
- always tear down the compose stack unless explicitly running in a local keep-alive mode

Non-goals for the first iteration:

- pluggable auth strategies
- dynamic service-name discovery
- per-test custom compose overrides
- a large assertion DSL
- server-side test helpers unless proven necessary

## Test Discovery

Tests live under `ci/tests/` and are discovered recursively.

A test directory is any directory under `ci/tests/`.  A test directory must contain at
least one recognized test, such as `channels/` or `test.py`.

Tests run in lexicographic order by relative path. This keeps execution deterministic without extra metadata.

The runner may support optional filtering by path later, but the default behavior is full discovery.

## Configuration Filtering

A test directory may contain a file named `configurations`.

Rules:

- The file contains newline-separated configuration names.
- Blank lines are ignored.
- A missing `configurations` file means the test runs in all configurations.

This is the only planned per-test targeting mechanism.

## Authentication

The runner authenticates using the existing REST login endpoint.

Initial contract:

- username: `admin`
- password: `admin`
- transport: HTTPS

The runner stores the authenticated session and passes a connected client object into any Python hook class loaded from a test directory.

## Test Execution Phases

Each test executes in this order:

1. `startup`
2. deploy channels from `channels/<channel_name>/channel.xml`
3. `postDeploy`
4. send messages from fixtures, assert results after each message
5. `postRun`
6. assertions
7. `teardown`

Hook methods are optional and come from the test directory's `test.py` file if present.
They are defined on a `Hooks` (or `TestHooks`) class and share one fixed signature —
`def <hook>(self, client, context)` — where `client` is the connected REST client and
`context` is the `TestRunContext` (test run, provisioned channels, message results). A
hook that needs only one of them simply ignores the other. Each hook runs under a
timeout; any exception it raises fails that hook's test case.

```python
class Hooks:
    def postDeploy(self, client, context):
        ...
```

The initial hook surface is:

- `startup`
- `postDeploy`
- `postRun`
- `teardown`

These hooks are intended for narrow gaps that fixture-only tests cannot cover.

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
          source_metadata.yml
          source_status
          dest01
          dest01_metadata.yml
          dest01_status
          assertion.py
        02-verify-foo/
          ...
```

Test fixture are split into the original data:

- `source` is the payload sent into the deployed channel.
- `source_sourcemap.yml` is the metadata sent with the source payload. (Optional) 

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
- `assertion.py` contains custom assertions for the message.

Any function in `assertion.py` named `test_*` is automatically discovered and executed by
the runner after any fixture-based assertions. The function receives the authenticated 
REST client and test results as arguments, and can execute arbitrary logic and assertions.

The assertions may include the byte string `((ANY))` as a wildcard to ignore content that
is not relevant to the test case. This is useful for fields like timestamps or IDs that 
are expected to change on each run.  `((ANY))` behaves as regex `.*?`.

## Channel Fixtures

`channels/` contains exported channel XML files that the runner deploys before message execution.

Initial assumptions:

- files are deployed in lexicographic order
- deployment errors fail the current test immediately
- the runner removes or undeploys test channels during teardown

## Local Developer Flow

Local development should mirror CI closely.

- runtests.sh: a shell entrypoint for macOS/Linux
- runtests.ps1: a PowerShell entrypoint for Windows

Those scripts should do the same high-level steps as workflow yml's for 
use by a developer running tests locally:

1. build the server image
2. build the runner image
3. execute one configuration or all configurations

The script layer should stay thin and delegate all real logic to the runner container.

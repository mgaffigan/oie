# split-plugins

One-shot migration that partitions the connectors, datatypes and plugins out of the `server` and `client`
projects into self-contained builds:

```
plugins/<name>/plugin.xml            descriptor(s), @mirthversion filled in by :distribution
plugins/<name>/common/               shared with the Administrator   -> <name>-shared.jar
plugins/<name>/server/               server side, tests, lib/        -> <name>-server.jar
plugins/<name>/client/               Administrator (Swing)           -> <name>-client.jar
gradle/plugin-{base,common,server,client}.gradle   conventions; a tier's build.gradle is only its dependencies
distribution/                        setup/ assembly, signing, extension zips, javadoc (was server/)
```

`server` and `client` do not depend on any plugin, so core code can no longer use plugin classes by accident.
The jar rules are read from the existing `server/build.gradle` and `client/build.gradle`, so the extension jars keep
their contents.

## Use

```
tools/split-plugins/split_plugins.py analyze   # read-only: ownership, leaks, dependencies, cycles
tools/split-plugins/split_plugins.py apply     # needs a clean tree (apart from tools/split-plugins)
```

`apply` leaves everything unstaged; review with `git add -A && git diff --cached -M --stat`. It writes
`plugins/gaps.md` with what moving files could not separate (classes core still uses, plugin cycles, core tests that
use plugins). The core -> plugin references that need real code changes (DICOM, jdbc, directory resource, vm, swagger)
are in the commit before this tool's; `model.OWNERSHIP_OVERRIDES` lists the classes it frees up to move.

## Checking the result without the full build

The slow parts are signing (needs a timestamp server) and javadoc (needs the network); neither is needed to check a
split. In order of cost:

```
./gradlew projects                                     # settings and project discovery
./gradlew :server:compileJava :client:compileJava      # core alone, no plugin on the classpath
./gradlew :plugins:assemble                            # every plugin tier
./gradlew assemble -x test -x userApiJavadoc -x signSetupJars
./gradlew testClasses                                  # all tests compile
./gradlew :plugins:test :server:test
```

To prove the distribution did not change, snapshot it before and after (the directory differs, the jars should not):

```
./gradlew assemble -x test -x userApiJavadoc -x signSetupJars     # before applying
tools/split-plugins/jar_manifest.py snapshot server/setup > before.json
tools/split-plugins/split_plugins.py apply
./gradlew assemble -x test -x userApiJavadoc -x signSetupJars
tools/split-plugins/jar_manifest.py snapshot distribution/setup > after.json
tools/split-plugins/jar_manifest.py compare before.json after.json
```

Expected differences: `mirth-client.jar` no longer carries the plugins' client classes (they were duplicated from the
extension jars), the classes in `gaps.md` move from extension jars to the core jars, and the removed Swagger examples
servlet.

## What `apply` changes outside `plugins/`

- Deletes `SwaggerExamplesServlet` and its registration in `MirthWebServer` if the commit that removes core references to plugins has not already.
- `server/build.gradle`: extension tasks, staging, signing, zips and dev launcher move to `distribution/build.gradle`;
  core jars are built into `server/build/libs`.
- `client/build.gradle`, `command/build.gradle`, root `build.gradle`, `settings.gradle`: plugin discovery, paths, aggregate tasks.
- `server/setup` and `server/dist` become `distribution/setup` and `distribution/dist` (Dockerfile, ignore files,
  install4j, README and CONTRIBUTING are updated); the signing keystore moves to `distribution/`.

Every edit asserts that its anchor is present, so a build file that has drifted fails loudly instead of being
half-converted.

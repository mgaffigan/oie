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

## Use

```
tools/split-plugins/split.py       # from the repository root, on a clean tree
```

Everything is left unstaged; review with `git add -A && git diff --cached -M --stat`.

## What to review

Nothing here is computed. The script performs, in order, what the files next to it say:

| | |
|---|---|
| `manifest.py` | which directories move where, and for each plugin project which templates with which parameters |
| `templates/` | the fragments a plugin project's `build.gradle` is made of |
| `files/` | files that are added as they are: the plugin conventions in `gradle/`, `plugins/build.gradle`, `plugins/gaps.md` |
| `patches/` | edits to files that already exist, one unified diff per file (apply with `git apply`) |
| `split.py` | carries all of that out; `python3 -m unittest` in this directory covers its two rules |

`distribution/build.gradle` starts as a copy of `server/build.gradle` (`COPIES`), so `patches/distribution/build.gradle.patch`
shows what changed in the moved code and `patches/server/build.gradle.patch` what left the server project.

`plugins/gaps.md` lists what moving files could not separate, and what was done about it.

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
tools/split-plugins/split.py
./gradlew assemble -x test -x userApiJavadoc -x signSetupJars
tools/split-plugins/jar_manifest.py snapshot distribution/setup > after.json
tools/split-plugins/jar_manifest.py compare before.json after.json
```

Expected differences: `mirth-client.jar` no longer carries the plugins' client classes (they were duplicated from the
extension jars) and the removed Swagger examples servlet.

# For developers and contributors

## Build and run locally

To build the solution, you must have a Java 1.8 JDK+FX and Apache Ant.  This
can be installed by [sdkman](https://sdkman.io/) by executing `sdk env install`.
From the `server/` directory, run `ant -f mirth-build.xml -DdisableSigning=true`.

After build, run the server by invoking `server/setup/oieserver` in bash.

## Build and run with docker

```bash
# Build using docker
docker build -t oie-dev .
# Start an ephemeral image
# NOTE: All data will be deleted on stop due to --rm.  Use a volume for "real" use.
docker run --rm -p 8443:8443 oie-dev
```

## Connect

Then use [Ballista](https://github.com/kayyagari/ballista) to connect to 
https://localhost:8443/ and login using admin admin.

If you are using Mirth Connect Administrator Launcher, you may need to omit
`-DdisableSigning=true` to support JWS signatures and run MCAL passing `-k -d`
to make it ignore self-signed certificates. Launchers like 
[Ballista](https://github.com/kayyagari/ballista) do not require signing, and 
signing adds considerable time to the build process.

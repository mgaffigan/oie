# Startup Theory

## Requirements

In order to boot, the server must be lauched with:

1. An appropriate version of Java
1. VM Options
  a. base_includes.vmoptions
  a. default_modules.vmoptions
1. Custom specified VM Options (custom.vmoptions), which may include additions or modifications to the classpath
1. Optional Server Arguments

## Cross platform

Each OS has a different expected mechanism to run services:

- Linux<br>systemd supervises the process, started via command line, output via STDOUT to journald
- Mac OS<br>launchd supervises the process, started via command line, output via STDOUT to unified logging
- Windows<br>SCM supervises the process, started via NT Services, output via Event Log
- Docker<br>docker supervises the process, started via wrapper script, output via STDOUT to docker

## File layout

- mirth-server-launcher.jar<br>Entrypoint to the server
- oieserver.vmoptions<br>Desired arguments to be used on boot.  Parsed to create appdata/launch.args
- appdata/launch.args<br>Arguments to be used on boot, should be the parsed result of oieserver.vmoptions

## Setup to Boot process

During install, a packaged JRE is installed or a suitable JRE is identified.  The path to that JRE is locally
stored as part of the setup installer and used in the service manifest.  No evaluation of the appropriate JRE
is performed following install.  Changing the JRE requires re-running the setup installer.  As a last step of
the setup, the launcher is written 

The service is initially installed using the following invocation:

## Launcher "main" classes

- com.mirth.connect.server.launcher.MirthLauncher<br>Direct-to-server entrypoint.  Used from wrapper scripts
  or if vmoptions parsing is not desirable.
- com.mirth.connect.server.launcher.ParseVmOptions<br>Parser that updates appdata/launch.args then exits.
  Used from setup and from wrapper scripts.
- com.mirth.connect.server.launcher.VmOptionsLauncher<br>Parse-then-launch entrypoint.  Default entrypoint 
  for services.  Delegates to ParseVmoptions and MirthLauncher.

## Initialize launch.args

```bash
java -cp mirth-server-launcher.jar com.mirth.connect.server.launcher.ParseVmOptions
```

## Run the app

```bash
java "@appdata/launch.args" com.mirth.connect.server.launcher.VmOptionsLauncher
```

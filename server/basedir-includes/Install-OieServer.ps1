# Fail on any error
$ErrorActionPreference = 'Stop';

# base path is the location of this script
$basePath = Split-Path -Parent $MyInvocation.MyCommand.Path;

# Use java from local jre, JAVA_HOME, or system path
$javaCmd = 'java.exe';
if (Test-Path "$basePath\jre\bin\java.exe") {
    $javaCmd = "$basePath\jre\bin\java.exe`"";
} elseif ($env:JAVA_HOME) {
    $javaCmd = "$env:JAVA_HOME\bin\java.exe";
}
Write-Host "Using Java command: $javaCmd";

# Generate the service launch arguments
$launchFile = "$basePath\appdata\ntlaunch.args";
& "$javaCmd" -cp "$basePath\mirth-server-launcher.jar" `
    com.mirth.connect.server.launcher.ParseVmOptions `
    "$basePath\oieserver.vmoptions" $launchFile;
if (($LASTEXITCODE -ne 0) -and ($LASTEXITCODE -ne 75)) {
    Write-Error "Error ${LASTEXITCODE}: Failed to generate service launch arguments. Please check the oieserver.vmoptions file.";
    exit $LASTEXITCODE;
}

# Make the full command
$command = "`"$javaCmd`" `"@$launchFile`" -Dmirth.home=`"$basePath`" com.mirth.connect.server.launcher.VmOptionsNtServiceLauncher oieserver `"$launchFile`"";

# Create the service using sc.exe
& sc create "oieserver" binPath="$command" start=auto displayName="Open Integration Engine"
# Set recovery to automatically restart on failure
& sc failure "oieserver" reset=86400 actions=restart/1000/restart/60000/restart/60000

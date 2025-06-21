#
# SPDX-License-Identifier: MPL-2.0
# SPDX-FileCopyrightText: 2025 Tony Germano and Mitch Gaffigan
#

<#
.SYNOPSIS
    Open Integration Engine Server Launcher Script (PowerShell Version)

.DESCRIPTION
    This script is the main launcher for the Open Integration Engine (OIE)
    server. It prepares the Java environment and executes the server launcher
    JAR file.

    The script automatically finds a compatible Java runtime (version 17+ by
    default) by searching for a valid executable in the following priority order:

    1. The OIE_JAVA_PATH environment variable.
    2. The -java-cmd directive in the oieserver.vmoptions file or included .vmoptions 
       files. Must specify the path to the 'java' executable. This is the preferred 
       way to declare the desired version for running the server and can be overridden
       by OIE_JAVA_PATH. Can be a relative path from the location of this script.
    3. The JAVA_HOME environment variable.
    4. The 'java' command available in the system's PATH.

    It also parses the 'oieserver.vmoptions' file to configure JVM options,
    system properties (-D...), and classpath modifications.

.NOTES
    Configuration via Environment Variables:
        OIE_JAVA_PATH   - (Highest priority) Set the full path to the 'java'
                        executable to be used. Can be a relative path from the
                        location of this script or a tilde path
                        (e.g., ~/path/to/java).
        JAVA_HOME       - Set the path to the root of a Java installation. The
                        script will look for 'bin/java' within this path.

.PARAMETER AppArgs
    Arguments passed directly to the underlying Java application
    (com.mirth.connect.server.launcher.MirthLauncher).

.EXAMPLE
    ./oieserver.ps1

.EXAMPLE
    $env:OIE_JAVA_PATH = 'C:\path\to\java.exe';
    ./oieserver.ps1 -Dproperty=value --some-arg value
#>

param(
    [parameter(ValueFromRemainingArguments = $true)][string[]] $AppArgs
)

# Stop on any error and exit non-zero
$ErrorActionPreference = "Stop"
$MinJavaVersion = 17

# Set OieHome to the script directory using PowerShell's built-in variable
$OieHome = $PSScriptRoot
$LauncherJar = Join-Path -Path $OieHome -ChildPath "mirth-server-launcher.jar"
 # Use script scope to be modifiable by functions
$script:Classpath = [System.Collections.Generic.List[string]]::new()
$script:VmOptions = [System.Collections.Generic.List[string]]::new()

# This will hold the validated path to the Java executable.
$FinalJavaCmd = $null
# This will temporarily hold the result from parsing the vmoptions file.
$script:VmOptionsJavaCmd = $null
$script:VmOptionsJavaCmdFile = $null

# --- Function to validate Java version ---
function Test-IsValidJavaVersion([string] $JavaCmd) {
    # Execute 'java -version' and capture the output from stderr
    # Example output: openjdk version "17.0.2" 2022-07-19
    try {
        $versionOutput = & $JavaCmd -version 2>&1
    }
    catch {
        return $false
    }
    
    if ($LASTEXITCODE -ne 0) {
        return $false
    }

    # Extract the major version number. This works for formats like "1.8.0" and "17.0.2".
    $match = $versionOutput | Select-String -Pattern '"(\d+)\.'
    return ($match -and ($match.Matches[0].Groups[1].Value -as [int]) -ge $MinJavaVersion)
}

# --- Function to parse vmoptions file ---
function Parse-VmOptions([string] $File) {
    
    if (-not (Test-Path -LiteralPath $File -PathType Leaf)) {
        Write-Warning "VM options file not found: $File"
        return
    }

    # Read the file line by line
    Get-Content -Path $File | ForEach-Object {
        $line = $_.Trim()

        # Skip empty lines and comments
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) {
            return
        }

        # Expand environment variables (converting the ${ABC} syntax to %ABC%)
        $line = [Environment]::ExpandEnvironmentVariables(($line -replace '\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}', '%$1%'))
        
        # Command is space-separated, discarding all intermediate whitespace
        $lineCommand, $lineArgument = $line.Split(' ', 2, 'RemoveEmptyEntries')
        if ($lineCommand -eq '-include-options') {
            # Resolve relative paths to the current file's directory
            $lineArgument = Join-Path -Path (Split-Path -Path $File -Parent) -ChildPath $lineArgument
            Parse-VmOptions -File $includedFile
        }
        elseif ($lineCommand -eq '-classpath') {
            $script:Classpath.Clear()
            $script:Classpath.Add($lineArgument)
        }
        elseif ($lineCommand -eq '-classpath/a') {
            $script:Classpath.Add($lineArgument)
        }
        elseif ($lineCommand -eq '-classpath/p') {
            $script:Classpath.Insert(0, $lineArgument)
        }
        elseif ($lineCommand -eq '-java-cmd') {
            # Store the path and the file it was found in. Validation is deferred.
            $script:VmOptionsJavaCmd = Join-Path $OieHome $lineArgument
            $script:VmOptionsJavaCmdFile = $File
        }
        else {
            $script:VmOptions.Add($line)
        }
    }
}

# --- Main Logic ---

# 1. Recursively parse the VM options file to populate vmoptions variables.
Parse-VmOptions -File (Join-Path -Path $OieHome -ChildPath "oieserver.vmoptions")

# 2. Ensure the launcher JAR is always in the classpath.
if (-not $script:Classpath.Contains($LauncherJar)) {
    Write-Host "Info: Prepending mirth-server-launcher.jar to the classpath." -ForegroundColor Green
    $script:Classpath.Insert(0, $LauncherJar)
}

# 3. Discover the Java executable using the documented priority order.

# Check OIE_JAVA_PATH (fail-fast on invalid).
if (-not [string]::IsNullOrWhiteSpace($env:OIE_JAVA_PATH)) {
    $resolvedPath = Join-Path $OieHome [System.Environment]::ExpandEnvironmentVariables($env:OIE_JAVA_PATH)
    if (Test-IsValidJavaVersion -JavaCmd $resolvedPath) {
        Write-Host "Info: Found suitable java version specified by the OIE_JAVA_PATH environment variable" -ForegroundColor Green
        $FinalJavaCmd = $resolvedPath
    } else {
        Write-Error "'$resolvedPath' is specified by the OIE_JAVA_PATH environment variable, which is not a valid Java executable of at least version $MinJavaVersion. Exiting."
    }
}

# Check -java-cmd from vmoptions (fail-fast on invalid, only if not already found).
if (-not $FinalJavaCmd -and -not [string]::IsNullOrWhiteSpace($script:VmOptionsJavaCmd)) {
    if (Test-IsValidJavaVersion -JavaCmd $script:VmOptionsJavaCmd) {
        Write-Host "Info: Found suitable java version specified by the -java-cmd directive in '$($script:VmOptionsJavaCmdFile)'" -ForegroundColor Green
        $FinalJavaCmd = $script:VmOptionsJavaCmd
    } else {
        Write-Error "'$($script:VmOptionsJavaCmd)' is specified by the -java-cmd directive in '$($script:VmOptionsJavaCmdFile)', which is not a valid Java executable of at least version $MinJavaVersion. Exiting."
        exit 1
    }
}

# Check JAVA_HOME (no fail-fast).
if (-not $FinalJavaCmd -and (Test-Path -Path $env:JAVA_HOME -PathType Container)) {
    $javaHomePath = Join-Path -Path $env:JAVA_HOME -ChildPath "bin\java"
    if (Test-IsValidJavaVersion -JavaCmd $javaHomePath) {
        Write-Host "Info: Found suitable java version specified by the JAVA_HOME environment variable" -ForegroundColor Green
        $FinalJavaCmd = $javaHomePath
    } else {
        Write-Warning "'$javaHomePath' is specified by the JAVA_HOME environment variable, which is not a valid Java executable of at least version $MinJavaVersion. Ignoring."
    }
}

# Check system PATH (no fail-fast).
if (-not $FinalJavaCmd) {
    if (Get-Command "java" -ErrorAction SilentlyContinue) {
        if (Test-IsValidJavaVersion -JavaCmd "java") {
            Write-Host "Info: Found suitable java version in the PATH" -ForegroundColor Green
            $FinalJavaCmd = "java"
        } else {
            Write-Warning "'java' does not exist in your PATH or is not a valid Java executable of at least version $MinJavaVersion."
        }
    }
}

# 4. Final check for a valid Java path before execution.
if (-not $FinalJavaCmd) {
    Write-Error "Could not find a Java $($MinJavaVersion)+ installation. Please configure -java-cmd in conf/custom.vmoptions, set OIE_JAVA_PATH, set JAVA_HOME, or ensure 'java' in your PATH is version $($MinJavaVersion) or higher."
}

# 5. Assemble final arguments and launch the process.
$javaOpts = [System.Collections.Generic.List[string]]::new()
$javaOpts.AddRange($script:VmOptions)
$javaOpts.Add("-cp")
$javaOpts.Add($script:Classpath -join [System.IO.Path]::PathSeparator)
$javaOpts.Add("com.mirth.connect.server.launcher.MirthLauncher")
if ($AppArgs) { $javaOpts.AddRange($AppArgs) }

# Launch Open Integration Engine
Write-Host "Starting Open Integration Engine..." -ForegroundColor Green
Write-Host ("$FinalJavaCmd " + (($javaOpts | %{ "`"$_`"" }) -join ' '));

# The engine expects it's working directory to be OieHome
Push-Location -Path $OieHome
try {
    & $FinalJavaCmd @javaOpts
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}

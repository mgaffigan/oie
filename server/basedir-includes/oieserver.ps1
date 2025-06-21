#
# SPDX-License-Identifier: MPL-2.0
# SPDX-FileCopyrightText: 2025 Tony Germano and Mitch Gaffigan
#

# =============================================================================
# Open Integration Engine Server Launcher Script (PowerShell Version)
#
# Description:
#   This script is the main launcher for the Open Integration Engine (OIE)
#   server. It prepares the Java environment and executes the server launcher
#   JAR file.
#
#   The script automatically finds a compatible Java runtime (version 17+ by
#   default) by searching for a valid executable in the following priority order:
#     1. The OIE_JAVA_PATH environment variable.
#     2. The -java-cmd directive in the oieserver.vmoptions file or included
#        .vmoptions files. Must specify the path to the 'java' executable.
#        This is the preferred way to declare the desired version for running
#        the server and can be overridden by OIE_JAVA_PATH. Can be a relative
#        path from the location of this script.
#     3. The JAVA_HOME environment variable.
#     4. The 'java' command available in the system's PATH.
#
#   It also parses the 'oieserver.vmoptions' file to configure JVM options,
#   system properties (-D...), and classpath modifications.
#
# Usage:
#   ./oieserver.ps1 [app-arguments]
#
#   All [app-arguments] are passed directly to the underlying Java application
#   (com.mirth.connect.server.launcher.MirthLauncher).
#
# Configuration via Environment Variables:
#   OIE_JAVA_PATH   - (Highest priority) Set the full path to the 'java'
#                   executable to be used. Can be a relative path from the
#                   location of this script or a tilde path
#                   (e.g., ~/path/to/java).
#   JAVA_HOME       - Set the path to the root of a Java installation. The
#                   script will look for 'bin/java' within this path.
# =============================================================================

# --- Script Parameters and Initial Variables ---
param(
    [string[]]$AppArgs = $args
)

$MinJavaVersion = 17

# Set OieHome to the script directory using PowerShell's built-in variable
$OieHome = $PSScriptRoot
# The engine expects it's working directory to be OieHome
try {
    Set-Location -Path $OieHome -ErrorAction Stop
}
catch {
    Write-Error "Could not change to the OieHome directory: $OieHome"
    exit 1
}
$LauncherJar = Join-Path -Path $OieHome -ChildPath "mirth-server-launcher.jar"
$script:Classpath = $LauncherJar # Use script scope to be modifiable by functions
$script:VmOptions = [System.Collections.Generic.List[string]]::new()

# This will hold the validated path to the Java executable.
$FinalJavaCmd = $null
# This will temporarily hold the result from parsing the vmoptions file.
$script:VmOptionsJavaCmd = $null
$script:VmOptionsJavaCmdFile = $null

# --- Function to resolve a path to a canonical absolute path ---
function Resolve-CanonicalPath {
    param(
        [string]$PathToResolve
    )

    # Explicitly handle simple tilde expansion first (`~/` or `~`)
    if ($PathToResolve -match '^~(/|$)') {
        $homePath = $env:HOME
        if ([string]::IsNullOrWhiteSpace($homePath)) {
            $homePath = $env:USERPROFILE
        }
        $PathToResolve = ($PathToResolve -replace '^~/', "$($homePath)/") -replace '^~$', $homePath
    }
    
    # If the path is not absolute, assume it's relative to OIE_HOME
    if (-not [System.IO.Path]::IsPathRooted($PathToResolve)) {
        $PathToResolve = Join-Path -Path $OieHome -ChildPath $PathToResolve
    }
    
    try {
        return (Resolve-Path -LiteralPath $PathToResolve).Path
    }
    catch {
        return $PathToResolve
    }
}

# --- Function to safely expand specific variable formats in a string ---
function Expand-LineVariables {
    param(
        [string]$Line
    )
    
    # Define a "match evaluator" script block. This block will be called
    # for every match the regex finds.
    $evaluator = {
        param($match)
        
        # The variable name is in the first capture group.
        $varName = $match.Groups[1].Value
        
        # Look for a PowerShell variable first.
        $varValue = (Get-Variable -Name $varName -Scope "global" -ErrorAction SilentlyContinue).Value
        # If not found, look for an environment variable.
        if ($null -eq $varValue) {
            $varValue = (Get-Variable -Name "env:$varName" -ErrorAction SilentlyContinue).Value
        }
        
        # Return the found value. This will be the replacement for the match.
        return $varValue
    }

    # Define the regex pattern to find ${...} variables.
    $regex = '\$\{([a-zA-Z_][a-zA-Z0-9_]*)\}'

    # Use the static Replace method, passing the input line, the regex, and our evaluator.
    $expandedLine = [regex]::Replace($Line, $regex, $evaluator)
    
    return $expandedLine
}

# --- Function to validate Java version ---
function Test-IsValidJavaVersion {
    param(
        [string]$JavaCmd
    )

    # Check if the command is found and is executable
    if (-not (Get-Command $JavaCmd -ErrorAction SilentlyContinue)) {
        return $false
    }

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
    if ($match -and ($match.Matches[0].Groups[1].Value -as [int]) -ge $MinJavaVersion) {
        return $true
    } else {
        return $false
    }
}

# --- Function to parse vmoptions file ---
function Parse-VmOptions {
    param(
        [string]$File
    )
    
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

        $line = Expand-LineVariables -Line $line

        if ($line -match '^-include-options\s+(.+)') {
            $includedFile = $matches[1].Trim()
            # Resolve relative paths to the current file's directory
            if (-not [System.IO.Path]::IsPathRooted($includedFile)) {
                $includedFile = Join-Path -Path (Split-Path -Path $File -Parent) -ChildPath $includedFile
            }
            Parse-VmOptions -File $includedFile
        }
        elseif ($line -match '^-classpath\s+(.+)') {
            $script:Classpath = $matches[1].Trim()
        }
        elseif ($line -match '^-classpath/a\s+(.+)') {
            $script:Classpath = "$($script:Classpath)$([System.IO.Path]::PathSeparator)$($matches[1].Trim())"
        }
        elseif ($line -match '^-classpath/p\s+(.+)') {
            $script:Classpath = "$($matches[1].Trim())$([System.IO.Path]::PathSeparator)$($script:Classpath)"
        }
        elseif ($line -match '^-java-cmd\s+(.+)') {
            # Store the path and the file it was found in. Validation is deferred.
            $script:VmOptionsJavaCmd = Resolve-CanonicalPath -PathToResolve $matches[1].Trim()
            $script:VmOptionsJavaCmdFile = $File
        }
        else {
            $script:VmOptions.Add($line) | Out-Null
        }
    }
}

# --- Main Logic ---

# 1. Recursively parse the VM options file to populate vmoptions variables.
Parse-VmOptions -File (Join-Path -Path $OieHome -ChildPath "oieserver.vmoptions")

# 2. Ensure the launcher JAR is always in the classpath.
if ($script:Classpath -notmatch [regex]::Escape($LauncherJar)) {
    Write-Host "Info: Prepending mirth-server-launcher.jar to the classpath." -ForegroundColor Green
    $script:Classpath = "$($LauncherJar)$([System.IO.Path]::PathSeparator)$($script:Classpath)"
}

# 3. Discover the Java executable using the documented priority order.

# Check OIE_JAVA_PATH (fail-fast on invalid).
if (-not [string]::IsNullOrWhiteSpace($env:OIE_JAVA_PATH)) {
    $resolvedPath = Resolve-CanonicalPath -PathToResolve $env:OIE_JAVA_PATH
    if (Test-IsValidJavaVersion -JavaCmd $resolvedPath) {
        Write-Host "Info: Found suitable java version specified by the OIE_JAVA_PATH environment variable" -ForegroundColor Green
        $FinalJavaCmd = $resolvedPath
    } else {
        Write-Error "'$resolvedPath' is specified by the OIE_JAVA_PATH environment variable, which is not a valid Java executable of at least version $MinJavaVersion. Exiting."
        exit 1
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
    exit 1
}

# 5. Assemble final arguments and launch the process.
$javaOpts = [System.Collections.Generic.List[string]]::new()
$javaOpts.AddRange($script:VmOptions)
$javaOpts.Add("-cp")
$javaOpts.Add('"{0}"' -f $script:Classpath)
$javaOpts.Add("com.mirth.connect.server.launcher.MirthLauncher")
$javaOpts.AddRange($AppArgs)

# Launch Open Integration Engine
Write-Host "Starting Open Integration Engine..." -ForegroundColor Green
Write-Host "$FinalJavaCmd $($javaOpts -join ' ')"

& $FinalJavaCmd $javaOpts
exit $LASTEXITCODE

param(
    [string]$Configuration = "all",
    [string]$GradleBuildArgs = "",
    [switch]$DisableUnitTests,
    [switch]$KeepAlive
)

$ErrorActionPreference = "Stop"
$PSNativeCommandUseErrorActionPreference = $true
$RootDir = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$AlpineServerImage = "oie-ci-server:local-alpine-temurin21"
$UbuntuServerImage = "oie-ci-server:local-ubuntu-temurin21"
$RunnerImage = if ($env:RUNNER_IMAGE) { $env:RUNNER_IMAGE } else { "oie-ci-runner:local" }
$ResultsDir = Join-Path $RootDir "ci/test-results"

function Build-Images {
    $serverBuildArgs = @("build")
    # Merge user args and the disable-tests flags into a single GRADLE_BUILD_ARGS;
    # passing --build-arg twice would let docker keep only the last, silently
    # dropping the user-supplied args.
    $effectiveBuildArgs = $GradleBuildArgs
    if ($DisableUnitTests) {
        $effectiveBuildArgs = ("$effectiveBuildArgs -PdisableTests=true -PdisableSigning=true").Trim()
    }
    if ($effectiveBuildArgs) {
        $serverBuildArgs += @( "--build-arg", "GRADLE_BUILD_ARGS=$effectiveBuildArgs" )
    }
    docker @($serverBuildArgs + @( "--target", "jre-run", "-t", $AlpineServerImage, $RootDir ))
    docker @($serverBuildArgs + @( "--target", "jdk-run", "-t", $UbuntuServerImage, $RootDir ))

    docker build -t $RunnerImage (Join-Path $RootDir "ci/runner")
}

function Invoke-Configuration([string]$Name) {
    $serverImage = if ($Name -like "ubuntu-*") { $UbuntuServerImage } else { $AlpineServerImage }
    $runnerArgs = @(
        "--workspace", "/workspace",
        "--configuration", $Name,
        "--server-image", $serverImage,
        "--results-root", "ci/test-results"
    )

    if ($KeepAlive) {
        $runnerArgs += "--keep-alive"
    }

    docker run --rm `
        --add-host host.docker.internal:host-gateway `
        -v /var/run/docker.sock:/var/run/docker.sock `
        -v "${RootDir}:/workspace" `
        $RunnerImage `
        @runnerArgs
}

Build-Images

    if (Test-Path $ResultsDir) {
        Remove-Item -Recurse -Force $ResultsDir
    }
    New-Item -ItemType Directory -Path $ResultsDir | Out-Null

if ($Configuration -eq "all") {
    Get-ChildItem (Join-Path $RootDir "ci/configurations") -Filter "*.compose.yml" |
        Sort-Object Name |
        ForEach-Object {
            $name = $_.Name -replace "\.compose\.yml$", ""
            Invoke-Configuration $name
        }
} else {
    Invoke-Configuration $Configuration
}

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$mineJarNames = [string[]](dir mine/*.jar | % Name)

New-Item -ItemType Directory -Path "$PWD/dots" -Force | Out-Null
New-Item -ItemType Directory -Path "$PWD/dots-api" -Force | Out-Null
jdeps --multi-release 17 -q -verbose:class --dot-output "$PWD/dots" -cp 'mine/*:libs/*' theirs/*.jar 2>$null | Out-Null
jdeps --multi-release 17 -q --api-only --recursive -verbose:class --dot-output "$PWD/dots-api" -cp 'mine/*:libs/*' theirs/*.jar 2>$null | Out-Null

$rows = foreach ($dot in ((dir dots/*.dot) + (dir dots-api/*.dot)) | ? Name -ne 'summary.dot') {
    Get-Content -LiteralPath $dot.FullName | ForEach-Object {
        if ($_ -match '^\s*"([^"]+)"\s*->\s*"(.+) \(([^()]+)\)";\s*$') {
            if ($mineJarNames -contains (Split-Path -Leaf $matches[3])) {
                [pscustomobject]@{
                    SourceJar   = $dot.BaseName
                    SourceClass = $matches[1]
                    TargetClass = $matches[2]
                    TargetJar   = Split-Path -Leaf $matches[3]
                }
            }
        }
    }
}

$rows | Sort-Object SourceJar, SourceClass, TargetClass, TargetJar -Unique
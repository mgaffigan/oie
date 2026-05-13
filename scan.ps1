Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$mineJarNames = [string[]](dir mine/*.jar | % Name)

New-Item -ItemType Directory -Path "$PWD/dots" -Force | Out-Null
jdeps --multi-release 17 -q --api-only -verbose:class --dot-output "$PWD/dots" -cp 'mine/*:libs/*' theirs/*.jar 2>$null | Out-Null

foreach ($dot in dir dots/*.dot | ? Name -ne 'summary.dot') {
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
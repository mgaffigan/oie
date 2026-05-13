Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$theirJars = dir theirs/*.jar

New-Item -ItemType Directory -Path "$PWD/dots" -Force | Out-Null
New-Item -ItemType Directory -Path "$PWD/dots-api" -Force | Out-Null

$rows = foreach ($family in $theirJars | Group-Object { $_.Name -replace '-(client|server|shared)', '' }) {
    Remove-Item "$PWD/dots/*.dot", "$PWD/dots-api/*.dot" -ErrorAction SilentlyContinue

    $targetJarNames = [string[]]($theirJars | Where-Object Name -notIn $family.Group.Name | ForEach-Object Name)
    $classPath = @('mine/*', 'libs/*') + ($theirJars | Where-Object Name -in $targetJarNames | ForEach-Object FullName)

    jdeps --multi-release 17 -q -verbose:class --dot-output "$PWD/dots" -cp ($classPath -join ':') $family.Group.FullName 2>$null | Out-Null
    jdeps --multi-release 17 -q --api-only --recursive -verbose:class --dot-output "$PWD/dots-api" -cp ($classPath -join ':') $family.Group.FullName 2>$null | Out-Null

    foreach ($dot in (@(dir dots/*.dot) + @(dir dots-api/*.dot)) | ? Name -ne 'summary.dot') {
        Get-Content -LiteralPath $dot.FullName | ForEach-Object {
            if ($_ -match '^\s*"([^"]+)"\s*->\s*"(.+) \(([^()]+)\)";\s*$') {
                $targetJar = Split-Path -Leaf $matches[3]

                if ($targetJarNames -contains $targetJar) {
                    [pscustomobject]@{
                        SourceJar   = $dot.BaseName
                        SourceClass = $matches[1]
                        TargetClass = $matches[2]
                        TargetJar   = $targetJar
                    }
                }
            }
        }
    }
}

$rows | Sort-Object SourceJar, SourceClass, TargetClass, TargetJar -Unique
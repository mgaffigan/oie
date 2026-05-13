param(
    [string]$OieRoot = '/Users/mgaffigan/dev/gitroot/oie'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

./scan.ps1 |
    Group-Object TargetJar |
    ForEach-Object {
        $jarName = $_.Name
        $bucket = switch -Regex ($jarName) {
            '^donkey-' { 'donkey' }
            '^mirth-client' { 'client' }
            default { 'server' }
        }
        $refPath = "$OieRoot/$bucket/refs/$($jarName -replace '\.jar$', '.txt')"
        $refClasses = $_.Group.TargetClass | ForEach-Object { ($_ -replace '\.', '/') + '.class' } | Sort-Object -Unique

        Set-Content -LiteralPath $refPath -Value $refClasses
    }

New-Item -ItemType Directory -Path "$OieRoot/server/refs/plugins" -Force | Out-Null
New-Item -ItemType Directory -Path "$OieRoot/client/refs/plugins" -Force | Out-Null

./scan-theirs-cross.ps1 |
    Group-Object TargetJar |
    ForEach-Object {
        $jarName = $_.Name
        $pluginBucket = if ($jarName -match '-client\.jar$') { 'client' } else { 'server' }
        $refPath = "$OieRoot/$pluginBucket/refs/plugins/$($jarName -replace '\.jar$', '.txt')"
        $refClasses = $_.Group.TargetClass | ForEach-Object { ($_ -replace '\.', '/') + '.class' } | Sort-Object -Unique

        Set-Content -LiteralPath $refPath -Value $refClasses
    }
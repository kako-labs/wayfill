param()

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repo 'design\motion-candidates\2026-11-13-old-acting-reference\direct-32'
$rawDir = Join-Path $sourceDir 'raw'
$cleanDir = Join-Path $sourceDir 'clean'
$webDir = Join-Path $repo 'web\assets\sunset'
$androidDir = Join-Path $repo 'feature\assist\src\main\res\drawable-nodpi'
$names = @('recommend','small','full','recovery')

New-Item -ItemType Directory -Force -Path $cleanDir | Out-Null
foreach ($name in $names) {
    $raw = Join-Path $rawDir "podong-$name-sheet.png"
    $clean = Join-Path $cleanDir "podong-$name-sheet.png"
    if (-not (Test-Path -LiteralPath $raw)) { throw "Missing authored 32-frame sheet: $raw" }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'clean-sprite-alpha.ps1') `
        -InputPath $raw -OutputPath $clean -Columns 8 -Rows 4
    if ($LASTEXITCODE -ne 0) { throw "Alpha cleanup failed for $name" }
    Copy-Item -LiteralPath $clean -Destination (Join-Path $webDir "podong-$name-sheet.png") -Force
    Copy-Item -LiteralPath $clean -Destination (Join-Path $androidDir "sunset_$name.png") -Force
}

Write-Output 'Installed four authored 8x4 / 32-frame WAYFILL motion sheets for web and Android.'

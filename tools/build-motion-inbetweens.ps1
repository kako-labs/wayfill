param(
    [string]$Ffmpeg = 'ffmpeg'
)

# Compatibility entry point. Optical-flow and crossfade intermediates were
# rejected after visual QA; product assets now use 32 separately authored poses.
& (Join-Path $PSScriptRoot 'install-motion-sheets.ps1')
exit $LASTEXITCODE

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$sourceDir = Join-Path $repo 'design\motion-candidates\2026-11-13-old-acting-reference'
$cleanDir = Join-Path $sourceDir 'clean'
$workDir = Join-Path $repo 'tmp\motion-inbetweens'
$webDir = Join-Path $repo 'web\assets\sunset'
$androidDir = Join-Path $repo 'feature\assist\src\main\res\drawable-nodpi'
$candidateDir = Join-Path $sourceDir 'smooth-32'
$names = @('recommend','small','full','recovery')

Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $candidateDir,$cleanDir | Out-Null

foreach ($name in $names) {
    $rawPath = Join-Path $sourceDir "podong-$name-v2-sheet.png"
    $sourcePath = Join-Path $cleanDir "podong-$name-v2-sheet.png"
    if (-not (Test-Path -LiteralPath $rawPath)) { throw "Missing source sheet: $rawPath" }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'clean-sprite-alpha.ps1') -InputPath $rawPath -OutputPath $sourcePath
    if ($LASTEXITCODE -ne 0) { throw "Alpha cleanup failed for $name" }
    $inputDir = Join-Path $workDir "$name\input"
    $outputDir = Join-Path $workDir "$name\output"
    New-Item -ItemType Directory -Force -Path $inputDir,$outputDir | Out-Null

    $sheet = [System.Drawing.Bitmap]::FromFile($sourcePath)
    try {
        if ($sheet.Width % 4 -ne 0 -or $sheet.Height % 4 -ne 0) { throw "Source sheet is not a 4x4 grid: $sourcePath" }
        $cellWidth = [int]($sheet.Width / 4)
        $cellHeight = [int]($sheet.Height / 4)
        for ($index = 0; $index -lt 16; $index++) {
            $frame = [System.Drawing.Bitmap]::new($cellWidth,$cellHeight,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            try {
                $graphics = [System.Drawing.Graphics]::FromImage($frame)
                try {
                    $source = [System.Drawing.Rectangle]::new(($index % 4) * $cellWidth,[math]::Floor($index / 4) * $cellHeight,$cellWidth,$cellHeight)
                    $destination = [System.Drawing.Rectangle]::new(0,0,$cellWidth,$cellHeight)
                    $graphics.DrawImage($sheet,$destination,$source,[System.Drawing.GraphicsUnit]::Pixel)
                } finally { $graphics.Dispose() }
                $frame.Save((Join-Path $inputDir ('frame_{0:D2}.png' -f $index)),[System.Drawing.Imaging.ImageFormat]::Png)
            } finally { $frame.Dispose() }
        }
    } finally { $sheet.Dispose() }

    Copy-Item -LiteralPath (Join-Path $inputDir 'frame_15.png') -Destination (Join-Path $inputDir 'frame_16.png') -Force
    Copy-Item -LiteralPath (Join-Path $inputDir 'frame_15.png') -Destination (Join-Path $inputDir 'frame_17.png') -Force
    $inputPattern = (Join-Path $inputDir 'frame_%02d.png')
    $outputPattern = (Join-Path $outputDir 'frame_%02d.png')
    # A simple adjacent-frame blend is intentionally used here. Optical-flow MCI
    # distorted Podong's small face and hands during large pose changes.
    & $Ffmpeg -y -hide_banner -loglevel error -framerate 10 -start_number 0 -i $inputPattern -vf 'format=rgba,minterpolate=fps=20:mi_mode=blend,format=rgba' -frames:v 32 -start_number 0 $outputPattern
    if ($LASTEXITCODE -ne 0) { throw "ffmpeg interpolation failed for $name" }

    $atlas = [System.Drawing.Bitmap]::new(2512,1256,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($atlas)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            for ($index = 0; $index -lt 32; $index++) {
                $framePath = Join-Path $outputDir ('frame_{0:D2}.png' -f $index)
                $frame = [System.Drawing.Bitmap]::FromFile($framePath)
                try { $graphics.DrawImageUnscaled($frame,($index % 8) * 314,[math]::Floor($index / 8) * 314) }
                finally { $frame.Dispose() }
            }
        } finally { $graphics.Dispose() }
        $candidatePath = Join-Path $candidateDir "podong-$name-smooth-sheet.png"
        $atlas.Save($candidatePath,[System.Drawing.Imaging.ImageFormat]::Png)
        Copy-Item -LiteralPath $candidatePath -Destination (Join-Path $webDir "podong-$name-sheet.png") -Force
        Copy-Item -LiteralPath $candidatePath -Destination (Join-Path $androidDir "sunset_$name.png") -Force
    } finally { $atlas.Dispose() }
}

Write-Output 'Built four 8x4 / 32-frame WAYFILL motion sheets for web and Android.'

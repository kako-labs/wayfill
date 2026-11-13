param(
    [Parameter(Mandatory = $true)][string]$InputPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [int]$Columns = 4,
    [int]$Rows = 4
)

$ErrorActionPreference = 'Stop'

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Runtime.InteropServices;

public static class SpriteAlphaCleaner {
    public static void Clean(string input, string output, int columns, int rows) {
        using (var source = new Bitmap(input))
        using (var bitmap = new Bitmap(source.Width, source.Height, PixelFormat.Format32bppArgb)) {
            using (var graphics = Graphics.FromImage(bitmap)) graphics.DrawImageUnscaled(source, 0, 0);
            var rect = new Rectangle(0, 0, bitmap.Width, bitmap.Height);
            var data = bitmap.LockBits(rect, ImageLockMode.ReadWrite, PixelFormat.Format32bppArgb);
            var bytes = new byte[Math.Abs(data.Stride) * data.Height];
            Marshal.Copy(data.Scan0, bytes, 0, bytes.Length);

            for (var row = 0; row < rows; row++) {
                for (var column = 0; column < columns; column++) {
                    var left = column * bitmap.Width / columns;
                    var right = (column + 1) * bitmap.Width / columns;
                    var top = row * bitmap.Height / rows;
                    var bottom = (row + 1) * bitmap.Height / rows;
                    KeepCentralComponent(bytes, data.Stride, left, top, right, bottom);
                }
            }

            Marshal.Copy(bytes, 0, data.Scan0, bytes.Length);
            bitmap.UnlockBits(data);
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(output)));
            bitmap.Save(output, ImageFormat.Png);
        }
    }

    private static void KeepCentralComponent(byte[] pixels, int stride, int left, int top, int right, int bottom) {
        var width = right - left;
        var height = bottom - top;
        var core = new bool[width * height];
        var keep = new bool[width * height];
        var queue = new Queue<int>();
        var centerX = width / 2;
        var centerY = height / 2;
        var seed = -1;

        for (var radius = 0; radius < Math.Max(width, height) / 2 && seed < 0; radius++) {
            for (var y = Math.Max(0, centerY - radius); y <= Math.Min(height - 1, centerY + radius) && seed < 0; y++) {
                for (var x = Math.Max(0, centerX - radius); x <= Math.Min(width - 1, centerX + radius); x++) {
                    if (Math.Max(Math.Abs(x - centerX), Math.Abs(y - centerY)) != radius) continue;
                    if (Alpha(pixels, stride, left + x, top + y) >= 96) { seed = y * width + x; break; }
                }
            }
        }
        if (seed < 0) return;

        core[seed] = true;
        queue.Enqueue(seed);
        var dx = new[] {-1, 0, 1, -1, 1, -1, 0, 1};
        var dy = new[] {-1, -1, -1, 0, 0, 1, 1, 1};
        while (queue.Count > 0) {
            var current = queue.Dequeue();
            var x = current % width;
            var y = current / width;
            for (var direction = 0; direction < 8; direction++) {
                var nx = x + dx[direction];
                var ny = y + dy[direction];
                if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
                var next = ny * width + nx;
                if (core[next] || Alpha(pixels, stride, left + nx, top + ny) < 96) continue;
                core[next] = true;
                queue.Enqueue(next);
            }
        }

        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                var index = y * width + x;
                if (!core[index]) continue;
                for (var oy = -2; oy <= 2; oy++) {
                    for (var ox = -2; ox <= 2; ox++) {
                        var nx = x + ox;
                        var ny = y + oy;
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height && Alpha(pixels, stride, left + nx, top + ny) > 0)
                            keep[ny * width + nx] = true;
                    }
                }
            }
        }

        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                if (keep[y * width + x]) continue;
                var offset = (top + y) * stride + (left + x) * 4;
                pixels[offset] = pixels[offset + 1] = pixels[offset + 2] = pixels[offset + 3] = 0;
            }
        }
    }

    private static byte Alpha(byte[] pixels, int stride, int x, int y) {
        return pixels[y * stride + x * 4 + 3];
    }
}
'@

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
[SpriteAlphaCleaner]::Clean($resolvedInput, $resolvedOutput, $Columns, $Rows)
Write-Output "Cleaned sprite alpha: $resolvedOutput"

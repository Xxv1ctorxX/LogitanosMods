$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$source = Join-Path $PSScriptRoot 'artwork/icon-master.png'
$target = Join-Path (Split-Path $PSScriptRoot -Parent) 'src/main/resources/assets/ludopathyroulette/icon.png'
$master = [System.Drawing.Image]::FromFile($source)
try {
    if ($master.Width -ne $master.Height) { throw 'El original del icono debe ser cuadrado.' }
    $icon = [System.Drawing.Bitmap]::new(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($icon)
    $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $attributes.SetWrapMode([System.Drawing.Drawing2D.WrapMode]::TileFlipXY)
        $graphics.DrawImage($master, [System.Drawing.Rectangle]::new(0, 0, 256, 256), 0, 0, $master.Width, $master.Height, [System.Drawing.GraphicsUnit]::Pixel, $attributes)
        $icon.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally { $attributes.Dispose(); $graphics.Dispose(); $icon.Dispose() }
} finally { $master.Dispose() }
Write-Output 'Icono actualizado: PNG de 256 x 256 desde el original de tools/artwork.'

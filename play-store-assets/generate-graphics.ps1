# Generates Google Play Store graphics that match the in-app adaptive icon.
# Output: app-icon-512.png (512x512) and feature-graphic-1024x500.png (1024x500).
# Run from any directory; files land next to this script.

Add-Type -AssemblyName System.Drawing

$outDir = Split-Path -Parent $MyInvocation.MyCommand.Path

function New-RoundedRectPath {
    param([float]$x, [float]$y, [float]$w, [float]$h, [float]$r)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

function Fill-Gradient {
    param($g, [float]$w, [float]$h)
    $start = New-Object System.Drawing.PointF(0, 0)
    $end = New-Object System.Drawing.PointF($w, $h)
    $bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        $start, $end,
        [System.Drawing.Color]::FromArgb(255, 91, 124, 255),
        [System.Drawing.Color]::FromArgb(255, 31, 46, 171)
    )
    $g.FillRectangle($bg, 0, 0, $w, $h)
    $bg.Dispose()
}

function Draw-CardStack {
    param($g, [float]$cx, [float]$cy, [float]$s)
    # Base card geometry from the 108-unit in-app icon: 60w x 28h, radius 6.
    $cardW = 60 * $s
    $cardH = 28 * $s
    $cardR = 6 * $s
    $cardX = $cx - $cardW / 2
    $cardY = $cy - $cardH / 2

    $blue = [System.Drawing.Color]::FromArgb(255, 61, 107, 232)
    $green = [System.Drawing.Color]::FromArgb(255, 34, 221, 136)

    foreach ($rot in @(-14, -7, 0)) {
        $alpha = if ($rot -eq -14) { 0x40 } elseif ($rot -eq -7) { 0xA0 } else { 0xFF }
        $color = [System.Drawing.Color]::FromArgb($alpha, 255, 255, 255)
        $state = $g.Save()
        $g.TranslateTransform([float]$cx, [float]$cy)
        $g.RotateTransform([float]$rot)
        $g.TranslateTransform(-[float]$cx, -[float]$cy)
        $path = New-RoundedRectPath $cardX $cardY $cardW $cardH $cardR
        $brush = New-Object System.Drawing.SolidBrush($color)
        $g.FillPath($brush, $path)
        $brush.Dispose()
        $path.Dispose()
        $g.Restore($state)
    }

    # Chip on the front card.
    $chip = New-RoundedRectPath ($cardX + 1.5 * $s) ($cardY + 6 * $s) (7 * $s) (6 * $s) (1.5 * $s)
    $b = New-Object System.Drawing.SolidBrush($blue)
    $g.FillPath($b, $chip); $chip.Dispose()

    # Stripe along the bottom of the front card.
    $stripe = New-RoundedRectPath ($cardX + 1 * $s) ($cardY + 20 * $s) (27 * $s) (2 * $s) (1 * $s)
    $g.FillPath($b, $stripe); $stripe.Dispose()
    $b.Dispose()

    # Green "paid" dot top-right.
    $dotR = 2.6 * $s
    $dotCx = $cardX + $cardW - 6 * $s
    $dotCy = $cardY + 6.5 * $s
    $gb = New-Object System.Drawing.SolidBrush($green)
    $g.FillEllipse($gb, $dotCx - $dotR, $dotCy - $dotR, $dotR * 2, $dotR * 2)
    $gb.Dispose()
}

# ---------- APP ICON 512x512 ----------
$icon = New-Object System.Drawing.Bitmap 512, 512
$gi = [System.Drawing.Graphics]::FromImage($icon)
$gi.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gi.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
Fill-Gradient $gi 512 512
# 512/108 ≈ 4.74. Centered.
Draw-CardStack $gi 256 256 (512 / 108)
$iconPath = Join-Path $outDir "app-icon-512.png"
$icon.Save($iconPath, [System.Drawing.Imaging.ImageFormat]::Png)
$gi.Dispose(); $icon.Dispose()

# ---------- FEATURE GRAPHIC 1024x500 ----------
$feat = New-Object System.Drawing.Bitmap 1024, 500
$gf = [System.Drawing.Graphics]::FromImage($feat)
$gf.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$gf.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$gf.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

Fill-Gradient $gf 1024 500

# Card stack on the left, scaled larger than the app icon for hero feel.
Draw-CardStack $gf 240 250 2.9

# Text on the right.
$family = New-Object System.Drawing.FontFamily("Segoe UI")
$titleFont = New-Object System.Drawing.Font($family, 72, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$taglineFont = New-Object System.Drawing.Font($family, 30, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$subFont = New-Object System.Drawing.Font($family, 22, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)

$white = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
$dim = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230, 255, 255, 255))
$dimmer = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(170, 255, 255, 255))

$mid = [string][char]0x00B7
$gf.DrawString("Loan Tracker", $titleFont, $white, 470, 158)
$gf.DrawString("Track loans between friends", $taglineFont, $dim, 472, 262)
$gf.DrawString("Real-time  $mid  Private  $mid  No ads", $subFont, $dimmer, 472, 315)

$titleFont.Dispose(); $taglineFont.Dispose(); $subFont.Dispose()
$white.Dispose(); $dim.Dispose(); $dimmer.Dispose()

$featPath = Join-Path $outDir "feature-graphic-1024x500.png"
$feat.Save($featPath, [System.Drawing.Imaging.ImageFormat]::Png)
$gf.Dispose(); $feat.Dispose()

Write-Output "Generated:"
Write-Output "  $iconPath"
Write-Output "  $featPath"

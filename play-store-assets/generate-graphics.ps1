# Generates Google Play graphics and legacy launcher mipmaps that match the
# in-app adaptive icon: a geometric Euro in thick round-capped strokes on a
# diagonal deep-pine -> emerald gradient with soft accent circles.
#
# Output:
#   app-icon-512.png            (Play Store listing icon, full-bleed square)
#   feature-graphic-1024x500.png
#   ../app/src/main/res/mipmap-*/ic_launcher.png / ic_launcher_round.png
#
# Run from any directory; store files land next to this script.

Add-Type -AssemblyName System.Drawing

$outDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$resDir = Join-Path $outDir "..\app\src\main\res"
$fontPath = Join-Path $resDir "font\instrument_serif.ttf"

$DeepPine = [System.Drawing.Color]::FromArgb(255, 0x0C, 0x44, 0x33)
$Emerald = [System.Drawing.Color]::FromArgb(255, 0x1F, 0xA2, 0x68)
$Cream = [System.Drawing.Color]::FromArgb(255, 0xFD, 0xFB, 0xF5)

$fonts = New-Object System.Drawing.Text.PrivateFontCollection
$fonts.AddFontFile($fontPath)
$serif = $fonts.Families[0]

function New-Canvas {
    param([int]$w, [int]$h)
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    return $bmp, $g
}

function Fill-ModernPine {
    param($g, [float]$w, [float]$h)
    # Diagonal gradient with two soft translucent circles, mirroring
    # ic_launcher_background.xml.
    $rect = New-Object System.Drawing.RectangleF(0, 0, $w, $h)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.PointF(0, 0)),
        (New-Object System.Drawing.PointF($w, $h)),
        $DeepPine, $Emerald
    )
    $g.FillRectangle($brush, $rect)
    $brush.Dispose()

    $s = [Math]::Min($w, $h)
    $soft1 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(20, 255, 255, 255))
    $r1 = $s * 0.352
    $g.FillEllipse($soft1, $w - $r1, $s * 0.204 - $r1, $r1 * 2, $r1 * 2)
    $soft1.Dispose()
    $soft2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(13, 255, 255, 255))
    $r2 = $s * 0.278
    $g.FillEllipse($soft2, $s * 0.056 - $r2, $h - $s * 0.111 - $r2, $r2 * 2, $r2 * 2)
    $soft2.Dispose()
}

# Draws the geometric Euro mark centered at ($cx, $cy). $unit is one
# adaptive-icon viewport unit; the mark is ~42.5 units tall (same proportions
# as ic_launcher_foreground.xml, where 1 unit = visible-icon-size / 72).
function Draw-EuroMark {
    param($g, [float]$cx, [float]$cy, [float]$unit)
    $pen = New-Object System.Drawing.Pen($Cream, (7.5 * $unit))
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round

    # C-arc open to the right: centered 2 units right of the mark center.
    $r = 17.5 * $unit
    $arcCx = $cx + 2 * $unit
    $g.DrawArc($pen, $arcCx - $r, $cy - $r, $r * 2, $r * 2, 55, 250)

    # The two Euro bars.
    $x1 = $cx - 19 * $unit
    $x2 = $cx + 3.5 * $unit
    $dy = 5.4 * $unit
    $g.DrawLine($pen, $x1, $cy - $dy, $x2, $cy - $dy)
    $g.DrawLine($pen, $x1, $cy + $dy, $x2, $cy + $dy)
    $pen.Dispose()
}

function Draw-IconInto {
    param($g, [float]$size)
    Fill-ModernPine $g $size $size
    # Adaptive icons show ~72 of the 108dp canvas; full-bleed renders match
    # that proportion with 1 viewport unit = size / 72.
    Draw-EuroMark $g ($size / 2) ($size / 2) ($size / 72)
}

# ---------- APP ICON 512x512 (Play masks its own corners) ----------
$icon, $gi = New-Canvas 512 512
Draw-IconInto $gi 512
$iconPath = Join-Path $outDir "app-icon-512.png"
$icon.Save($iconPath, [System.Drawing.Imaging.ImageFormat]::Png)
$gi.Dispose(); $icon.Dispose()
Write-Output "Generated: $iconPath"

# ---------- LEGACY LAUNCHER MIPMAPS ----------
# Unused at runtime on minSdk 30 (the adaptive icon wins), kept for tools
# that read the bitmap fallbacks.
$densities = @(
    @{ dir = "mipmap-mdpi"; px = 48 },
    @{ dir = "mipmap-hdpi"; px = 72 },
    @{ dir = "mipmap-xhdpi"; px = 96 },
    @{ dir = "mipmap-xxhdpi"; px = 144 },
    @{ dir = "mipmap-xxxhdpi"; px = 192 }
)
foreach ($d in $densities) {
    $px = $d.px
    foreach ($variant in @("ic_launcher", "ic_launcher_round")) {
        $bmp, $g = New-Canvas $px $px
        $mask = New-Object System.Drawing.Drawing2D.GraphicsPath
        if ($variant -eq "ic_launcher_round") {
            $mask.AddEllipse(0, 0, $px, $px)
        } else {
            $r = [float]($px * 0.17); $dm = $r * 2
            $mask.AddArc(0, 0, $dm, $dm, 180, 90)
            $mask.AddArc($px - $dm, 0, $dm, $dm, 270, 90)
            $mask.AddArc($px - $dm, $px - $dm, $dm, $dm, 0, 90)
            $mask.AddArc(0, $px - $dm, $dm, $dm, 90, 90)
            $mask.CloseFigure()
        }
        $g.SetClip($mask)
        Draw-IconInto $g $px
        $g.ResetClip(); $mask.Dispose()
        $file = Join-Path (Join-Path $resDir $d.dir) "$variant.png"
        $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
        $g.Dispose(); $bmp.Dispose()
    }
    Write-Output "Generated: $($d.dir) launcher PNGs"
}

# ---------- FEATURE GRAPHIC 1024x500 ----------
$feat, $gf = New-Canvas 1024 500
$gf.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
Fill-ModernPine $gf 1024 500

# Euro mark as the left-side brand block (~300px tall / 42.5 units).
Draw-EuroMark $gf 235 250 (300 / 42.5)

# Serif wordmark, drawn as a path so the private font renders reliably.
$creamBrush = New-Object System.Drawing.SolidBrush($Cream)
$title = New-Object System.Drawing.Drawing2D.GraphicsPath
$title.AddString(
    "Loan Tracker", $serif,
    [int][System.Drawing.FontStyle]::Regular, 92,
    (New-Object System.Drawing.PointF(455, 160)),
    [System.Drawing.StringFormat]::GenericTypographic
)
$gf.FillPath($creamBrush, $title)
$title.Dispose(); $creamBrush.Dispose()

$ui = New-Object System.Drawing.FontFamily("Segoe UI")
$taglineFont = New-Object System.Drawing.Font($ui, 30, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$subFont = New-Object System.Drawing.Font($ui, 22, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$dim = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(230, 0xFD, 0xFB, 0xF5))
$dimmer = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(175, 0xFD, 0xFB, 0xF5))
$mid = [string][char]0x00B7
$gf.DrawString("Clear terms for loans between friends", $taglineFont, $dim, 458, 292)
$gf.DrawString("Offers & requests  $mid  Payment plans  $mid  No ads", $subFont, $dimmer, 459, 345)
$taglineFont.Dispose(); $subFont.Dispose(); $dim.Dispose(); $dimmer.Dispose()

$featPath = Join-Path $outDir "feature-graphic-1024x500.png"
$feat.Save($featPath, [System.Drawing.Imaging.ImageFormat]::Png)
$gf.Dispose(); $feat.Dispose()
Write-Output "Generated: $featPath"

$fonts.Dispose()

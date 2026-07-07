# Generates Google Play graphics and legacy launcher mipmaps that match the
# in-app adaptive icon: a serif Euro (Instrument Serif, bundled in res/font)
# on the warm-ledger pine-to-gold gradient.
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

$PineGreen = [System.Drawing.Color]::FromArgb(255, 0x1B, 0x5E, 0x43)
$LedgerGold = [System.Drawing.Color]::FromArgb(255, 0x7B, 0x5F, 0x0E)
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

function Fill-LedgerGradient {
    param($g, [float]$w, [float]$h)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.PointF(0, 0)),
        (New-Object System.Drawing.PointF($w, $h)),
        $PineGreen, $LedgerGold
    )
    $g.FillRectangle($brush, 0, 0, $w, $h)
    $brush.Dispose()

    # Soft top-left highlight for depth, like the adaptive-icon background.
    $hl = New-Object System.Drawing.Drawing2D.GraphicsPath
    $hl.AddEllipse($w * -0.45, $h * -0.45, $w * 1.2, $h * 1.2)
    $pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush($hl)
    $pgb.CenterColor = [System.Drawing.Color]::FromArgb(0x2E, 255, 255, 255)
    $pgb.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 255, 255, 255))
    $g.FillPath($pgb, $hl)
    $pgb.Dispose(); $hl.Dispose()
}

# Returns a GraphicsPath of the Euro glyph scaled to $targetH tall and
# centered at ($cx, $cy).
function New-EuroPath {
    param([float]$cx, [float]$cy, [float]$targetH)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $fmt = [System.Drawing.StringFormat]::GenericTypographic
    $path.AddString(
        [string][char]0x20AC, $serif,
        [int][System.Drawing.FontStyle]::Regular, 100,
        (New-Object System.Drawing.PointF(0, 0)), $fmt
    )
    $b = $path.GetBounds()
    $s = $targetH / $b.Height
    $m = New-Object System.Drawing.Drawing2D.Matrix
    $m.Translate($cx - $s * ($b.X + $b.Width / 2), $cy - $s * ($b.Y + $b.Height / 2))
    $m.Scale($s, $s)
    $path.Transform($m)
    $m.Dispose()
    return $path
}

function Draw-IconInto {
    param($g, [float]$size)
    Fill-LedgerGradient $g $size $size
    # Adaptive icons show ~72 of the 108dp canvas, where the glyph is 48 tall;
    # full-bleed renders match that proportion at 48/72 ≈ 0.65 of the height.
    $euro = New-EuroPath ($size / 2) ($size / 2) ($size * 0.62)
    $brush = New-Object System.Drawing.SolidBrush($Cream)
    $g.FillPath($brush, $euro)
    $brush.Dispose(); $euro.Dispose()
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
Fill-LedgerGradient $gf 1024 500

# Big serif Euro as the left-side mark.
$euro = New-EuroPath 235 250 300
$creamBrush = New-Object System.Drawing.SolidBrush($Cream)
$gf.FillPath($creamBrush, $euro)
$euro.Dispose()

# Serif wordmark, drawn as a path so the private font renders reliably.
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

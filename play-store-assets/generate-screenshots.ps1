# Frames raw phone screenshots into branded Google Play listing slides that
# match the app icon: a geometric Euro brand mark and a headline over the
# diagonal deep-pine -> emerald gradient, with the screenshot floating below
# on rounded corners with a soft shadow.
#
# Input:  screenshots-raw/<file>.png   (real device captures — see README)
# Output: screenshots/NN-<name>.png    (1080x1920, 9:16, Play-ready)
#
# If a raw file is missing the slide still renders with a neutral placeholder,
# so you can preview the layout before dropping the real captures in.
#
# Run from any directory; files land next to this script.

Add-Type -AssemblyName System.Drawing

$outDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$resDir = Join-Path $outDir "..\app\src\main\res"
$fontPath = Join-Path $resDir "font\instrument_serif.ttf"
$rawDir = Join-Path $outDir "screenshots-raw"
$shotsDir = Join-Path $outDir "screenshots"
New-Item -ItemType Directory -Force -Path $shotsDir | Out-Null

$W = 1080
$H = 1920

$DeepPine = [System.Drawing.Color]::FromArgb(255, 0x0C, 0x44, 0x33)
$Emerald = [System.Drawing.Color]::FromArgb(255, 0x1F, 0xA2, 0x68)
$Cream = [System.Drawing.Color]::FromArgb(255, 0xFD, 0xFB, 0xF5)

$fonts = New-Object System.Drawing.Text.PrivateFontCollection
$fonts.AddFontFile($fontPath)
$serif = $fonts.Families[0]

# ---- Listing order + captions. Edit freely; save raw files with these names. ----
$slides = @(
    @{ file = "home-owe.png";    head = "Always know`nwhat you owe";        sub = "And exactly what's owed back to you" }
    @{ file = "loan-detail.png"; head = "Every term in`nblack and white";   sub = "Amount, interest and due date, agreed up front" }
    @{ file = "new-loan.png";    head = "Set up a loan`nin under a minute";  sub = "Offer to lend, or ask to borrow" }
    @{ file = "sign-in.png";     head = "For loans between`npeople you trust"; sub = "One tap to sign in - both sides see the same terms" }
    @{ file = "active.png";      head = "Offers, requests`nand repayments";  sub = "Received, sent and active in one place" }
)

function New-RoundRect {
    param([float]$x, [float]$y, [float]$w, [float]$h, [float]$r)
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $p.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $p.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

function Fill-Background {
    param($g)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.PointF(0, 0)),
        (New-Object System.Drawing.PointF($W, $H)),
        $DeepPine, $Emerald
    )
    $g.FillRectangle($brush, 0, 0, $W, $H)
    $brush.Dispose()
    # Soft accent circles echoing the app icon.
    $c1 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(18, 255, 255, 255))
    $g.FillEllipse($c1, $W - 220, -260, 620, 620)
    $c1.Dispose()
    $c2 = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(12, 255, 255, 255))
    $g.FillEllipse($c2, -300, $H - 520, 640, 640)
    $c2.Dispose()
}

# Geometric Euro brand mark centered at ($cx,$cy); $unit sizes it (mark ~42.5 units tall).
function Draw-EuroMark {
    param($g, [float]$cx, [float]$cy, [float]$unit)
    $pen = New-Object System.Drawing.Pen($Cream, (7.5 * $unit))
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $r = 17.5 * $unit
    $arcCx = $cx + 2 * $unit
    $g.DrawArc($pen, $arcCx - $r, $cy - $r, $r * 2, $r * 2, 55, 250)
    $x1 = $cx - 19 * $unit
    $x2 = $cx + 3.5 * $unit
    $dy = 5.4 * $unit
    $g.DrawLine($pen, $x1, $cy - $dy, $x2, $cy - $dy)
    $g.DrawLine($pen, $x1, $cy + $dy, $x2, $cy + $dy)
    $pen.Dispose()
}

# Neutral stand-in when a real capture isn't present yet.
function New-Placeholder {
    param([string]$label)
    $bmp = New-Object System.Drawing.Bitmap 1080, 2280
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::FromArgb(255, 0x0B, 0x0F, 0x0D))
    $bar = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 0x18, 0x22, 0x1D))
    foreach ($y in 260, 470, 680, 890, 1100) {
        $rr = New-RoundRect 90 $y 900 150 28
        $g.FillPath($bar, $rr); $rr.Dispose()
    }
    $bar.Dispose()
    $f = New-Object System.Drawing.Font("Segoe UI", 44, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::Center
    $tb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(150, 255, 255, 255))
    $g.DrawString("[$label]", $f, $tb, (New-Object System.Drawing.RectangleF(0, 1500, 1080, 80)), $sf)
    $f.Dispose(); $tb.Dispose(); $g.Dispose()
    return $bmp
}

$headFont = New-Object System.Drawing.Font("Segoe UI", 62, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$subFont = New-Object System.Drawing.Font("Segoe UI", 33, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$creamBrush = New-Object System.Drawing.SolidBrush($Cream)
$subBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(205, 0xFD, 0xFB, 0xF5))
$centre = New-Object System.Drawing.StringFormat
$centre.Alignment = [System.Drawing.StringAlignment]::Center
$centre.LineAlignment = [System.Drawing.StringAlignment]::Near

$i = 0
foreach ($slide in $slides) {
    $i++
    $bmp = New-Object System.Drawing.Bitmap $W, $H
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    Fill-Background $g

    # Brand mark, then headline + subhead.
    Draw-EuroMark $g ($W / 2) 130 (74 / 42.5)
    $headTop = 220
    $headSize = $g.MeasureString($slide.head, $headFont, $W, $centre)
    $g.DrawString($slide.head, $headFont, $creamBrush,
        (New-Object System.Drawing.RectangleF(0, $headTop, $W, 400)), $centre)
    $subTop = $headTop + $headSize.Height + 22
    $subSize = $g.MeasureString($slide.sub, $subFont, ($W - 160), $centre)
    $g.DrawString($slide.sub, $subFont, $subBrush,
        (New-Object System.Drawing.RectangleF(80, $subTop, ($W - 160), 200)), $centre)

    # Load the capture (or a placeholder) and fit it into the lower region.
    $rawPath = Join-Path $rawDir $slide.file
    $usingReal = Test-Path $rawPath
    $shot = if ($usingReal) { [System.Drawing.Image]::FromFile($rawPath) }
            else { New-Placeholder ($slide.file -replace '\.png$', '') }

    $regionTop = $subTop + $subSize.Height + 70
    $regionBottom = $H - 70
    $regionH = $regionBottom - $regionTop
    $maxW = $W - 220
    $ar = $shot.Width / $shot.Height
    $th = $regionH
    $tw = $th * $ar
    if ($tw -gt $maxW) { $tw = $maxW; $th = $tw / $ar }
    $x = ($W - $tw) / 2
    $y = $regionTop + ($regionH - $th) / 2
    $radius = [float]($tw * 0.055)

    # Soft drop shadow (layered low-alpha rounded rects — GDI+ has no blur).
    for ($s = 12; $s -ge 1; $s--) {
        $sp = New-RoundRect ($x - $s) ($y + $s * 1.4 + 8) ($tw + 2 * $s) ($th + 2 * $s) ($radius + $s)
        $sb = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(5, 0, 0, 0))
        $g.FillPath($sb, $sp); $sb.Dispose(); $sp.Dispose()
    }

    # Clip to rounded corners, draw the screenshot, then a hairline border.
    $clip = New-RoundRect $x $y $tw $th $radius
    $g.SetClip($clip)
    $g.DrawImage($shot, $x, $y, $tw, $th)
    $g.ResetClip()
    $borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(70, 255, 255, 255), 2)
    $g.DrawPath($borderPen, $clip)
    $borderPen.Dispose(); $clip.Dispose(); $shot.Dispose()

    $name = "{0:D2}-{1}" -f $i, ($slide.file -replace '\.png$', '')
    $file = Join-Path $shotsDir "$name.png"
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    $tag = if ($usingReal) { "capture" } else { "PLACEHOLDER" }
    Write-Output "Generated: screenshots/$name.png ($tag)"
}

$headFont.Dispose(); $subFont.Dispose(); $creamBrush.Dispose(); $subBrush.Dispose()
$fonts.Dispose()

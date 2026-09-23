# Generate cavedream.ico: pixel dreamer sleeping against a crescent moon (32x32 logical, 8x scale).
# Output: desktop/pkg/cavedream.ico  (used by tools/package-exe.ps1 via jpackage --icon)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$scale = 8
$bmp = New-Object System.Drawing.Bitmap(256, 256)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::Transparent)

function Col([int]$hex) {
    return [System.Drawing.Color]::FromArgb(255, ($hex -shr 16) -band 0xFF, ($hex -shr 8) -band 0xFF, $hex -band 0xFF)
}

$cMoon  = Col 0xF5D76E
$cEdge  = Col 0xD9B44A
$cHair  = Col 0x3A2A55
$cSkin  = Col 0xE8C8A0
$cEye   = Col 0x222233
$cRobe  = Col 0x8E7CC3
$cRobeD = Col 0x6E5DA6
$cArm   = Col 0xA494D6
$cPants = Col 0x4A4A66
$cZ     = Col 0x9FC6FF
$cStar  = Col 0xFFF6C8

# Zzz bitmap pattern (3 wide x 5 tall)
$zPat = @(@(1,1,1), @(0,0,1), @(0,1,1), @(1,0,0), @(1,1,1))

function InZ([int]$x, [int]$y, [int]$ox, [int]$oy) {
    $i = $x - $ox; $j = $y - $oy
    if ($i -lt 0 -or $i -gt 2 -or $j -lt 0 -or $j -gt 4) { return $false }
    return $zPat[$j][$i] -eq 1
}

for ($y = 0; $y -lt 32; $y++) {
    for ($x = 0; $x -lt 32; $x++) {
        $c = $null
        $d1 = [Math]::Sqrt(($x - 14) * ($x - 14) + ($y - 16) * ($y - 16))
        $d2 = [Math]::Sqrt(($x - 19) * ($x - 19) + ($y - 11) * ($y - 11))
        if ($d1 -le 12.5 -and $d2 -gt 11.5) {
            $c = if ($d1 -gt 10.8) { $cEdge } else { $cMoon }
        }
        # --- dreamer (back leaning on the crescent's left arm, sitting in the hollow) ---
        $head = (($x - 10.5) * ($x - 10.5)) + (($y - 19) * ($y - 19))
        if ($head -le 8.4 -and $y -ge 16 -and $y -le 21) {
            if ($y -le 17.6 -and $x -le 12.4) { $c = $cHair }
            elseif ($x -lt 8.6 -and $y -le 20.4) { $c = $cHair }
            else { $c = $cSkin }
        }
        if ($y -ge 18.4 -and $y -le 19.4 -and $x -ge 11 -and $x -le 12.6) { $c = $cEye }  # closed eye
        if ($x -ge 7.6 -and $x -le 12.4 -and $y -ge 21.6 -and $y -le 26.4) {
            $c = if ($x -lt 9) { $cRobeD } else { $cRobe }                               # torso
        }
        if ($x -ge 12.6 -and $x -le 15.6 -and $y -ge 22.6 -and $y -le 23.9) { $c = $cArm } # arm resting
        if ($x -ge 12.6 -and $x -le 17.8 -and $y -ge 24.6 -and $y -le 26.4) { $c = $cRobe } # thigh (knee up)
        if ($x -ge 16.6 -and $x -le 19.2 -and $y -ge 26.6 -and $y -le 28.4) { $c = $cPants } # shin/foot
        # --- zzz rising to the right ---
        if ((InZ $x $y 20 13) -or (InZ $x $y 23 9) -or (InZ $x $y 26 5)) { $c = $cZ }
        # --- stars ---
        if (($x -eq 5 -and $y -eq 8) -or ($x -eq 27 -and $y -eq 15) -or ($x -eq 8 -and $y -eq 4) -or ($x -eq 24 -and $y -eq 22)) { $c = $cStar }

        if ($null -ne $c) {
            $b = New-Object System.Drawing.SolidBrush $c
            $g.FillRectangle($b, $x * $scale, $y * $scale, $scale, $scale)
            $b.Dispose()
        }
    }
}
$g.Dispose()

# PNG bytes -> ICO (single 256x256 PNG entry)
$ms = New-Object System.IO.MemoryStream
$bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
$png = $ms.ToArray()
$bmp.Dispose(); $ms.Dispose()

$ico = New-Object System.IO.MemoryStream
$bw = New-Object System.IO.BinaryWriter($ico)
$bw.Write([UInt16]0); $bw.Write([UInt16]1); $bw.Write([UInt16]1)   # reserved, type=icon, count=1
$bw.Write([Byte]0); $bw.Write([Byte]0)                             # 0 = 256px
$bw.Write([Byte]0); $bw.Write([Byte]0)                             # colors, reserved
$bw.Write([UInt16]1); $bw.Write([UInt16]32)                        # planes, bpp
$bw.Write([UInt32]$png.Length); $bw.Write([UInt32]22)              # size, offset
$bw.Write($png)
$bw.Flush()
$bytes = $ico.ToArray()
$bw.Dispose(); $ico.Dispose()

$outDir = 'D:\Dev\projects\CaveDream\desktop\pkg'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$outIco = Join-Path $outDir 'cavedream.ico'
[System.IO.File]::WriteAllBytes($outIco, $bytes)
Write-Host ('icon written: ' + $outIco + ' (' + $bytes.Length + ' bytes)')

Add-Type -AssemblyName System.Drawing
$diagramRoot = Join-Path $PSScriptRoot 'diagrams'
Get-ChildItem -LiteralPath $diagramRoot -Filter '*.json' | ForEach-Object {
    $diagramSpec = Get-Content -LiteralPath $_.FullName -Raw | ConvertFrom-Json
    $bitmap = New-Object System.Drawing.Bitmap ([int]$diagramSpec.width * 2), ([int]$diagramSpec.height * 2)
    $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
    $graphics.Clear([System.Drawing.Color]::White)
    $graphics.ScaleTransform(2, 2)
    $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $pen = New-Object System.Drawing.Pen ([System.Drawing.ColorTranslator]::FromHtml('#17324d')), 1.5
    $ink = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#17324d'))
    foreach ($shape in $diagramSpec.shapes) {
        switch ($shape.kind) {
            'text' {
                $fontStyle = [System.Drawing.FontStyle]::Regular
                if ($shape.bold) { $fontStyle = [System.Drawing.FontStyle]::Bold }
                $font = New-Object System.Drawing.Font 'Arial', ([single]$shape.size), $fontStyle, ([System.Drawing.GraphicsUnit]::Pixel)
                $format = [System.Drawing.StringFormat]::GenericTypographic.Clone()
                if ($shape.center) { $format.Alignment = [System.Drawing.StringAlignment]::Center }
                $point = New-Object System.Drawing.PointF ([single]$shape.x), ([single]$shape.y)
                $graphics.DrawString([string]$shape.text, $font, $ink, $point, $format)
                $font.Dispose(); $format.Dispose()
            }
            'line' { $graphics.DrawLine($pen, [single]$shape.x1, [single]$shape.y1, [single]$shape.x2, [single]$shape.y2) }
            'rect' {
                $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($shape.fill))
                $graphics.FillRectangle($brush, [single]$shape.x, [single]$shape.y, [single]$shape.w, [single]$shape.h)
                $graphics.DrawRectangle($pen, [single]$shape.x, [single]$shape.y, [single]$shape.w, [single]$shape.h)
                $brush.Dispose()
            }
            'ellipse' {
                $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($shape.fill))
                $graphics.FillEllipse($brush, [single]$shape.x, [single]$shape.y, [single]$shape.w, [single]$shape.h)
                $graphics.DrawEllipse($pen, [single]$shape.x, [single]$shape.y, [single]$shape.w, [single]$shape.h)
                $brush.Dispose()
            }
            'polygon' {
                $points = [System.Drawing.PointF[]]@($shape.points | ForEach-Object { New-Object System.Drawing.PointF ([single]$_[0]), ([single]$_[1]) })
                $brush = New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($shape.fill))
                $graphics.FillPolygon($brush, $points); $graphics.DrawPolygon($pen, $points)
                $brush.Dispose()
            }
        }
    }
    $pngPath = Join-Path $diagramRoot ($_.BaseName + '.png')
    $bitmap.Save($pngPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $graphics.Dispose(); $bitmap.Dispose(); $pen.Dispose(); $ink.Dispose()
    Write-Output $pngPath
}

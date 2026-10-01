Add-Type -AssemblyName System.Runtime.WindowsRuntime
[Windows.Storage.StorageFile, Windows.Storage, ContentType=WindowsRuntime] > $null
[Windows.Data.Pdf.PdfDocument, Windows.Data.Pdf, ContentType=WindowsRuntime] > $null
[Windows.Storage.Streams.InMemoryRandomAccessStream, Windows.Storage.Streams, ContentType=WindowsRuntime] > $null
[Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType=WindowsRuntime] > $null
$asyncMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.IsGenericMethodDefinition -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
} | Select-Object -First 1
$actionMethod = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and -not $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncAction'
} | Select-Object -First 1
function Await-Operation($operation, $resultType) {
    $task = $asyncMethod.MakeGenericMethod($resultType).Invoke($null, @($operation))
    $task.Wait()
    return $task.Result
}
$pdfPath = Join-Path $PSScriptRoot 'HRGenius_Project_Report.pdf'
$file = Await-Operation ([Windows.Storage.StorageFile]::GetFileFromPathAsync($pdfPath)) ([Windows.Storage.StorageFile])
$pdf = Await-Operation ([Windows.Data.Pdf.PdfDocument]::LoadFromFileAsync($file)) ([Windows.Data.Pdf.PdfDocument])
$previewDir = Join-Path $PSScriptRoot 'previews'
New-Item -ItemType Directory -Path $previewDir -Force > $null
foreach ($pageIndex in @(4,5,6,8,10)) {
    $page = $pdf.GetPage($pageIndex)
    $stream = New-Object Windows.Storage.Streams.InMemoryRandomAccessStream
    $task = $actionMethod.Invoke($null, @($page.RenderToStreamAsync($stream)))
    $task.Wait()
    $reader = New-Object Windows.Storage.Streams.DataReader ($stream.GetInputStreamAt(0))
    $null = Await-Operation ($reader.LoadAsync([uint32]$stream.Size)) ([uint32])
    $bytes = New-Object byte[] ([int]$stream.Size)
    $reader.ReadBytes($bytes)
    $imagePath = Join-Path $previewDir ('page-' + ($pageIndex + 1) + '.png')
    [System.IO.File]::WriteAllBytes($imagePath, $bytes)
    $reader.Dispose(); $stream.Dispose(); $page.Dispose()
    Write-Output $imagePath
}

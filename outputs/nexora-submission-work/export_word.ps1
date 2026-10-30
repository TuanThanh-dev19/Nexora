param([Parameter(Mandatory=$true)][string]$InputDocument,[Parameter(Mandatory=$true)][string]$OutputPdf)
$ErrorActionPreference = 'Stop'
$wordApp = $null
$wordDocument = $null
try {
    $wordApp = New-Object -ComObject Word.Application
    $wordApp.Visible = $false
    $wordApp.DisplayAlerts = 0
    $wordDocument = $wordApp.Documents.Open($InputDocument, $false, $true)
    $wordDocument.Repaginate()
    $pageTotal = $wordDocument.ComputeStatistics(2)
    $wordDocument.ExportAsFixedFormat($OutputPdf, 17)
    Write-Output "Exported PDF; pages=$pageTotal"
}
finally {
    if ($null -ne $wordDocument) { $wordDocument.Close(0); [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($wordDocument) }
    if ($null -ne $wordApp) { $wordApp.Quit(); [void][System.Runtime.InteropServices.Marshal]::ReleaseComObject($wordApp) }
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}

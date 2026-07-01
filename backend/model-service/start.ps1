param(
    [string]$HostAddress = "127.0.0.1",
    [int]$Port = 18080
)

$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

$env:PYTHONUTF8 = "1"
$env:PYTHONIOENCODING = "utf-8"

$pythonBin = $env:PYTHON_BIN
if ([string]::IsNullOrWhiteSpace($pythonBin)) {
    $venvPython = Join-Path $PSScriptRoot ".venv\Scripts\python.exe"
    if (Test-Path $venvPython) {
        $pythonBin = $venvPython
    } else {
        $pythonBin = "python"
    }
}

$outLog = Join-Path $PSScriptRoot "model-service-run.log"
$errLog = Join-Path $PSScriptRoot "model-service-run.err.log"

$process = Start-Process `
    -FilePath $pythonBin `
    -ArgumentList @("-X", "utf8", "-m", "uvicorn", "model_server:app", "--host", $HostAddress, "--port", $Port) `
    -WorkingDirectory $PSScriptRoot `
    -RedirectStandardOutput $outLog `
    -RedirectStandardError $errLog `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Model service started on http://$HostAddress`:$Port"
Write-Host "PID: $($process.Id)"
Write-Host "stdout: $outLog"
Write-Host "stderr: $errLog"

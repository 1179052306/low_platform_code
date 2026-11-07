$ErrorActionPreference = "Stop"
$log = "C:\flutter-download.log"
"START $(Get-Date)" | Out-File $log

$zip = "C:\flutter_sdk.zip"
$url = "https://storage.googleapis.com/flutter_infra_release/releases/stable/windows/flutter_windows_3.27.0-stable.zip"

"Downloading $url" | Out-File $log -Append
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $wc = New-Object System.Net.WebClient
    $wc.DownloadFile($url, $zip)
    "DOWNLOAD DONE $(Get-Date)" | Out-File $log -Append
} catch {
    "DOWNLOAD FAILED: $_" | Out-File $log -Append
    exit 1
}

"Extracting..." | Out-File $log -Append
try {
    Expand-Archive -Path $zip -DestinationPath "C:\" -Force
    "EXTRACT DONE $(Get-Date)" | Out-File $log -Append
} catch {
    "EXTRACT FAILED: $_" | Out-File $log -Append
    exit 1
}

Remove-Item $zip -Force -ErrorAction SilentlyContinue
"ALL DONE $(Get-Date)" | Out-File $log -Append
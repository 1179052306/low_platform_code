$ErrorActionPreference = "Stop"
$log = "D:\code\flutter-download.log"
"START $(Get-Date)" | Out-File $log -Encoding utf8

if (Test-Path "C:\flutter\bin\flutter.bat") {
    "Already exists" | Out-File $log -Append -Encoding utf8
    exit 0
}

if (Test-Path "C:\flutter") {
    Remove-Item "C:\flutter" -Recurse -Force -ErrorAction SilentlyContinue
}

"Cloning from gitee..." | Out-File $log -Append -Encoding utf8
& git clone --depth 1 -b stable https://gitee.com/mirrors/Flutter.git C:\flutter 2>&1 | Out-File $log -Append -Encoding utf8

if (Test-Path "C:\flutter\bin\flutter.bat") {
    "CLONE DONE $(Get-Date)" | Out-File $log -Append -Encoding utf8
} else {
    "CLONE FAILED" | Out-File $log -Append -Encoding utf8
    exit 1
}
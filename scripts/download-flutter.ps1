$ErrorActionPreference = "SilentlyContinue"
if (Test-Path C:\flutter\.git) {
    "Already cloned" | Out-File C:\flutter-download.log
    exit 0
}
git clone --depth 1 -b stable https://github.com/flutter/flutter.git C:\flutter 2>&1 | Out-File C:\flutter-download.log
"CLONE_DONE" | Out-File C:\flutter-download.log -Append
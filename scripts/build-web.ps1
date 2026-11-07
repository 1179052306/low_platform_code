$env:FLUTTER_STORAGE_BASE_URL = "https://storage.flutter-io.cn"
$env:PUB_HOSTED_URL = "https://pub.flutter-io.cn"
$env:http_proxy = ""
$env:https_proxy = ""
$env:PATH = "C:\flutter\bin;" + $env:PATH

$log = "D:\code\flutter-web-build.log"
"START $(Get-Date)" | Out-File $log -Encoding utf8

Set-Location D:\code\mobile-app
& flutter create . --platforms web 2>&1 | Out-File $log -Append -Encoding utf8
"CREATE DONE $(Get-Date)" | Out-File $log -Append -Encoding utf8

& flutter build web --release --base-href "/mobile-preview/" 2>&1 | Out-File $log -Append -Encoding utf8
"BUILD DONE $(Get-Date)" | Out-File $log -Append -Encoding utf8

if (Test-Path D:\code\mobile-app\build\web\index.html) {
    "Web build OK" | Out-File $log -Append -Encoding utf8
    $dest = "D:\code\web-code\public\mobile-preview"
    if (Test-Path $dest) { Remove-Item $dest -Recurse -Force }
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    Copy-Item "D:\code\mobile-app\build\web\*" $dest -Recurse -Force
    "Copied to $dest" | Out-File $log -Append -Encoding utf8
} else {
    "Web build FAILED" | Out-File $log -Append -Encoding utf8
}
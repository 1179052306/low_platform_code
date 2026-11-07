$env:FLUTTER_STORAGE_BASE_URL = "https://storage.flutter-io.cn"
$env:PUB_HOSTED_URL = "https://pub.flutter-io.cn"
$env:http_proxy = ""
$env:https_proxy = ""
$env:PATH = "C:\flutter\bin;" + $env:PATH

$log = "D:\code\flutter-build2.log"
"START $(Get-Date)" | Out-File $log -Encoding utf8

Set-Location D:\code\mobile-app
& flutter build apk --release 2>&1 | Out-File $log -Append -Encoding utf8

"BUILD DONE $(Get-Date)" | Out-File $log -Append -Encoding utf8

$apk = "D:\code\mobile-app\build\app\outputs\flutter-apk\app-release.apk"
if (Test-Path $apk) {
    $f = Get-Item $apk
    "APK SIZE: " + [math]::Round($f.Length/1MB, 1) + "MB" | Out-File $log -Append -Encoding utf8
}
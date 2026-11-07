$env:FLUTTER_STORAGE_BASE_URL = "https://storage.flutter-io.cn"
$env:PUB_HOSTED_URL = "https://pub.flutter-io.cn"
$env:http_proxy = ""
$env:https_proxy = ""
$env:PATH = "C:\flutter\bin;" + $env:PATH

$log = "D:\code\flutter-init.log"
"START $(Get-Date)" | Out-File $log -Encoding utf8

& flutter --version 2>&1 | Out-File $log -Append -Encoding utf8
"VERSION DONE $(Get-Date)" | Out-File $log -Append -Encoding utf8
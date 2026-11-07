$env:FLUTTER_STORAGE_BASE_URL = "https://storage.flutter-io.cn"
$env:PUB_HOSTED_URL = "https://pub.flutter-io.cn"
$env:http_proxy = ""
$env:https_proxy = ""
$env:PATH = "C:\flutter\bin;" + $env:PATH

$log = "D:\code\flutter-build.log"
"START $(Get-Date)" | Out-File $log -Encoding utf8

# 1. 在临时目录生成骨架
"=== flutter create ===" | Out-File $log -Append -Encoding utf8
& flutter create --org com.lowcode --project-name mobile_app D:\code\mobile-app-tmp 2>&1 | Out-File $log -Append -Encoding utf8

# 2. 拷贝 android 目录到正式工程
"=== copy android ===" | Out-File $log -Append -Encoding utf8
if (Test-Path D:\code\mobile-app\android) {
    Remove-Item D:\code\mobile-app\android -Recurse -Force
}
Copy-Item D:\code\mobile-app-tmp\android D:\code\mobile-app\android -Recurse -Force

# 3. 拷贝其他必要文件
Copy-Item D:\code\mobile-app-tmp\analysis_options.yaml D:\code\mobile-app\analysis_options.yaml -Force -ErrorAction SilentlyContinue
Copy-Item D:\code\mobile-app-tmp\.gitignore D:\code\mobile-app\.gitignore -Force -ErrorAction SilentlyContinue
if (-not (Test-Path D:\code\mobile-app\test)) {
    Copy-Item D:\code\mobile-app-tmp\test D:\code\mobile-app\test -Recurse -Force -ErrorAction SilentlyContinue
}

# 4. 修改 AndroidManifest：加网络权限 + 明文 HTTP
"=== patch manifest ===" | Out-File $log -Append -Encoding utf8
$manifestPath = "D:\code\mobile-app\android\app\src\main\AndroidManifest.xml"
$manifest = Get-Content $manifestPath -Raw -Encoding utf8
if ($manifest -notmatch "INTERNET") {
    $manifest = $manifest -replace '<manifest', '<manifest'
    $manifest = $manifest -replace '<application', '    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <application'
}
if ($manifest -notmatch 'usesCleartextTraffic') {
    $manifest = $manifest -replace '<application', '<application android:usesCleartextTraffic="true"'
}
$manifest | Out-File $manifestPath -Encoding utf8 -NoNewline

# 5. pub get
"=== flutter pub get ===" | Out-File $log -Append -Encoding utf8
Set-Location D:\code\mobile-app
& flutter pub get 2>&1 | Out-File $log -Append -Encoding utf8

# 6. build apk
"=== flutter build apk ===" | Out-File $log -Append -Encoding utf8
& flutter build apk --release 2>&1 | Out-File $log -Append -Encoding utf8

"BUILD DONE $(Get-Date)" | Out-File $log -Append -Encoding utf8
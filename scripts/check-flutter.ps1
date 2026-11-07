if (Test-Path C:\flutter-download.log) {
    Get-Content C:\flutter-download.log
} else {
    "no log yet"
}
if (Test-Path C:\flutter_sdk.zip) {
    $f = Get-Item C:\flutter_sdk.zip
    "zip size: " + [math]::Round($f.Length/1MB, 1) + "MB"
} else {
    "no zip file"
}
if (Test-Path C:\flutter\bin\flutter.bat) {
    "FLUTTER SDK READY"
} else {
    "flutter not extracted yet"
}
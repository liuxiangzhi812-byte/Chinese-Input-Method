$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$DEVICE = "7fbf2094"
$PKG = "com.mercury.chinesepinyinime"
$OUT = "D:\Study\InputMethod\tests\v0.01.0029_2026-07-10_162115\screenshots\04_case_c_learn"
function Tap($x,$y,$l){ Write-Host "tap $l"; & $ADB -s $DEVICE shell input tap $x $y; Start-Sleep -Milliseconds 600 }
function Shot($n){ Write-Host "shot $n"; & $ADB -s $DEVICE shell screencap -p /sdcard/$n; & $ADB -s $DEVICE pull /sdcard/$n "$OUT\$n" | Out-Null }
& $ADB -s $DEVICE shell input keyevent 3
Start-Sleep -Milliseconds 300
& $ADB -s $DEVICE shell am force-stop $PKG
Start-Sleep -Milliseconds 700
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity"
Start-Sleep -Seconds 2
Tap 720 1293 "field"
1..8 | ForEach-Object { & $ADB -s $DEVICE shell input keyevent 67 | Out-Null }
Tap 1292 2556 "chong"
Tap 404 2748 "7"; Tap 700 2364 "2"; Tap 996 2556 "6"
Start-Sleep -Milliseconds 700
Shot "cf_only726.png"
Tap 128 2548 "slot2"; Start-Sleep -Milliseconds 400; Shot "cf_only726_slot2.png"
Tap 128 2732 "slot3"; Start-Sleep -Milliseconds 400; Shot "cf_only726_slot3.png"
Write-Host DONE

$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$DEVICE = "7fbf2094"
$PKG = "com.mercury.chinesepinyinime"
$OUT = "D:\Study\InputMethod\tests\v0.01.0029_2026-07-10_162115\screenshots\04_case_c_learn"
New-Item -ItemType Directory -Force -Path $OUT | Out-Null

function Tap($x,$y,$l) {
  Write-Host "$(Get-Date -Format HH:mm:ss) tap $l ($x,$y)"
  & $ADB -s $DEVICE shell input tap $x $y
  Start-Sleep -Milliseconds 650
}
function Shot($n) {
  Write-Host "$(Get-Date -Format HH:mm:ss) shot $n"
  & $ADB -s $DEVICE shell screencap -p /sdcard/$n
  & $ADB -s $DEVICE pull /sdcard/$n (Join-Path $OUT $n) | Out-Null
}

Write-Host "start clean case C"
& $ADB -s $DEVICE shell input keyevent 3
Start-Sleep -Milliseconds 400
& $ADB -s $DEVICE shell am force-stop $PKG
Start-Sleep -Milliseconds 800
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity"
Start-Sleep -Seconds 2

Tap 720 1293 "field"
1..12 | ForEach-Object { & $ADB -s $DEVICE shell input keyevent 67 | Out-Null }
Tap 1292 2556 "chongshu"

# 336726
Tap 996 2364 "3"
Tap 996 2364 "3"
Tap 996 2556 "6"
Tap 404 2748 "7"
Tap 700 2364 "2"
Tap 996 2556 "6"
Start-Sleep -Milliseconds 700
Shot "cf01_336726.png"

# first pinyin item ~ aligned with digit row1
Tap 128 2364 "fen"
Start-Sleep -Milliseconds 700
Shot "cf02_after_fen.png"

# if still whole-word, try slightly higher/lower once
Tap 128 2300 "fen2"
Start-Sleep -Milliseconds 500
Shot "cf03_fen2.png"

Tap 200 2140 "cand1"
Start-Sleep -Milliseconds 800
Shot "cf04_after_syll1.png"

# remaining pinyin choices for 726
Tap 128 2364 "rem1"
Start-Sleep -Milliseconds 500
Shot "cf05_rem1.png"
Tap 128 2548 "rem2"
Start-Sleep -Milliseconds 500
Shot "cf06_rem2.png"
Tap 128 2732 "rem3"
Start-Sleep -Milliseconds 500
Shot "cf07_rem3.png"

Tap 200 2140 "cand2"
Start-Sleep -Milliseconds 900
Shot "cf08_final.png"

# recall
Start-Sleep -Seconds 2
Tap 1292 2556 "chongshu"
1..10 | ForEach-Object { & $ADB -s $DEVICE shell input keyevent 67 | Out-Null }
Tap 996 2364 "3"; Tap 996 2364 "3"; Tap 996 2556 "6"; Tap 404 2748 "7"; Tap 700 2364 "2"; Tap 996 2556 "6"
Start-Sleep -Milliseconds 900
Shot "cf09_recall.png"
Tap 200 2140 "recall_cand"
Start-Sleep -Milliseconds 800
Shot "cf10_recall_commit.png"

Write-Host "DONE"

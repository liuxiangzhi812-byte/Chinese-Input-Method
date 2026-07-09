# Retest: 26-key smoke + corrected T9 94664, using in-app field
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0025_2026-07-09_133826"
$DEVICE = "7fbf2094"
$LOG = Join-Path $TEST_DIR "artifacts\retest_log.txt"
"" | Set-Content $LOG

# T9: rows top->bottom R1=1-3-DEL, R2=4-6-重输, R3=7-9-0
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$PINYIN_X = 128
$NI_Y = 2440; $MI_Y = 2520
$SPACE_X = 665; $SPACE_Y = 2940
$BOTTOM_Y = 2940; $KEY_123_X = 131

# 26-key letter coords (OnePlus 7 Pro prior calibration)
$N_X = 1005; $N_Y = 2790
$I_X = 1065; $I_Y = 2390
$DEL_X = 1310; $DEL_Y = 2790
$CAND_X = 180; $CAND_Y = 2140

function Log($m) { $l = "$(Get-Date -Format 'HH:mm:ss') $m"; Add-Content $LOG $l; Write-Host $l }
function Tap($x,$y,$label) { Log "tap $label ($x,$y)"; & $ADB -s $DEVICE shell input tap $x $y | Out-Null; Start-Sleep -Milliseconds 650 }
function Shot($name,$subdir) {
  $d = Join-Path $TEST_DIR "screenshots\$subdir"
  New-Item -ItemType Directory -Force -Path $d | Out-Null
  & $ADB -s $DEVICE shell screencap -p /sdcard/$name | Out-Null
  & $ADB -s $DEVICE pull /sdcard/$name (Join-Path $d $name) 2>&1 | Out-Null
  Log "shot $subdir/$name"
}
function Dump($name) {
  $p = Join-Path $TEST_DIR "ui_dumps\$name"
  & $ADB -s $DEVICE shell uiautomator dump /sdcard/$name | Out-Null
  & $ADB -s $DEVICE pull /sdcard/$name $p 2>&1 | Out-Null
  return (Get-Content $p -Raw -ErrorAction SilentlyContinue)
}
function HideKbd() {
  & $ADB -s $DEVICE shell input keyevent 4 | Out-Null  # BACK hide IME
  Start-Sleep -Milliseconds 500
}
function OpenAppNoKbd() {
  & $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
  Start-Sleep -Milliseconds 500
  & $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
  Start-Sleep -Seconds 2
  HideKbd
  Start-Sleep -Milliseconds 400
}
function FocusField() {
  $xml = Dump "retest_focus.xml"
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = [int](([int]$Matches[1]+[int]$Matches[3])/2)
    $y = [int](([int]$Matches[2]+[int]$Matches[4])/2)
    Tap $x $y "ime_test_input"
  } else { Tap 720 900 "ime_test_input fallback" }
  Start-Sleep -Milliseconds 900
}
function SetLayout26() {
  OpenAppNoKbd
  # scroll so layout button is mid-screen
  & $ADB -s $DEVICE shell input swipe 720 1800 720 900 350 | Out-Null
  Start-Sleep -Milliseconds 500
  $xml = Dump "retest_before_26.xml"
  Log "status snippet: $(($xml | Select-String -Pattern 'keyboard_layout_status|9 键|26 键' -AllMatches).Matches.Value -join ',')"
  if ($xml -match '当前布局：9 键|keyboard_layout_status_9|9 键（实验性）') {
    if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/keyboard_layout_toggle_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
      $x = [int](([int]$Matches[1]+[int]$Matches[3])/2)
      $y = [int](([int]$Matches[2]+[int]$Matches[4])/2)
      Tap $x $y "toggle -> 26"
    } else {
      # text search for button
      if ($xml -match 'text="切换为 26 键输入"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        Tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2)) "toggle text 26"
      } else { Tap 720 1400 "toggle 26 fallback" }
    }
    Start-Sleep -Milliseconds 600
  } else { Log "already 26 or unknown" }
  $xml2 = Dump "retest_after_toggle_26.xml"
  if ($xml2 -match '26 键') { Log "PASS UI shows 26-key status" } else { Log "WARN layout status after toggle" }
  Shot "r01_layout_26_status.png" "03_26key_smoke"
  # restart IME process
  & $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
  Start-Sleep -Milliseconds 700
}

function SetLayout9() {
  OpenAppNoKbd
  & $ADB -s $DEVICE shell input swipe 720 1800 720 900 350 | Out-Null
  Start-Sleep -Milliseconds 500
  $xml = Dump "retest_before_9.xml"
  if ($xml -match '当前布局：26 键|26 键（QWERTY）|切换为 9 键') {
    if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/keyboard_layout_toggle_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
      Tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2)) "toggle -> 9"
    } elseif ($xml -match 'text="切换为 9 键输入"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
      Tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2)) "toggle text 9"
    } else { Tap 720 1400 "toggle 9 fallback" }
    Start-Sleep -Milliseconds 600
  } else { Log "already 9 or unknown" }
  Shot "r02_layout_9_status.png" "05_layout_toggle"
  & $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
  Start-Sleep -Milliseconds 700
}

Log "=== RETEST 26-key path ==="
SetLayout26
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 2
FocusField
Shot "r03_26key_keyboard.png" "03_26key_smoke"
# type n, i
Tap $N_X $N_Y "n"
Tap $I_X $I_Y "i"
Start-Sleep -Milliseconds 700
Shot "r04_ni_composing.png" "03_26key_smoke"
# space commit
Tap $SPACE_X $SPACE_Y "space"
Start-Sleep -Milliseconds 700
Shot "r05_ni_committed.png" "03_26key_smoke"
# type ni again + candidate tap
Tap $N_X $N_Y "n"
Tap $I_X $I_Y "i"
Start-Sleep -Milliseconds 500
Shot "r06_ni_before_cand.png" "03_26key_smoke"
Tap $CAND_X $CAND_Y "cand1"
Start-Sleep -Milliseconds 500
Shot "r07_ni_cand_tap.png" "03_26key_smoke"
# DEL short
Tap $N_X $N_Y "n"; Tap $I_X $I_Y "i"; Start-Sleep -Milliseconds 400
Tap $DEL_X $DEL_Y "DEL"
Start-Sleep -Milliseconds 500
Shot "r08_del_short.png" "03_26key_smoke"
& $ADB -s $DEVICE shell input swipe $DEL_X $DEL_Y $DEL_X $DEL_Y 1200
Start-Sleep -Milliseconds 800
Shot "r09_del_long.png" "03_26key_smoke"
# symbols
Tap $KEY_123_X $BOTTOM_Y "123"
Start-Sleep -Milliseconds 600
Shot "r10_symbol.png" "03_26key_smoke"
Tap $KEY_123_X $BOTTOM_Y "ABC"
Start-Sleep -Milliseconds 600
Shot "r11_abc_back.png" "03_26key_smoke"

Log "=== RETEST 9-key 94664 with fixed coords ==="
SetLayout9
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 2
FocusField
Shot "r12_t9_keyboard.png" "04_t9_smoke"
# clear then 9 4 6 6 4 — 9 is R3 not R1
Tap $C3 $R2Y "重输 clear"
Tap $C2 $R3Y "9"
Tap $C1 $R2Y "4"
Tap $C2 $R2Y "6"
Tap $C2 $R2Y "6"
Tap $C0 $R2Y "4"
Start-Sleep -Milliseconds 700
Shot "r13_94664_zhong.png" "04_t9_smoke"
# 64 again for vertical list confirm after retest
Tap $C3 $R2Y "重输"
Tap $C2 $R2Y "6"; Tap $C0 $R2Y "4"
Start-Sleep -Milliseconds 600
Shot "r14_64_list.png" "04_t9_smoke"
Tap $PINYIN_X $MI_Y "mi"
Start-Sleep -Milliseconds 500
Shot "r15_mi.png" "04_t9_smoke"
# empty 0 space
Tap $C3 $R2Y "重输"
Tap $C3 $R3Y "0"
Start-Sleep -Milliseconds 500
Shot "r16_zero_space.png" "04_t9_smoke"
# symbol round trip
Tap $C0 $R1Y "1 symbol"
Start-Sleep -Milliseconds 500
Shot "r17_symbol.png" "04_t9_smoke"
Tap $KEY_123_X $BOTTOM_Y "9键"
Start-Sleep -Milliseconds 500
Shot "r18_back_t9.png" "04_t9_smoke"

Log "=== RETEST DONE ==="

# v0.01.0029 — whole-word candidates + syllable fallback (Cases A/B/C)
# Device already has latest APK; this script does NOT install.
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0029_2026-07-10_162115"
$DEVICE = "7fbf2094"
$LOG = Join-Path $TEST_DIR "artifacts\adb_commands.txt"
"" | Set-Content $LOG

# T9 grid — OnePlus 7 Pro 1440x3120 (left pinyin col 64dp ~256px shift)
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$PINYIN_X = 128
$PINYIN1_Y = 2440
$PINYIN2_Y = 2520
$PINYIN3_Y = 2600
$SPACE_X = 665; $SPACE_Y = 2940
$CAND_Y = 2140
$CAND1_X = 200
$CAND2_X = 380
$EXPAND_X = 1320
$PAGE_NEXT_X = 1240
$DEL_X = $C3; $DEL_Y = $R1Y
$CHONGSHU_X = $C3; $CHONGSHU_Y = $R2Y

function Log($m) {
  $l = "$(Get-Date -Format 'HH:mm:ss') $m"
  Add-Content $LOG $l -ErrorAction SilentlyContinue
  Write-Host $l
}
function Tap($x, $y, $label) {
  Log "tap $label ($x,$y)"
  & $ADB -s $DEVICE shell input tap $x $y | Out-Null
  Start-Sleep -Milliseconds 750
}
function Shot($name, $sub) {
  $d = Join-Path $TEST_DIR "screenshots\$sub"
  New-Item -ItemType Directory -Force -Path $d | Out-Null
  & $ADB -s $DEVICE shell screencap -p /sdcard/$name | Out-Null
  & $ADB -s $DEVICE pull /sdcard/$name (Join-Path $d $name) 2>&1 | Out-Null
  Log "shot $sub/$name"
}
function Dump($name) {
  $p = Join-Path $TEST_DIR "ui_dumps\$name"
  & $ADB -s $DEVICE shell uiautomator dump /sdcard/$name | Out-Null
  & $ADB -s $DEVICE pull /sdcard/$name $p 2>&1 | Out-Null
  Log "dump $name"
  return (Get-Content $p -Raw -ErrorAction SilentlyContinue)
}
function FieldText() {
  $xml = Dump ("field_" + [guid]::NewGuid().ToString("N").Substring(0, 8) + ".xml")
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*text="([^"]*)"') {
    return $Matches[1]
  }
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*text=""') {
    return ""
  }
  # text attr may be missing when empty
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"') {
    return "(present_no_text_attr)"
  }
  return "(no_field)"
}
function HideKbd() {
  & $ADB -s $DEVICE shell input keyevent 4 | Out-Null
  Start-Sleep -Milliseconds 500
}
function OpenApp() {
  & $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
  Start-Sleep -Seconds 2
}
function RestartApp() {
  & $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
  Start-Sleep -Milliseconds 600
  OpenApp
}
function FocusField() {
  $xml = Dump "focus_field.xml"
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Tap $x $y "ime_test_input"
  } else {
    Tap 720 900 "ime_test_input fb"
  }
  Start-Sleep -Milliseconds 900
}
function ClearField() {
  # Select-all + delete via keyevents after focus
  & $ADB -s $DEVICE shell input keyevent 123 | Out-Null  # move end
  for ($i = 0; $i -lt 30; $i++) {
    & $ADB -s $DEVICE shell input keyevent 67 | Out-Null  # DEL
  }
  Start-Sleep -Milliseconds 300
}
function TapDigit($d) {
  switch ($d) {
    "1" { Tap $C0 $R1Y "1" }
    "2" { Tap $C1 $R1Y "2" }
    "3" { Tap $C2 $R1Y "3" }
    "4" { Tap $C0 $R2Y "4" }
    "5" { Tap $C1 $R2Y "5" }
    "6" { Tap $C2 $R2Y "6" }
    "7" { Tap $C0 $R3Y "7" }
    "8" { Tap $C1 $R3Y "8" }
    "9" { Tap $C2 $R3Y "9" }
    "0" { Tap $C3 $R3Y "0" }
  }
}
function TypeDigits($s) {
  foreach ($ch in $s.ToCharArray()) {
    TapDigit ([string]$ch)
  }
}
function ChongShu() { Tap $CHONGSHU_X $CHONGSHU_Y "重输" }
function EnsureIme() {
  $cur = & $ADB -s $DEVICE shell settings get secure default_input_method
  Log "ime=$cur"
  if ($cur -notmatch "chinesepinyinime") {
    & $ADB -s $DEVICE shell ime enable "$PKG/.ChinesePinyinInputMethodService" 2>&1 | Out-Null
    & $ADB -s $DEVICE shell ime set "$PKG/.ChinesePinyinInputMethodService" 2>&1 | Out-Null
  }
}
function EnsureT9() {
  OpenApp
  HideKbd
  & $ADB -s $DEVICE shell input swipe 720 2200 720 800 350 | Out-Null
  Start-Sleep -Milliseconds 600
  $xml = Dump "settings_layout.xml"
  Shot "s01_settings_scroll.png" "01_settings"
  if ($xml -match '当前布局：26' -or ($xml -match '26 键' -and $xml -notmatch '当前布局：9')) {
    if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/keyboard_layout_toggle_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
      $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
      $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
      Tap $x $y "toggle to 9-key"
      Start-Sleep -Milliseconds 500
    }
  } else {
    Log "layout already 9-key or status text not matched"
  }
  Shot "s02_layout_status.png" "01_settings"
  # capture version line
  if ($xml -match 'v0\.01\.\d+') { Log "version_in_ui=$($Matches[0])" }
}
function ClearLearnedIfPresent() {
  OpenApp
  HideKbd
  & $ADB -s $DEVICE shell input swipe 720 2200 720 600 400 | Out-Null
  Start-Sleep -Milliseconds 600
  $xml = Dump "settings_learned.xml"
  Shot "s03_before_clear_learned.png" "01_settings"
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/clear_learned_data_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Tap $x $y "clear learned data"
    Start-Sleep -Milliseconds 800
    # confirm dialog if any: try positive button center-ish
    $xml2 = Dump "clear_dialog.xml"
    if ($xml2 -match 'text="确定"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' -or
        $xml2 -match 'text="清除"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' -or
        $xml2 -match 'text="OK"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
      $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
      $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
      Tap $x $y "confirm clear"
    }
    Start-Sleep -Milliseconds 600
    Shot "s04_after_clear_learned.png" "01_settings"
  } else {
    Log "clear learned button not found in dump"
  }
}
function ReadyField() {
  OpenApp
  FocusField
  ClearField
  ChongShu
  Start-Sleep -Milliseconds 400
}

# ========== START ==========
Log "=== v0.01.0029 device test start (no install) ==="
EnsureIme
EnsureT9

# ---------- Case A: 64426 whole-word 你好 ----------
Log "=== CASE A: whole-word 64426 ==="
ReadyField
Shot "a00_ready.png" "02_case_a_whole_word"
TypeDigits "64426"
Start-Sleep -Milliseconds 900
Shot "a01_after_64426.png" "02_case_a_whole_word"
Dump "a01_after_64426.xml" | Out-Null

# Backspace regression while composing
Tap $DEL_X $DEL_Y "DEL once"
Start-Sleep -Milliseconds 500
Shot "a02_after_del.png" "02_case_a_whole_word"
# retype full
ChongShu
TypeDigits "64426"
Start-Sleep -Milliseconds 900
Shot "a03_retape_64426.png" "02_case_a_whole_word"

# Tap first candidate (expect 你好)
Tap $CAND1_X $CAND_Y "cand1 whole-word"
Start-Sleep -Milliseconds 900
Shot "a04_after_commit.png" "02_case_a_whole_word"
$tA = FieldText
Log "CASE_A_field_text=$tA"
Dump "a04_final.xml" | Out-Null

# ---------- Case B: 64426 -> tap ni -> syllable path ----------
Log "=== CASE B: syllable path via ni ==="
ReadyField
TypeDigits "64426"
Start-Sleep -Milliseconds 800
Shot "b01_after_64426.png" "03_case_b_syllable"
# tap left pinyin first item (ni)
Tap $PINYIN_X $PINYIN1_Y "pinyin1 ni"
Start-Sleep -Milliseconds 700
Shot "b02_after_tap_ni.png" "03_case_b_syllable"
# select 你
Tap $CAND1_X $CAND_Y "cand 你"
Start-Sleep -Milliseconds 800
Shot "b03_after_ni_select.png" "03_case_b_syllable"
# remaining should be 426 -> hao; tap hao if visible in list else default
Tap $PINYIN_X $PINYIN1_Y "pinyin remaining hao?"
Start-Sleep -Milliseconds 600
Shot "b04_remaining_pinyin.png" "03_case_b_syllable"
Tap $CAND1_X $CAND_Y "cand 好"
Start-Sleep -Milliseconds 900
Shot "b05_after_hao.png" "03_case_b_syllable"
$tB = FieldText
Log "CASE_B_field_text=$tB"
Dump "b05_final.xml" | Out-Null

# ---------- Case C: fenpan learn + recall ----------
Log "=== CASE C: clear learned + fenpan learn/recall ==="
ClearLearnedIfPresent
# restart IME process so learned store reloads empty
RestartApp
Start-Sleep -Seconds 1
ReadyField
Shot "c00_ready_cleared.png" "04_case_c_learn"
TypeDigits "336726"
Start-Sleep -Milliseconds 900
Shot "c01_after_336726.png" "04_case_c_learn"
Dump "c01_after_336726.xml" | Out-Null

# select fen from left list if needed (try first then second)
Tap $PINYIN_X $PINYIN1_Y "pinyin fen?"
Start-Sleep -Milliseconds 600
Shot "c02_after_pinyin_fen.png" "04_case_c_learn"
Tap $CAND1_X $CAND_Y "cand 分"
Start-Sleep -Milliseconds 800
Shot "c03_after_fen.png" "04_case_c_learn"
Dump "c03_after_fen.xml" | Out-Null

# remaining pan — may need to pick pan not san
Tap $PINYIN_X $PINYIN1_Y "pinyin1 remaining"
Start-Sleep -Milliseconds 500
Shot "c04_remaining1.png" "04_case_c_learn"
# if first is wrong (san), try second/third
# also try scrolling pinyin list
& $ADB -s $DEVICE shell input swipe $PINYIN_X 2680 $PINYIN_X 2400 300 | Out-Null
Start-Sleep -Milliseconds 400
Shot "c05_pinyin_scrolled.png" "04_case_c_learn"
# Try tap second and third pinyin rows looking for pan
Tap $PINYIN_X $PINYIN2_Y "pinyin2"
Start-Sleep -Milliseconds 500
Shot "c06_pinyin2.png" "04_case_c_learn"
Tap $PINYIN_X $PINYIN3_Y "pinyin3"
Start-Sleep -Milliseconds 500
Shot "c07_pinyin3.png" "04_case_c_learn"
# Prefer pan: try pinyin1 again after scroll, then pick 盘 (usually top for pan)
Tap $PINYIN_X $PINYIN1_Y "pinyin1 again"
Start-Sleep -Milliseconds 500
Tap $CAND1_X $CAND_Y "cand 盘/三?"
Start-Sleep -Milliseconds 1000
Shot "c08_after_second_syllable.png" "04_case_c_learn"
$tC1 = FieldText
Log "CASE_C_after_compose_field=$tC1"
Dump "c08_after_compose.xml" | Out-Null

# Wait for learning
Log "wait for learning..."
Start-Sleep -Seconds 3
# Recall: type 336726 again without tapping pinyin
ChongShu
ClearField
FocusField
ChongShu
TypeDigits "336726"
Start-Sleep -Milliseconds 1000
Shot "c09_recall_336726.png" "04_case_c_learn"
Dump "c09_recall.xml" | Out-Null
# tap first candidate (expect learned whole word if previous commit succeeded)
Tap $CAND1_X $CAND_Y "cand recall whole-word"
Start-Sleep -Milliseconds 900
Shot "c10_after_recall_commit.png" "04_case_c_learn"
$tC2 = FieldText
Log "CASE_C_recall_field=$tC2"
Dump "c10_final.xml" | Out-Null

# learned status
HideKbd
& $ADB -s $DEVICE shell input swipe 720 2200 720 600 400 | Out-Null
Start-Sleep -Milliseconds 500
Shot "c11_learned_status.png" "01_settings"
Dump "learned_status.xml" | Out-Null

# ---------- light regression: 64 ni/mi ----------
Log "=== REGRESSION: 64 -> ni/mi smoke ==="
ReadyField
TypeDigits "64"
Start-Sleep -Milliseconds 700
Shot "r01_64.png" "05_regression"
Tap $PINYIN_X $PINYIN2_Y "mi"
Start-Sleep -Milliseconds 600
Shot "r02_mi.png" "05_regression"
ChongShu
TypeDigits "94664"
Start-Sleep -Milliseconds 800
Shot "r03_94664_zhong.png" "05_regression"
Tap $CAND1_X $CAND_Y "cand 中"
Start-Sleep -Milliseconds 700
Shot "r04_zhong_commit.png" "05_regression"
$tR = FieldText
Log "REGRESSION_field=$tR"

Log "=== DONE ==="
Log "SUMMARY A=$tA B=$tB C1=$tC1 C2=$tC2 R=$tR"
Write-Host "DONE"

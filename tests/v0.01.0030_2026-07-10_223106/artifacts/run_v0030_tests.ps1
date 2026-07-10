# v0.01.0030 — dictionary-aligned leading syllables + simplified candidate bar
# Device already has latest APK; this script does NOT install/compile.
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0030_2026-07-10_223106"
$DEVICE = "7fbf2094"
$LOG = Join-Path $TEST_DIR "artifacts\adb_commands.txt"
"" | Set-Content $LOG

# T9 grid — OnePlus 7 Pro 1440x3120 (left pinyin col 64dp ~256px shift)
# Calibrated from v0.01.0029: pinyin first row ~2300–2364 more reliable than 2440
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$PINYIN_X = 128
$PINYIN1_Y = 2330
$PINYIN2_Y = 2450
$PINYIN3_Y = 2570
$SPACE_X = 665; $SPACE_Y = 2940
$CAND_Y = 2140
$CAND1_X = 200
$CAND2_X = 380
$CAND3_X = 560
$EXPAND_X = 1360
$EXPAND_Y = 2140
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
  & $ADB -s $DEVICE shell input keyevent 123 | Out-Null
  for ($i = 0; $i -lt 40; $i++) {
    & $ADB -s $DEVICE shell input keyevent 67 | Out-Null
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
  if ($xml -match 'v0\.01\.\d+') { Log "version_in_ui=$($Matches[0])" }
}
function ReadyField() {
  OpenApp
  FocusField
  ClearField
  ChongShu
  Start-Sleep -Milliseconds 400
}

# ========== START ==========
Log "=== v0.01.0030 device test start (no install) ==="
EnsureIme
EnsureT9

# ---------- Case Q: 744824 qiguai — qi first, whole-word 奇怪 ----------
Log "=== CASE Q: 744824 dictionary-aligned qi + 奇怪 ==="
ReadyField
Shot "q00_ready.png" "02_case_qiguai"
TypeDigits "744824"
Start-Sleep -Milliseconds 1000
Shot "q01_after_744824.png" "02_case_qiguai"
Dump "q01_after_744824.xml" | Out-Null

# Probe pinyin first item (expect qi highlighted first)
Tap $PINYIN_X $PINYIN1_Y "pinyin1 expect qi"
Start-Sleep -Milliseconds 700
Shot "q02_after_tap_pinyin1.png" "02_case_qiguai"

# If we entered syllable mode, select first candidate (奇)
Tap $CAND1_X $CAND_Y "cand1 奇?"
Start-Sleep -Milliseconds 900
Shot "q03_after_cand1.png" "02_case_qiguai"
Dump "q03_after_cand1.xml" | Out-Null

# remaining should be 4824 -> guai; tap first pinyin if needed
Tap $PINYIN_X $PINYIN1_Y "pinyin remaining guai?"
Start-Sleep -Milliseconds 600
Shot "q04_remaining_pinyin.png" "02_case_qiguai"
Tap $CAND1_X $CAND_Y "cand 怪?"
Start-Sleep -Milliseconds 1000
Shot "q05_after_second_syllable.png" "02_case_qiguai"
$tQ1 = FieldText
Log "CASE_Q_syllable_field=$tQ1"
Dump "q05_syllable_final.xml" | Out-Null

# Whole-word path: retype 744824 and commit first candidate without tapping pinyin
Log "=== CASE Q2: whole-word commit 奇怪 ==="
ReadyField
TypeDigits "744824"
Start-Sleep -Milliseconds 1000
Shot "q06_whole_before_commit.png" "02_case_qiguai"
Tap $CAND1_X $CAND_Y "cand1 whole 奇怪?"
Start-Sleep -Milliseconds 900
Shot "q07_whole_after_commit.png" "02_case_qiguai"
$tQ2 = FieldText
Log "CASE_Q_whole_field=$tQ2"
Dump "q07_whole_final.xml" | Out-Null

# ---------- Case S: 744 alone — shi remains available ----------
Log "=== CASE S: 744 shi regression ==="
ReadyField
TypeDigits "744"
Start-Sleep -Milliseconds 900
Shot "s01_after_744.png" "03_case_shi_regression"
Dump "s01_after_744.xml" | Out-Null
# Try second pinyin if first is not shi
Tap $PINYIN_X $PINYIN1_Y "pinyin1 for 744"
Start-Sleep -Milliseconds 600
Shot "s02_pinyin1.png" "03_case_shi_regression"
ChongShu
TypeDigits "744"
Start-Sleep -Milliseconds 700
Tap $PINYIN_X $PINYIN2_Y "pinyin2 for 744"
Start-Sleep -Milliseconds 600
Shot "s03_pinyin2.png" "03_case_shi_regression"
Tap $CAND1_X $CAND_Y "cand after shi?"
Start-Sleep -Milliseconds 800
Shot "s04_after_cand.png" "03_case_shi_regression"
$tS = FieldText
Log "CASE_S_field=$tS"
Dump "s04_final.xml" | Out-Null

# ---------- Case E: expand panel; no page arrows ----------
Log "=== CASE E: expand panel + no page arrows ==="
ReadyField
TypeDigits "64"
Start-Sleep -Milliseconds 800
Shot "e01_compact_bar_64.png" "04_candidate_expand"
# expand
Tap $EXPAND_X $EXPAND_Y "expand toggle"
Start-Sleep -Milliseconds 800
Shot "e02_expanded_panel.png" "04_candidate_expand"
# tap a candidate in expanded panel (mid area of panel)
Tap 400 2500 "expanded cand mid"
Start-Sleep -Milliseconds 900
Shot "e03_after_expand_select.png" "04_candidate_expand"
$tE1 = FieldText
Log "CASE_E_expand_select=$tE1"

# reopen compose and test expand close via DEL path / re-open
ReadyField
TypeDigits "64426"
Start-Sleep -Milliseconds 900
Shot "e04_64426_compact.png" "04_candidate_expand"
Tap $EXPAND_X $EXPAND_Y "expand on whole-word"
Start-Sleep -Milliseconds 800
Shot "e05_64426_expanded.png" "04_candidate_expand"
# close expand by tapping expand toggle again
Tap $EXPAND_X $EXPAND_Y "collapse expand"
Start-Sleep -Milliseconds 700
Shot "e06_after_collapse.png" "04_candidate_expand"
# DEL while composing
Tap $DEL_X $DEL_Y "DEL once"
Start-Sleep -Milliseconds 500
Shot "e07_after_del.png" "04_candidate_expand"
ChongShu
TypeDigits "64426"
Start-Sleep -Milliseconds 800
Tap $CAND1_X $CAND_Y "cand 你好"
Start-Sleep -Milliseconds 900
Shot "e08_nihao_commit.png" "04_candidate_expand"
$tE2 = FieldText
Log "CASE_E_nihao=$tE2"
Dump "e08_final.xml" | Out-Null

# ---------- Case R: 336726 fen syllable + DEL smoke ----------
Log "=== CASE R: 336726 fen + regression smoke ==="
ReadyField
TypeDigits "336726"
Start-Sleep -Milliseconds 900
Shot "r01_336726.png" "05_regression"
# try pinyin first (fen)
Tap $PINYIN_X $PINYIN1_Y "pinyin fen?"
Start-Sleep -Milliseconds 700
Shot "r02_after_fen.png" "05_regression"
Tap $CAND1_X $CAND_Y "cand 分?"
Start-Sleep -Milliseconds 800
Shot "r03_after_fen_select.png" "05_regression"
$tR1 = FieldText
Log "CASE_R_after_fen=$tR1"
Dump "r03_after_fen.xml" | Out-Null
ChongShu
ClearField

# 94664 zhong smoke
TypeDigits "94664"
Start-Sleep -Milliseconds 800
Shot "r04_94664.png" "05_regression"
Tap $CAND1_X $CAND_Y "cand 中?"
Start-Sleep -Milliseconds 800
Shot "r05_zhong.png" "05_regression"
$tR2 = FieldText
Log "CASE_R_zhong=$tR2"

# settings version final
HideKbd
& $ADB -s $DEVICE shell input swipe 720 2200 720 600 400 | Out-Null
Start-Sleep -Milliseconds 500
Shot "s03_final_settings.png" "01_settings"
Dump "settings_final.xml" | Out-Null

Log "=== DONE ==="
Log "SUMMARY Q1=$tQ1 Q2=$tQ2 S=$tS E1=$tE1 E2=$tE2 R1=$tR1 R2=$tR2"
Write-Host "DONE"
Write-Host "SUMMARY Q1=$tQ1 Q2=$tQ2 S=$tS E1=$tE1 E2=$tE2 R1=$tR1 R2=$tR2"

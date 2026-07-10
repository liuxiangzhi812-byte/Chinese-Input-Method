# v0.01.0030 retest — fixed scroll-to-top + field focus (no install)
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0030_2026-07-10_223106"
$DEVICE = "7fbf2094"
$LOG = Join-Path $TEST_DIR "artifacts\retest_log.txt"
"" | Set-Content $LOG

# T9 grid — OnePlus 7 Pro 1440x3120
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$PINYIN_X = 128
$PINYIN1_Y = 2330
$PINYIN2_Y = 2450
$PINYIN3_Y = 2570
$CAND_Y = 2140
$CAND1_X = 200
$CAND2_X = 400
$EXPAND_X = 1360
$EXPAND_Y = 2140
$DEL_X = $C3; $DEL_Y = $R1Y
$CHONGSHU_X = $C3; $CHONGSHU_Y = $R2Y

function Log($m) {
  $l = "$(Get-Date -Format 'HH:mm:ss') $m"
  Add-Content $LOG $l
  Write-Host $l
}
function Tap($x, $y, $label) {
  Log "tap $label ($x,$y)"
  & $ADB -s $DEVICE shell input tap $x $y | Out-Null
  Start-Sleep -Milliseconds 650
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
  if ($xml -match 'text="([^"]*)"[^>]*resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"') {
    return $Matches[1]
  }
  if ($xml -match 'ime_test_input') { return "(present_no_text)" }
  return "(no_field)"
}
function HideKbd() {
  & $ADB -s $DEVICE shell input keyevent 4 | Out-Null
  Start-Sleep -Milliseconds 400
}
function ScrollTop() {
  for ($i = 0; $i -lt 3; $i++) {
    & $ADB -s $DEVICE shell input swipe 720 900 720 2500 250 | Out-Null
    Start-Sleep -Milliseconds 250
  }
}
function OpenAppTop() {
  # leave any system settings
  for ($i = 0; $i -lt 3; $i++) {
    & $ADB -s $DEVICE shell input keyevent 4 | Out-Null
    Start-Sleep -Milliseconds 250
  }
  & $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
  Start-Sleep -Seconds 1
  ScrollTop
  Start-Sleep -Milliseconds 400
}
function FocusField() {
  $xml = Dump "focus_field.xml"
  $x = 720; $y = 1000
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Log "field bounds center=($x,$y)"
  } elseif ($xml -match 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"') {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Log "field bounds(alt) center=($x,$y)"
  } else {
    Log "field bounds not found, use default ($x,$y)"
  }
  Tap $x $y "ime_test_input"
  Start-Sleep -Milliseconds 900
}
function ClearField() {
  # select all then delete via keyevents; also try long DEL
  & $ADB -s $DEVICE shell input keyevent 123 | Out-Null
  for ($i = 0; $i -lt 50; $i++) {
    & $ADB -s $DEVICE shell input keyevent 67 | Out-Null
  }
  Start-Sleep -Milliseconds 250
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
  foreach ($ch in $s.ToCharArray()) { TapDigit ([string]$ch) }
}
function ChongShu() { Tap $CHONGSHU_X $CHONGSHU_Y "重输" }
function ReadyField() {
  OpenAppTop
  FocusField
  # verify keyboard visible by screenshot name later
  ClearField
  ChongShu
  Start-Sleep -Milliseconds 350
}

Log "=== v0.01.0030 retest start ==="
$cur = & $ADB -s $DEVICE shell settings get secure default_input_method
Log "ime=$cur"
if ($cur -notmatch "chinesepinyinime") {
  & $ADB -s $DEVICE shell ime set "$PKG/.ChinesePinyinInputMethodService" | Out-Null
}

# Confirm version + 9-key without leaving test field area
OpenAppTop
HideKbd
ScrollTop
Shot "rt_s01_version_top.png" "01_settings"
$xml = Dump "settings_top.xml"
if ($xml -match 'v0\.01\.\d+') { Log "version=$($Matches[0])" }
if ($xml -match '当前布局：9') { Log "layout=9key OK" } else { Log "layout check: may need toggle" }

# ========== CASE Q: 744824 ==========
Log "=== CASE Q: 744824 qi/奇怪 ==="
ReadyField
Shot "rt_q00_ready.png" "02_case_qiguai"
TypeDigits "744824"
Start-Sleep -Milliseconds 1000
Shot "rt_q01_after_744824.png" "02_case_qiguai"
Dump "rt_q01.xml" | Out-Null

# pinyin first = expect qi
Tap $PINYIN_X $PINYIN1_Y "pinyin1"
Start-Sleep -Milliseconds 700
Shot "rt_q02_pinyin1.png" "02_case_qiguai"
Tap $CAND1_X $CAND_Y "cand1"
Start-Sleep -Milliseconds 900
Shot "rt_q03_after_cand1.png" "02_case_qiguai"
Tap $PINYIN_X $PINYIN1_Y "pinyin remaining"
Start-Sleep -Milliseconds 500
Shot "rt_q04_remaining.png" "02_case_qiguai"
Tap $CAND1_X $CAND_Y "cand2"
Start-Sleep -Milliseconds 900
Shot "rt_q05_syllable_done.png" "02_case_qiguai"
$tQ1 = FieldText
Log "Q_syllable_field=$tQ1"

# whole-word path
Log "=== CASE Q2 whole-word ==="
ReadyField
TypeDigits "744824"
Start-Sleep -Milliseconds 1000
Shot "rt_q06_whole.png" "02_case_qiguai"
Tap $CAND1_X $CAND_Y "whole cand1"
Start-Sleep -Milliseconds 900
Shot "rt_q07_whole_commit.png" "02_case_qiguai"
$tQ2 = FieldText
Log "Q_whole_field=$tQ2"

# ========== CASE S: 744 shi ==========
Log "=== CASE S: 744 shi ==="
ReadyField
TypeDigits "744"
Start-Sleep -Milliseconds 900
Shot "rt_s01_744.png" "03_case_shi_regression"
Dump "rt_s01_744.xml" | Out-Null
# probe pinyin slots
Tap $PINYIN_X $PINYIN1_Y "pinyin1"
Start-Sleep -Milliseconds 600
Shot "rt_s02_p1.png" "03_case_shi_regression"
ChongShu
TypeDigits "744"
Start-Sleep -Milliseconds 700
Tap $PINYIN_X $PINYIN2_Y "pinyin2"
Start-Sleep -Milliseconds 600
Shot "rt_s03_p2.png" "03_case_shi_regression"
Tap $CAND1_X $CAND_Y "cand"
Start-Sleep -Milliseconds 800
Shot "rt_s04_cand.png" "03_case_shi_regression"
$tS = FieldText
Log "S_field=$tS"

# ========== CASE E: expand / no page arrows ==========
Log "=== CASE E: expand panel ==="
ReadyField
TypeDigits "64"
Start-Sleep -Milliseconds 800
Shot "rt_e01_compact.png" "04_candidate_expand"
Tap $EXPAND_X $EXPAND_Y "expand"
Start-Sleep -Milliseconds 800
Shot "rt_e02_expanded.png" "04_candidate_expand"
# select candidate in expanded panel (row of candidates over keyboard area)
Tap 360 2450 "exp cand"
Start-Sleep -Milliseconds 900
Shot "rt_e03_selected.png" "04_candidate_expand"
$tE1 = FieldText
Log "E_expand_field=$tE1"

ReadyField
TypeDigits "64426"
Start-Sleep -Milliseconds 900
Shot "rt_e04_64426.png" "04_candidate_expand"
Tap $EXPAND_X $EXPAND_Y "expand whole"
Start-Sleep -Milliseconds 800
Shot "rt_e05_expanded_ww.png" "04_candidate_expand"
Tap $EXPAND_X $EXPAND_Y "collapse"
Start-Sleep -Milliseconds 700
Shot "rt_e06_collapsed.png" "04_candidate_expand"
Tap $DEL_X $DEL_Y "DEL"
Start-Sleep -Milliseconds 500
Shot "rt_e07_del.png" "04_candidate_expand"
ChongShu
TypeDigits "64426"
Start-Sleep -Milliseconds 800
Tap $CAND1_X $CAND_Y "你好"
Start-Sleep -Milliseconds 900
Shot "rt_e08_nihao.png" "04_candidate_expand"
$tE2 = FieldText
Log "E_nihao=$tE2"

# ========== CASE R: 336726 fen + 94664 ==========
Log "=== CASE R regression ==="
ReadyField
TypeDigits "336726"
Start-Sleep -Milliseconds 900
Shot "rt_r01_336726.png" "05_regression"
Tap $PINYIN_X $PINYIN1_Y "fen?"
Start-Sleep -Milliseconds 700
Shot "rt_r02_fen.png" "05_regression"
Tap $CAND1_X $CAND_Y "分?"
Start-Sleep -Milliseconds 800
Shot "rt_r03_fen_sel.png" "05_regression"
$tR1 = FieldText
Log "R_fen=$tR1"
ChongShu
ClearField
TypeDigits "94664"
Start-Sleep -Milliseconds 800
Shot "rt_r04_94664.png" "05_regression"
Tap $CAND1_X $CAND_Y "中?"
Start-Sleep -Milliseconds 800
Shot "rt_r05_zhong.png" "05_regression"
$tR2 = FieldText
Log "R_zhong=$tR2"

Log "=== DONE ==="
Log "SUMMARY Q1=$tQ1 Q2=$tQ2 S=$tS E1=$tE1 E2=$tE2 R1=$tR1 R2=$tR2"
Write-Host "SUMMARY Q1=$tQ1 Q2=$tQ2 S=$tS E1=$tE1 E2=$tE2 R1=$tR1 R2=$tR2"

# v0.01.0028 — test 9-key syllable composition for 分爿 (fen + pan)
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$APK = "D:\Study\InputMethod\ChinesePinyinIME\app\build\outputs\apk\debug\app-debug.apk"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0028_2026-07-09_155530"
$DEVICE = "7fbf2094"
$LOG = Join-Path $TEST_DIR "artifacts\adb_log.txt"
"" | Set-Content $LOG

# T9 digits: left col 64dp shifted
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$PINYIN_X = 128
$SPACE_X = 665; $SPACE_Y = 2940
# candidate row approx
$CAND_Y = 2140
$CAND1_X = 200
$EXPAND_X = 1320
$PAGE_NEXT_X = 1240
# expanded panel first rows (rough grid over keyboard area)
$EXP_Y1 = 2360; $EXP_Y2 = 2480; $EXP_Y3 = 2600; $EXP_Y4 = 2720
$EXP_X1 = 200; $EXP_X2 = 450; $EXP_X3 = 700; $EXP_X4 = 950; $EXP_X5 = 1200

function Log($m) { $l = "$(Get-Date -Format 'HH:mm:ss') $m"; Add-Content $LOG $l -ErrorAction SilentlyContinue; Write-Host $l }
function Tap($x,$y,$label) { Log "tap $label ($x,$y)"; & $ADB -s $DEVICE shell input tap $x $y | Out-Null; Start-Sleep -Milliseconds 700 }
function Shot($name,$sub) {
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
  return (Get-Content $p -Raw -ErrorAction SilentlyContinue)
}
function HideKbd() { & $ADB -s $DEVICE shell input keyevent 4 | Out-Null; Start-Sleep -Milliseconds 450 }
function OpenApp() {
  & $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
  Start-Sleep -Seconds 2
}
function FocusField() {
  $xml = Dump "field.xml"
  $m = [regex]::Match($xml, 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if ($m.Success) {
    $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value)/2)
    $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value)/2)
    Tap $x $y "ime_test_input"
  } else { Tap 720 900 "ime_test_input fb" }
  Start-Sleep -Milliseconds 900
}
function EnsureT9() {
  OpenApp; HideKbd
  & $ADB -s $DEVICE shell input swipe 720 2100 720 900 350 | Out-Null
  Start-Sleep -Milliseconds 500
  $xml = Dump "settings_layout.xml"
  $m = [regex]::Match($xml, 'resource-id="com\.mercury\.chinesepinyinime:id/keyboard_layout_toggle_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
  if ($xml -match '当前布局：26' -and $m.Success) {
    $x = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value)/2)
    $y = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value)/2)
    Tap $x $y "switch to 9-key"
    Start-Sleep -Milliseconds 500
  } else { Log "layout already 9 or unknown" }
  Shot "s01_layout.png" "01_settings"
  # clear field text: select all delete via long press is hard; reopen focus and use 重输 later
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

Log "=== Install ==="
& $ADB -s $DEVICE install -r $APK 2>&1 | ForEach-Object { Log "$_" }
$cur = & $ADB -s $DEVICE shell settings get secure default_input_method
Log "ime=$cur"
if ($cur -notmatch "chinesepinyinime") {
  & $ADB -s $DEVICE shell ime set "$PKG/.ChinesePinyinInputMethodService" 2>&1 | Out-Null
}

EnsureT9
OpenApp
FocusField
Shot "t00_ready.png" "02_t9_fenpan"

# --- Path A: syllable by syllable 336 then 726 ---
Log "=== Path A: type 336 (fen) ==="
# clear any leftover via 重输
Tap $C3 $R2Y "重输"
TapDigit "3"; TapDigit "3"; TapDigit "6"
Start-Sleep -Milliseconds 600
Shot "t01_336_fen.png" "02_t9_fenpan"

# if multiple pinyin, fen should be default or tappable on left list
# tap fen area (first or second item) — try default first candidate 分
Tap $CAND1_X $CAND_Y "cand 分"
Start-Sleep -Milliseconds 700
Shot "t02_after_fen_commit.png" "02_t9_fenpan"

Log "=== Path A: type 726 (pan) ==="
TapDigit "7"; TapDigit "2"; TapDigit "6"
Start-Sleep -Milliseconds 700
Shot "t03_726_pan.png" "02_t9_fenpan"

# expand candidates to find 爿 (index ~13)
Tap $EXPAND_X $CAND_Y "expand candidates"
Start-Sleep -Milliseconds 800
Shot "t04_expanded_pan.png" "02_t9_fenpan"

# try paging if expand didn't open: page next several times
# In expanded panel, 爿 may be in later positions. Try several taps in grid.
# First page candidates: 盘盼判拚畔攀 — not 爿
# Second page of compact: need expand scroll

# Scroll expanded list down
& $ADB -s $DEVICE shell input swipe 720 2700 720 2300 350 | Out-Null
Start-Sleep -Milliseconds 500
Shot "t05_expanded_scrolled.png" "02_t9_fenpan"

# Tap candidates that might be 爿 — mid-right cells after scroll
# Also try page next on compact bar if expand failed
for ($i=0; $i -lt 4; $i++) {
  Tap $PAGE_NEXT_X $CAND_Y "page next $i"
  Start-Sleep -Milliseconds 400
}
Shot "t06_after_pages.png" "02_t9_fenpan"

# Try expand again and tap various cells looking for 爿
Tap $EXPAND_X $CAND_Y "expand again"
Start-Sleep -Milliseconds 600
Shot "t07_expand_again.png" "02_t9_fenpan"

# Grid taps across expanded panel
$positions = @(
  @($EXP_X1,$EXP_Y1),@($EXP_X2,$EXP_Y1),@($EXP_X3,$EXP_Y1),@($EXP_X4,$EXP_Y1),@($EXP_X5,$EXP_Y1),
  @($EXP_X1,$EXP_Y2),@($EXP_X2,$EXP_Y2),@($EXP_X3,$EXP_Y2),@($EXP_X4,$EXP_Y2),@($EXP_X5,$EXP_Y2),
  @($EXP_X1,$EXP_Y3),@($EXP_X2,$EXP_Y3),@($EXP_X3,$EXP_Y3),@($EXP_X4,$EXP_Y3),@($EXP_X5,$EXP_Y3),
  @($EXP_X1,$EXP_Y4),@($EXP_X2,$EXP_Y4),@($EXP_X3,$EXP_Y4),@($EXP_X4,$EXP_Y4),@($EXP_X5,$EXP_Y4)
)
# Don't auto-tap all (would commit wrong). Just screenshot and dump field text.
$xml = Dump "after_pan.xml"
if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*text="([^"]*)"') {
  Log "field_text=$($Matches[1])"
} else { Log "field_text=(no text attr)" }

# --- Path B: full 336726 then syllable select ---
Log "=== Path B: full 336726 ==="
Tap $C3 $R2Y "重输"
# clear field: DEL many times on empty composing - need to clear text
# long press field then select all is hard; use input keyevent DEL many
for ($i=0; $i -lt 8; $i++) { & $ADB -s $DEVICE shell input keyevent 67 | Out-Null }
Start-Sleep -Milliseconds 300
FocusField
Tap $C3 $R2Y "重输"
foreach ($d in @("3","3","6","7","2","6")) { TapDigit $d }
Start-Sleep -Milliseconds 800
Shot "t08_full_336726.png" "02_t9_fenpan"

# left list should show fen etc. - tap first pinyin (likely fen default)
# If fen not first, try pinyin list y positions
Tap $PINYIN_X 2440 "pinyin1"
Start-Sleep -Milliseconds 500
Shot "t09_after_pinyin1.png" "02_t9_fenpan"
Tap $CAND1_X $CAND_Y "cand after pinyin"
Start-Sleep -Milliseconds 700
Shot "t10_after_first_syllable.png" "02_t9_fenpan"

# remaining pan digits auto?
Shot "t11_remaining.png" "02_t9_fenpan"
Tap $EXPAND_X $CAND_Y "expand for 爿"
Start-Sleep -Milliseconds 700
Shot "t12_expand_for_pan2.png" "02_t9_fenpan"
& $ADB -s $DEVICE shell input swipe 720 2700 720 2200 400 | Out-Null
Start-Sleep -Milliseconds 500
Shot "t13_scroll_for_pan2.png" "02_t9_fenpan"

$xml = Dump "final.xml"
if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*text="([^"]*)"') {
  Log "final_field_text=$($Matches[1])"
}

# settings learned status
HideKbd
& $ADB -s $DEVICE shell input swipe 720 1800 720 900 300 | Out-Null
Start-Sleep -Milliseconds 400
Shot "t14_learned_status.png" "01_settings"

Log "=== DONE ==="

# v0.02.0001 remaining device tests (priority, malformed, export, clear, regression)
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.02.0001_2026-07-11_003542"
$DEVICE = "7fbf2094"
$LOG = Join-Path $TEST_DIR "artifacts\remaining_log.txt"
"" | Set-Content $LOG

$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$PINYIN_X = 128; $PINYIN1_Y = 2330
$CAND_Y = 2140; $CAND1_X = 200; $EXPAND_X = 1360; $EXPAND_Y = 2140
$DEL_X = $C3; $DEL_Y = $R1Y
$CHONGSHU_X = $C3; $CHONGSHU_Y = $R2Y

function Log($m) {
  $l = "$(Get-Date -Format 'HH:mm:ss') $m"
  try { Add-Content $LOG $l -ErrorAction SilentlyContinue } catch {}
  Write-Host $l
}
function Tap($x, $y, $label) {
  Log "tap $label ($x,$y)"
  & $ADB -s $DEVICE shell input tap $x $y | Out-Null
  Start-Sleep -Milliseconds 700
}
function Shot($name, $sub) {
  $d = Join-Path $TEST_DIR "screenshots\$sub"
  New-Item -ItemType Directory -Force -Path $d | Out-Null
  $dest = Join-Path $d $name
  & $ADB -s $DEVICE shell screencap -p /sdcard/ime_shot.png | Out-Null
  $pull = & $ADB -s $DEVICE pull /sdcard/ime_shot.png $dest 2>&1
  if (-not (Test-Path $dest)) {
    Log "SHOT_FAIL $sub/$name pull=$pull"
  } else {
    Log "shot $sub/$name size=$((Get-Item $dest).Length)"
  }
}
function Dump($name) {
  $p = Join-Path $TEST_DIR "ui_dumps\$name"
  & $ADB -s $DEVICE shell uiautomator dump /sdcard/ime_ui.xml | Out-Null
  & $ADB -s $DEVICE pull /sdcard/ime_ui.xml $p 2>&1 | Out-Null
  Log "dump $name"
  if (Test-Path $p) { return (Get-Content $p -Raw -ErrorAction SilentlyContinue) }
  return ""
}
function ScrollTop() {
  for ($i = 0; $i -lt 3; $i++) {
    & $ADB -s $DEVICE shell input swipe 720 900 720 2500 250 | Out-Null
    Start-Sleep -Milliseconds 200
  }
}
function ScrollDownN($n) {
  for ($i = 0; $i -lt $n; $i++) {
    & $ADB -s $DEVICE shell input swipe 720 2200 720 900 350 | Out-Null
    Start-Sleep -Milliseconds 350
  }
}
function OpenApp() {
  & $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
  Start-Sleep -Seconds 1
}
function FocusField() {
  ScrollTop
  $xml = Dump "focus.xml"
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Tap $x $y "field"
  } else {
    Tap 720 1006 "field fb"
  }
  Start-Sleep -Milliseconds 800
}
function ClearField() {
  & $ADB -s $DEVICE shell input keyevent 123 | Out-Null
  for ($i = 0; $i -lt 40; $i++) { & $ADB -s $DEVICE shell input keyevent 67 | Out-Null }
}
function ChongShu() { Tap $CHONGSHU_X $CHONGSHU_Y "重输" }
function TypeDigits($s) {
  foreach ($ch in $s.ToCharArray()) {
    switch ([string]$ch) {
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
}
function EnsureLayout($want9) {
  OpenApp
  & $ADB -s $DEVICE shell input keyevent 4 | Out-Null
  Start-Sleep -Milliseconds 300
  ScrollDownN 3
  $xml = Dump "layout_check.xml"
  $is9 = $xml -match '当前布局：9'
  Log "layout is9=$is9 want9=$want9"
  if ($want9 -and -not $is9) {
    if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/keyboard_layout_toggle_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
      $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
      $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
      Tap $x $y "toggle to 9"
    }
  } elseif (-not $want9 -and $is9) {
    if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/keyboard_layout_toggle_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
      $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
      $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
      Tap $x $y "toggle to 26"
    }
  }
  Start-Sleep -Milliseconds 500
}
function ReadyT9() {
  EnsureLayout $true
  OpenApp
  FocusField
  ClearField
  ChongShu
}
function ReadManualStatus() {
  OpenApp
  & $ADB -s $DEVICE shell input keyevent 4 | Out-Null
  Start-Sleep -Milliseconds 300
  ScrollTop
  ScrollDownN 2
  $xml = Dump "manual_status_check.xml"
  Shot "status_check.png" "02_import_valid"
  [regex]::Matches($xml, 'text="([^"]+)"') | ForEach-Object {
    $t = $_.Groups[1].Value
    if ($t -match '人工|导入|有效|重复|无效|条|清空|导出') { Log "UI:$t" }
  }
  return $xml
}
function OpenImportPicker() {
  OpenApp
  & $ADB -s $DEVICE shell input keyevent 4 | Out-Null
  Start-Sleep -Milliseconds 300
  ScrollDownN 3
  $xml = Dump "import_btn.xml"
  if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/import_manual_dictionary_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Tap $x $y "import"
  } else {
    Tap 720 187 "import fb"
  }
  Start-Sleep -Seconds 2
}
function SelectRecentFile($filename) {
  $xml = Dump "picker_for_$filename.xml"
  Shot "picker_before_$filename.png" "04_import_malformed"
  if ($xml -match "text=`"$([regex]::Escape($filename))`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Tap $x $y "file $filename"
    return $true
  }
  # try scroll in picker
  & $ADB -s $DEVICE shell input swipe 720 2200 720 1200 300 | Out-Null
  Start-Sleep -Milliseconds 500
  $xml = Dump "picker2_for_$filename.xml"
  if ($xml -match "text=`"$([regex]::Escape($filename))`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"") {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Tap $x $y "file $filename"
    return $true
  }
  Log "FILE_NOT_FOUND $filename"
  return $false
}

Log "=== remaining tests start ==="
Log "manual store before:"
& $ADB -s $DEVICE shell "run-as $PKG cat files/manual_dictionary.tsv" 2>&1 | ForEach-Object { Log "store: $_" }

# ---- Priority: 64 on 9-key ----
Log "=== PRIORITY 64 ==="
ReadyT9
Shot "p01_ready.png" "03_priority_input"
TypeDigits "64"
Start-Sleep -Milliseconds 1000
Shot "p02_64_candidates.png" "03_priority_input"
Dump "p02_64.xml" | Out-Null

# ---- 26-key ni ----
Log "=== PRIORITY 26-key ni ==="
EnsureLayout $false
OpenApp
FocusField
ClearField
# Wait for keyboard - 26 key letter positions (calibrated for OP7 Pro Chinese layout)
# From earlier 0025 tests approximate:
# letter row1 Y~2364, row2~2556, row3~2748
# n is on bottom letter row (approx x=950), i on top row (approx x=1080)
Shot "p03_26_ready.png" "03_priority_input"
# Try tapping n then i with a few probe points if needed - first shot after
Tap 950 2748 "n"
Tap 1080 2364 "i"
Start-Sleep -Milliseconds 1000
Shot "p04_26_ni.png" "03_priority_input"
Dump "p04_26_ni.xml" | Out-Null

# switch back to 9-key for rest
EnsureLayout $true

# ---- Malformed only import ----
Log "=== MALFORMED ONLY ==="
# capture manual count before
$before = & $ADB -s $DEVICE shell "run-as $PKG cat files/manual_dictionary.tsv" 2>&1
Log "before_malformed_import_len=$($before.Length)"
OpenImportPicker
Shot "m01_picker.png" "04_import_malformed"
if (SelectRecentFile "malformed_only.tsv") {
  Start-Sleep -Seconds 3
  Shot "m02_after_malformed.png" "04_import_malformed"
  $xml = ReadManualStatus
  $after = & $ADB -s $DEVICE shell "run-as $PKG cat files/manual_dictionary.tsv" 2>&1
  Log "after_malformed_store:"
  $after | ForEach-Object { Log "store: $_" }
} else {
  Shot "m02_file_missing.png" "04_import_malformed"
}

# ---- Export ----
Log "=== EXPORT ==="
OpenApp
& $ADB -s $DEVICE shell input keyevent 4 | Out-Null
Start-Sleep -Milliseconds 300
ScrollDownN 3
$xml = Dump "export_btn.xml"
if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/export_dictionary_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
  $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
  $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
  Tap $x $y "export"
} else {
  Tap 720 419 "export fb"
}
Start-Sleep -Seconds 2
Shot "e01_export_picker.png" "05_export"
$xml = Dump "export_picker.xml"
# try confirm save - look for save/保存 button or just accept default name
if ($xml -match 'text="保存"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' -or
    $xml -match 'text="SAVE"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' -or
    $xml -match 'content-desc="保存"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
  $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
  $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
  Tap $x $y "save"
} else {
  # OnePlus CreateDocument often has checkmark top-right
  Tap 1320 230 "save checkmark?"
}
Start-Sleep -Seconds 3
Shot "e02_after_export.png" "05_export"
$xml = ReadManualStatus
# find exported file
& $ADB -s $DEVICE shell "ls -la /sdcard/Download/*.tsv /sdcard/Download/ChinesePinyin* 2>/dev/null; ls -la /sdcard/Documents/* 2>/dev/null" | ForEach-Object { Log "export_ls: $_" }
# pull any recent export
$exp = & $ADB -s $DEVICE shell "ls -t /sdcard/Download/*.tsv 2>/dev/null | head -3"
Log "export_candidates=$exp"

# ---- Clear manual only ----
Log "=== CLEAR MANUAL ==="
# re-import example first if malformed wiped? check store
$store = & $ADB -s $DEVICE shell "run-as $PKG cat files/manual_dictionary.tsv" 2>&1
if ("$store" -notmatch '妮') {
  Log "manual missing 妮; reimport mixed"
  OpenImportPicker
  SelectRecentFile "mixed.tsv" | Out-Null
  Start-Sleep -Seconds 3
}
OpenApp
& $ADB -s $DEVICE shell input keyevent 4 | Out-Null
Start-Sleep -Milliseconds 300
ScrollDownN 3
$xml = Dump "clear_manual_btn.xml"
Shot "c01_before_clear_manual.png" "06_clear_layers"
if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/clear_manual_dictionary_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
  $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
  $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
  Tap $x $y "clear manual"
} else {
  Tap 720 651 "clear manual fb"
}
Start-Sleep -Seconds 3
Shot "c02_after_clear_manual.png" "06_clear_layers"
$xml = ReadManualStatus
$store = & $ADB -s $DEVICE shell "run-as $PKG ls files; run-as $PKG cat files/manual_dictionary.tsv 2>&1; run-as $PKG cat files/user_dictionary.tsv 2>&1" 
$store | ForEach-Object { Log "after_clear_manual: $_" }

# priority should be gone - 64 first cand not 妮
ReadyT9
TypeDigits "64"
Start-Sleep -Milliseconds 1000
Shot "c03_64_after_clear_manual.png" "06_clear_layers"

# ---- Clear learned ----
Log "=== CLEAR LEARNED ==="
# reimport mixed so manual present while clearing learned
OpenImportPicker
SelectRecentFile "mixed.tsv" | Out-Null
Start-Sleep -Seconds 3
OpenApp
& $ADB -s $DEVICE shell input keyevent 4 | Out-Null
Start-Sleep -Milliseconds 300
ScrollDownN 4
$xml = Dump "clear_learned_btn.xml"
Shot "c04_before_clear_learned.png" "06_clear_layers"
if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/clear_learned_data_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
  $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
  $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
  Tap $x $y "clear learned"
  Start-Sleep -Milliseconds 800
  $xml2 = Dump "clear_learned_dialog.xml"
  if ($xml2 -match 'text="确定"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' -or
      $xml2 -match 'text="清除"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
    $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
    Tap $x $y "confirm clear learned"
  }
}
Start-Sleep -Seconds 2
Shot "c05_after_clear_learned.png" "06_clear_layers"
$store = & $ADB -s $DEVICE shell "run-as $PKG cat files/manual_dictionary.tsv 2>&1; echo '---'; run-as $PKG cat files/user_dictionary.tsv 2>&1; echo '---'; run-as $PKG cat files/user_frequency.tsv 2>&1"
$store | ForEach-Object { Log "after_clear_learned: $_" }
$xml = ReadManualStatus

# ---- Regressions ----
Log "=== REGRESSION ==="
ReadyT9
Shot "r01_ready.png" "07_regression"
TypeDigits "64426"
Start-Sleep -Milliseconds 1000
Shot "r02_64426.png" "07_regression"
Tap $CAND1_X $CAND_Y "cand 你好?"
Start-Sleep -Milliseconds 900
Shot "r03_nihao.png" "07_regression"

ReadyT9
TypeDigits "64426"
Start-Sleep -Milliseconds 700
Tap $DEL_X $DEL_Y "DEL"
Start-Sleep -Milliseconds 500
Shot "r04_del.png" "07_regression"

ReadyT9
TypeDigits "64"
Start-Sleep -Milliseconds 700
Tap $EXPAND_X $EXPAND_Y "expand"
Start-Sleep -Milliseconds 800
Shot "r05_expand.png" "07_regression"

# cold start 744824 (0032)
Log "=== COLD START 744824 ==="
& $ADB -s $DEVICE shell am force-stop $PKG
Start-Sleep -Milliseconds 800
OpenApp
FocusField
# type immediately without waiting for dict ready
TypeDigits "744824"
Start-Sleep -Milliseconds 600
Shot "r06_cold_744824.png" "07_regression"
Start-Sleep -Seconds 3
Shot "r07_after_load_744824.png" "07_regression"

# rapid 644 (0031) - 5 cycles for smoke (20 would be long)
Log "=== RAPID 644 smoke ==="
ReadyT9
for ($i = 1; $i -le 5; $i++) {
  ChongShu
  TypeDigits "644"
  Start-Sleep -Milliseconds 200
  Shot ("r08_rapid644_$i.png") "07_regression"
}

Log "=== DONE ==="
& $ADB -s $DEVICE shell "run-as $PKG cat files/manual_dictionary.tsv" 2>&1 | ForEach-Object { Log "final_manual: $_" }
Write-Host "DONE"

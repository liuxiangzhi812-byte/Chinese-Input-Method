# v0.02.0003 device smoke: version, 26-key ji expand, learn rebuild logs, T9 nihao
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$DEVICE = "7fbf2094"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.02.0003_2026-07-13_141900"
$LOG = Join-Path $TEST_DIR "artifacts\test_log.txt"
"" | Set-Content $LOG

# 26-key calibration (OnePlus 7 Pro 1440x3120, prior sessions)
$I_X = 1065; $I_Y = 2390
$J_X = 930;  $J_Y = 2590   # row2 ~j
$N_X = 1005; $N_Y = 2790
$H_X = 820;  $H_Y = 2590
$A_X = 220;  $A_Y = 2590
$O_X = 1200; $O_Y = 2390
$SPACE_X = 665; $SPACE_Y = 2940
$DEL_X = 1310; $DEL_Y = 2790
$EXPAND_X = 1360; $EXPAND_Y = 2140
$CAND1_X = 180; $CAND_Y = 2140

# T9
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292

function Log($m) { $l = "$(Get-Date -Format 'HH:mm:ss') $m"; Add-Content $LOG $l; Write-Host $l }
function Tap($x,$y,$label) { Log "tap $label ($x,$y)"; & $ADB -s $DEVICE shell input tap $x $y | Out-Null; Start-Sleep -Milliseconds 700 }
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
}

Log "=== v0.02.0003 device smoke start ==="
& $ADB -s $DEVICE logcat -c | Out-Null
& $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
Start-Sleep -Seconds 1
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 3
Dump "open.xml"
Shot "s01_open.png" "01_baseline"

# Scroll to layout toggle if needed and ensure 26-key
& $ADB -s $DEVICE shell input swipe 720 2400 720 900 350 | Out-Null
Start-Sleep -Milliseconds 500
$xml = Get-Content (Join-Path $TEST_DIR "ui_dumps\open.xml") -Raw -ErrorAction SilentlyContinue
if ($xml -notmatch '26 键') {
  # Try tap layout toggle region
  if ($xml -match '切换为26键.*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $tx = [int](([int]$Matches[1]+[int]$Matches[3])/2)
    $ty = [int](([int]$Matches[2]+[int]$Matches[4])/2)
    Tap $tx $ty "switch to 26-key"
  } elseif ($xml -match '切换为9键') {
    Log "already 26-key (toggle says switch to 9)"
  } else {
    Log "layout toggle not found in dump; continue"
  }
}

# Focus test field
& $ADB -s $DEVICE shell input swipe 720 900 720 1800 300 | Out-Null
Start-Sleep -Milliseconds 400
Tap 720 1006 "ime_test_input"
Start-Sleep -Seconds 1
Shot "s02_keyboard.png" "01_baseline"

Log "=== T02 type ji and expand ==="
# Clear field: select all + del is hard; long DEL
for ($i=0; $i -lt 8; $i++) { Tap $DEL_X $DEL_Y "del" }
Tap $J_X $J_Y "j"
Tap $I_X $I_Y "i"
Start-Sleep -Milliseconds 500
Shot "c01_ji_compact.png" "02_candidates"
Tap $EXPAND_X $EXPAND_Y "expand toggle"
Start-Sleep -Milliseconds 800
Shot "c02_ji_expanded.png" "02_candidates"
# Scroll expanded panel (center of keyboard replacement area ~ y 2500)
& $ADB -s $DEVICE shell input swipe 720 2650 720 2300 400 | Out-Null
Start-Sleep -Milliseconds 400
& $ADB -s $DEVICE shell input swipe 720 2650 720 2300 400 | Out-Null
Start-Sleep -Milliseconds 400
Shot "c03_ji_scrolled.png" "02_candidates"
# Collapse
Tap $EXPAND_X $EXPAND_Y "collapse"
Start-Sleep -Milliseconds 400

Log "=== T03 learn path: commit several candidates to exercise incremental merge ==="
for ($k=0; $k -lt 12; $k++) {
  for ($d=0; $d -lt 6; $d++) { Tap $DEL_X $DEL_Y "del" }
  Tap $N_X $N_Y "n"
  Tap $I_X $I_Y "i"
  Start-Sleep -Milliseconds 300
  # Tap different-ish candidate x positions across the bar
  $cx = 160 + ($k % 6) * 160
  Tap $cx $CAND_Y "cand_$k"
  Start-Sleep -Milliseconds 350
}
Shot "l01_after_learn_taps.png" "03_learn"

Log "=== T04 T9 64426 nihao smoke ==="
# Ensure 9-key
& $ADB -s $DEVICE shell input keyevent 4 | Out-Null
Start-Sleep -Milliseconds 400
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 1
& $ADB -s $DEVICE shell input swipe 720 2400 720 900 350 | Out-Null
Start-Sleep -Milliseconds 500
Dump "layout.xml"
$layout = Get-Content (Join-Path $TEST_DIR "ui_dumps\layout.xml") -Raw -ErrorAction SilentlyContinue
if ($layout -match '切换为9键.*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
  $tx = [int](([int]$Matches[1]+[int]$Matches[3])/2)
  $ty = [int](([int]$Matches[2]+[int]$Matches[4])/2)
  Tap $tx $ty "switch to 9-key"
} elseif ($layout -match '当前布局：9 键') {
  Log "already 9-key"
} else {
  Log "WARN could not confirm 9-key toggle"
}
& $ADB -s $DEVICE shell input swipe 720 900 720 1800 300 | Out-Null
Tap 720 1006 "ime_test_input"
Start-Sleep -Seconds 1
# 6 4 4 2 6
Tap $C1 $R2Y "6"
Tap $C0 $R2Y "4"
Tap $C0 $R2Y "4"
Tap $C1 $R1Y "2"
Tap $C1 $R2Y "6"
Start-Sleep -Milliseconds 500
Shot "r01_64426.png" "04_regression"
Tap $CAND1_X $CAND_Y "commit first cand"
Start-Sleep -Milliseconds 500
Shot "r02_after_commit.png" "04_regression"

Log "=== T05 computer manager start smoke ==="
& $ADB -s $DEVICE shell input keyevent 4 | Out-Null
Start-Sleep -Milliseconds 400
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 1
& $ADB -s $DEVICE shell input swipe 720 2400 720 900 400 | Out-Null
Start-Sleep -Milliseconds 500
Dump "mgr.xml"
$mgr = Get-Content (Join-Path $TEST_DIR "ui_dumps\mgr.xml") -Raw -ErrorAction SilentlyContinue
if ($mgr -match '开启电脑管理.*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
  $tx = [int](([int]$Matches[1]+[int]$Matches[3])/2)
  $ty = [int](([int]$Matches[2]+[int]$Matches[4])/2)
  Tap $tx $ty "start computer manager"
  Start-Sleep -Seconds 1
  Shot "m01_manager_started.png" "05_manager"
  $svc = & $ADB -s $DEVICE shell dumpsys activity services $PKG 2>&1 | Out-String
  if ($svc -match 'ComputerDictionaryService') { Log "PASS ComputerDictionaryService running" } else { Log "WARN service not found in dumpsys" }
  # Stop again to avoid leaving FG service
  Dump "mgr2.xml"
  $mgr2 = Get-Content (Join-Path $TEST_DIR "ui_dumps\mgr2.xml") -Raw -ErrorAction SilentlyContinue
  if ($mgr2 -match '关闭电脑管理.*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
    $tx = [int](([int]$Matches[1]+[int]$Matches[3])/2)
    $ty = [int](([int]$Matches[2]+[int]$Matches[4])/2)
    Tap $tx $ty "stop computer manager"
  }
} else {
  Log "WARN start computer manager button not found"
}

Log "=== Collect logcat PinyinDict / OOM ==="
$logOut = Join-Path $TEST_DIR "artifacts\pinyin_dict_logcat.txt"
& $ADB -s $DEVICE logcat -d -s PinyinDict:I AndroidRuntime:E *:S | Out-File -Encoding utf8 $logOut
$rebuild = Select-String -Path $logOut -Pattern "rebuild reason=" -ErrorAction SilentlyContinue
Log ("rebuild log lines: " + (@($rebuild).Count))
$oom = Select-String -Path $logOut -Pattern "OutOfMemory|FATAL" -ErrorAction SilentlyContinue
Log ("oom/fatal lines: " + (@($oom).Count))
if ($rebuild) { $rebuild | ForEach-Object { Log $_.Line } }

Log "=== done ==="

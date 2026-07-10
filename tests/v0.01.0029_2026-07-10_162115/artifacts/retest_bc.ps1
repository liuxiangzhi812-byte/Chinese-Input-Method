# Retest Case B/C with pinyin-column coordinate probe
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0029_2026-07-10_162115"
$DEVICE = "7fbf2094"
$LOG = Join-Path $TEST_DIR "artifacts\retest_bc_log.txt"
"" | Set-Content $LOG

$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$CAND_Y = 2140; $CAND1_X = 200; $CAND2_X = 420
$CHONG_X = 1292; $CHONG_Y = 2556

function Log($m) { $l = "$(Get-Date -Format 'HH:mm:ss') $m"; Add-Content $LOG $l; Write-Host $l }
function Tap($x,$y,$label) { Log "tap $label ($x,$y)"; & $ADB -s $DEVICE shell input tap $x $y | Out-Null; Start-Sleep -Milliseconds 650 }
function Shot($name,$sub) {
  $d = Join-Path $TEST_DIR "screenshots\$sub"
  New-Item -ItemType Directory -Force -Path $d | Out-Null
  & $ADB -s $DEVICE shell screencap -p /sdcard/$name | Out-Null
  & $ADB -s $DEVICE pull /sdcard/$name (Join-Path $d $name) 2>&1 | Out-Null
  Log "shot $sub/$name"
}
function Dig($d) {
  switch ($d) {
    "1" { Tap $C0 $R1Y "1" }; "2" { Tap $C1 $R1Y "2" }; "3" { Tap $C2 $R1Y "3" }
    "4" { Tap $C0 $R2Y "4" }; "5" { Tap $C1 $R2Y "5" }; "6" { Tap $C2 $R2Y "6" }
    "7" { Tap $C0 $R3Y "7" }; "8" { Tap $C1 $R3Y "8" }; "9" { Tap $C2 $R3Y "9" }
    "0" { Tap $C3 $R3Y "0" }
  }
}
function TypeDigits($s) { foreach ($ch in $s.ToCharArray()) { Dig ([string]$ch) } }
function Back() { & $ADB -s $DEVICE shell input keyevent 4 | Out-Null; Start-Sleep -Milliseconds 500 }
function ForceHome() {
  & $ADB -s $DEVICE shell input keyevent 3 | Out-Null
  Start-Sleep -Milliseconds 400
  & $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
  Start-Sleep -Milliseconds 700
  & $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
  Start-Sleep -Seconds 2
}
function FocusField() {
  # field center from prior dump [96,1053][1344,1533] approx mid
  Tap 720 1293 "field"
  Start-Sleep -Milliseconds 900
}
function ClearText() {
  for ($i=0; $i -lt 20; $i++) { & $ADB -s $DEVICE shell input keyevent 67 | Out-Null }
  Start-Sleep -Milliseconds 200
  Tap $CHONG_X $CHONG_Y "重输"
}

# Leave system settings if stuck
Log "=== prep ==="
& $ADB -s $DEVICE shell ime set "$PKG/.ChinesePinyinInputMethodService" | Out-Null
for ($i=0; $i -lt 4; $i++) { Back }
ForceHome
FocusField
ClearText
Shot "rb00_ready.png" "03_case_b_syllable"

# --- Probe pinyin hit: type 64, try mi at several coords ---
Log "=== probe: 64 then tap mi at several coords ==="
TypeDigits "64"
Start-Sleep -Milliseconds 700
Shot "rb_probe_64.png" "03_case_b_syllable"

# Try candidates: if mi works, candidates become 米...
$probeCoords = @(
  @(128,2440), @(100,2364), @(128,2364), @(160,2364),
  @(100,2440), @(160,2440), @(128,2500), @(128,2520),
  @(90,2480), @(180,2480), @(128,2580)
)
$hit = $false
$idx = 0
foreach ($p in $probeCoords) {
  $idx++
  Tap $p[0] $p[1] "probe_mi_$idx"
  Start-Sleep -Milliseconds 500
  Shot ("rb_probe_$idx.png") "03_case_b_syllable"
  # After successful mi, first cand is usually 米 not 你
  # We can't OCR easily; use heuristic: if status still 中文 64 and we can re-check by visual later
}

# Restart clean for Case B proper
Log "=== CASE B retest: 64426 + force syllable via mi then ni path ==="
ForceHome
FocusField
ClearText
TypeDigits "64426"
Start-Sleep -Milliseconds 800
Shot "rb01_64426_whole.png" "03_case_b_syllable"

# Tap second pinyin (mi) first — if active highlight leaves ni, we know hit worked
# Then tap first (ni) to enter ni syllable mode
# Probe Y values around digit rows for first three pinyin slots
$pinyinYs = @(2364, 2440, 2480, 2520, 2556, 2600)
$pinyinXs = @(100, 128, 160)

# Strategy: tap mi (2nd) at several Y, screenshot; then tap ni (1st)
foreach ($x in $pinyinXs) {
  foreach ($y in @(2480, 2520, 2556, 2600)) {
    Tap $x $y "try_mi $x,$y"
  }
}
Shot "rb02_after_mi_probes.png" "03_case_b_syllable"

# Now try first slot (ni) at digit row heights
foreach ($x in $pinyinXs) {
  foreach ($y in @(2300, 2364, 2400, 2440)) {
    Tap $x $y "try_ni $x,$y"
  }
}
Shot "rb03_after_ni_probes.png" "03_case_b_syllable"

# If syllable mode engaged, first candidate should be single char like 你
Tap $CAND1_X $CAND_Y "cand1 after probes"
Start-Sleep -Milliseconds 800
Shot "rb04_after_cand1.png" "03_case_b_syllable"

# If composition advanced (remaining digits), try more candidates
Tap $CAND1_X $CAND_Y "cand1 remaining"
Start-Sleep -Milliseconds 700
Shot "rb05_after_cand2.png" "03_case_b_syllable"

# --- CASE C: 336726 syllable compose 分盘 ---
Log "=== CASE C retest: 336726 fen->分 pan->盘 ==="
ForceHome
FocusField
ClearText
# clear learned again
Back
& $ADB -s $DEVICE shell input swipe 720 2200 720 700 350 | Out-Null
Start-Sleep -Milliseconds 500
# tap clear learned if visible - approximate button y from s02
Tap 720 1846 "clear learned?"
Start-Sleep -Milliseconds 600
# if dialog, try 确定 area
Tap 900 1800 "confirm?"
Start-Sleep -Milliseconds 400
Back
ForceHome
FocusField
ClearText
Shot "rc00_ready.png" "04_case_c_learn"

TypeDigits "336726"
Start-Sleep -Milliseconds 800
Shot "rc01_336726.png" "04_case_c_learn"

# Force syllable: probe fen (1st) then if needed den (2nd)
foreach ($x in @(100,128,160)) {
  foreach ($y in @(2300,2364,2400,2440,2480)) {
    Tap $x $y "fen_probe $x,$y"
  }
}
Shot "rc02_after_fen_probe.png" "04_case_c_learn"

# First candidate should be 分 if syllable mode
Tap $CAND1_X $CAND_Y "cand 分?"
Start-Sleep -Milliseconds 800
Shot "rc03_after_fen_cand.png" "04_case_c_learn"

# Remaining 726 — try select pan from left list (may show pan/san/ran)
foreach ($x in @(100,128,160)) {
  foreach ($y in @(2364,2440,2520,2556,2600,2680)) {
    Tap $x $y "pan_probe $x,$y"
  }
}
Shot "rc04_after_pan_probe.png" "04_case_c_learn"
Tap $CAND1_X $CAND_Y "cand 盘?"
Start-Sleep -Milliseconds 900
Shot "rc05_after_pan_cand.png" "04_case_c_learn"

# Second candidate if first wrong
Tap $CAND2_X $CAND_Y "cand2"
Start-Sleep -Milliseconds 700
Shot "rc06_cand2.png" "04_case_c_learn"

# Recall attempt
Start-Sleep -Seconds 2
Tap $CHONG_X $CHONG_Y "重输"
ClearText
TypeDigits "336726"
Start-Sleep -Milliseconds 900
Shot "rc07_recall.png" "04_case_c_learn"
Tap $CAND1_X $CAND_Y "recall commit"
Start-Sleep -Milliseconds 800
Shot "rc08_recall_commit.png" "04_case_c_learn"

Log "=== DONE retest ==="

# v0.01.0025 — in-app IME test input box + smoke
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$APK = "D:\Study\InputMethod\ChinesePinyinIME\app\build\outputs\apk\debug\app-debug.apk"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0025_2026-07-09_133826"
$DEVICE = "7fbf2094"
$LOG = Join-Path $TEST_DIR "artifacts\adb_commands.txt"

# T9 grid — shifted right by 64dp (256px) on 1440-wide IME
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$PINYIN_X = 128
$NI_Y = 2440; $MI_Y = 2520
$SPACE_Y = 2940; $SPACE_X = 665
$SYM_TOGGLE_X = 131; $BOTTOM_Y = 2940

# 26-key (OnePlus 7 Pro, calibrated in prior sessions)
# n = row3 col6-ish; i = row1
$N_X = 1005; $N_Y = 2790
$I_X = 1065; $I_Y = 2390
$KEY_123_X = 131
$CAND_FIRST_X = 180; $CAND_Y = 2140

function Log($msg) {
    $line = "$(Get-Date -Format 'HH:mm:ss') $msg"
    Add-Content -Path $LOG -Value $line
    Write-Host $line
}

function Shot($name, $subdir) {
    $localDir = Join-Path $TEST_DIR "screenshots\$subdir"
    New-Item -ItemType Directory -Force -Path $localDir | Out-Null
    $local = Join-Path $localDir $name
    & $ADB -s $DEVICE shell screencap -p /sdcard/$name | Out-Null
    & $ADB -s $DEVICE pull /sdcard/$name $local 2>&1 | Out-Null
    Log "shot $subdir/$name"
}

function Tap($x, $y, $label) {
    Log "tap $label ($x,$y)"
    & $ADB -s $DEVICE shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 700
}

function Dump($name) {
    $path = Join-Path $TEST_DIR "ui_dumps\$name"
    & $ADB -s $DEVICE shell uiautomator dump /sdcard/$name | Out-Null
    & $ADB -s $DEVICE pull /sdcard/$name $path 2>&1 | Out-Null
    Log "dump $name"
    return (Get-Content $path -Raw -ErrorAction SilentlyContinue)
}

function OpenApp() {
    & $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
    Start-Sleep -Milliseconds 400
    & $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
    Start-Sleep -Seconds 2
}

function ForceStopIme() {
    # layout preference is read when IME restarts
    & $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
    Start-Sleep -Milliseconds 500
}

function EnsureIme() {
    $cur = & $ADB -s $DEVICE shell settings get secure default_input_method
    Log "default_ime=$cur"
    if ($cur -notmatch "chinesepinyinime") {
        & $ADB -s $DEVICE shell ime enable "$PKG/.ChinesePinyinInputMethodService" 2>&1 | Out-Null
        & $ADB -s $DEVICE shell ime set "$PKG/.ChinesePinyinInputMethodService" 2>&1 | Out-Null
        Start-Sleep -Milliseconds 500
        $cur = & $ADB -s $DEVICE shell settings get secure default_input_method
        Log "default_ime_after_set=$cur"
    }
}

function FocusTestBox() {
    # open app already requests focus; tap center of EditText if dump available
    $xml = Dump "focus_check.xml"
    if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
        $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
        Tap $x $y "ime_test_input"
        return $true
    }
    # fallback approx for OnePlus 7 Pro with new layout
    Tap 720 780 "ime_test_input fallback"
    return $false
}

function ToggleLayoutIfNeeded($wantT9) {
    OpenApp
    $xml = Dump "settings_layout.xml"
    $isT9 = $xml -match '9 键'
    Log "layout_isT9=$isT9 wantT9=$wantT9"
    if ($wantT9 -and -not $isT9) {
        if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/keyboard_layout_toggle_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
            Tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2)) "toggle to 9-key"
        } else {
            # may need scroll; try mid-page
            Tap 720 1500 "toggle 9-key fallback"
        }
        Start-Sleep -Milliseconds 400
        ForceStopIme
        OpenApp
    } elseif ((-not $wantT9) -and $isT9) {
        if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/keyboard_layout_toggle_button"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
            Tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2)) "toggle to 26-key"
        } else {
            Tap 720 1500 "toggle 26-key fallback"
        }
        Start-Sleep -Milliseconds 400
        ForceStopIme
        OpenApp
    }
}

function ShowKeyboard() {
    FocusTestBox
    # show soft input
    & $ADB -s $DEVICE shell input keyevent 4 2>&1 | Out-Null  # noop if not needed
    Start-Sleep -Milliseconds 200
    # tap field again to ensure IME up
    FocusTestBox
    Start-Sleep -Milliseconds 800
}

# --- begin ---
"" | Set-Content $LOG
Log "=== Install ==="
& $ADB -s $DEVICE install -r $APK 2>&1 | ForEach-Object { Log $_ }

Log "=== Ensure IME ==="
EnsureIme

Log "=== P01/P02 baseline: open app, version, quick test ==="
OpenApp
Shot "s01_app_open.png" "01_settings"
$xml = Dump "main_open.xml"
if ($xml -match 'v0\.01\.0025') { Log "PASS version v0.01.0025 visible in dump" } else { Log "CHECK version in screenshot" }
if ($xml -match '快速测试|ime_test_input') { Log "PASS quick test field present" } else { Log "CHECK quick test field" }
if ($xml -match 'resource-id="com\.mercury\.chinesepinyinime:id/ime_test_input"[^>]*focused="true"') {
    Log "PASS ime_test_input focused=true"
} else {
    Log "INFO focused attr not true or not present; will tap field"
}
Shot "s02_after_focus_check.png" "02_inapp_focus"

Log "=== T01 26-key: ni composing in app field ==="
ToggleLayoutIfNeeded $false
ShowKeyboard
Shot "t01_26key_ready.png" "03_26key_smoke"
Tap $N_X $N_Y "n"
Tap $I_X $I_Y "i"
Start-Sleep -Milliseconds 600
Shot "t01_ni_composing.png" "03_26key_smoke"
Dump "after_ni.xml" | Out-Null

Log "=== T02 space commit ==="
Tap $SPACE_X $SPACE_Y "space commit"
Start-Sleep -Milliseconds 600
Shot "t02_ni_space_commit.png" "03_26key_smoke"
Dump "after_space.xml" | Out-Null

Log "=== T03 candidate tap path (type ni again) ==="
Tap $N_X $N_Y "n"
Tap $I_X $I_Y "i"
Start-Sleep -Milliseconds 500
Shot "t03_ni_before_candidate_tap.png" "03_26key_smoke"
Tap $CAND_FIRST_X $CAND_Y "first candidate"
Start-Sleep -Milliseconds 500
Shot "t03_ni_candidate_tap.png" "03_26key_smoke"

Log "=== T04 DEL short + long ==="
# type a few letters then del
Tap $N_X $N_Y "n"
Tap $I_X $I_Y "i"
Start-Sleep -Milliseconds 400
# DEL key bottom-right-ish on 26-key
Tap 1310 2790 "DEL short"
Start-Sleep -Milliseconds 400
Shot "t04_after_del_short.png" "03_26key_smoke"
& $ADB -s $DEVICE shell input swipe 1310 2790 1310 2790 1200
Start-Sleep -Milliseconds 800
Shot "t04_after_del_long.png" "03_26key_smoke"

Log "=== T05 symbol keyboard round trip ==="
Tap $KEY_123_X $BOTTOM_Y "123 symbols"
Start-Sleep -Milliseconds 600
Shot "t05_symbol_mode.png" "03_26key_smoke"
# return ABC usually same corner
Tap $KEY_123_X $BOTTOM_Y "ABC return"
Start-Sleep -Milliseconds 600
Shot "t05_back_letters.png" "03_26key_smoke"

Log "=== T06 switch to 9-key, in-app field ==="
ToggleLayoutIfNeeded $true
ShowKeyboard
Shot "t06_t9_ready.png" "04_t9_smoke"

Log "=== T07 64 -> ni/mi vertical list ==="
Tap $C2 $R2Y "6"
Tap $C0 $R2Y "4"
Start-Sleep -Milliseconds 600
Shot "t07_64_vertical_list.png" "04_t9_smoke"

Log "=== T08 tap mi ==="
Tap $PINYIN_X $MI_Y "mi"
Start-Sleep -Milliseconds 500
Shot "t08_mi_selected.png" "04_t9_smoke"

Log "=== T09 94664 zhong ==="
Tap $C3 $R2Y "重输"
Tap $C2 $R1Y "9"; Tap $C1 $R2Y "4"; Tap $C2 $R2Y "6"; Tap $C2 $R2Y "6"; Tap $C0 $R2Y "4"
Start-Sleep -Milliseconds 600
Shot "t09_94664_zhong.png" "04_t9_smoke"

Log "=== T10 重输 + empty 0 space ==="
Tap $C3 $R2Y "重输"
Start-Sleep -Milliseconds 400
Shot "t10_after_retype.png" "04_t9_smoke"
Tap $C3 $R3Y "0 space"
Start-Sleep -Milliseconds 500
Shot "t10_zero_space.png" "04_t9_smoke"

Log "=== T11 T9 symbol round trip ==="
Tap $C0 $R1Y "1 symbol"
Start-Sleep -Milliseconds 500
Shot "t11_t9_symbol.png" "04_t9_smoke"
Tap $SYM_TOGGLE_X $BOTTOM_Y "9键 return"
Start-Sleep -Milliseconds 500
Shot "t11_back_t9.png" "04_t9_smoke"

Log "=== T12 settings still intact ==="
OpenApp
# scroll down a bit to see buttons
& $ADB -s $DEVICE shell input swipe 720 2000 720 900 400
Start-Sleep -Milliseconds 500
Shot "t12_settings_scrolled.png" "05_layout_toggle"
$xml = Dump "settings_final.xml"
if ($xml -match 'clear_learned_data_button|清除') { Log "PASS clear learned present" }
if ($xml -match 'open_input_settings_button|输入法设置') { Log "PASS open settings present" }
if ($xml -match 'keyboard_layout_toggle_button|切换') { Log "PASS layout toggle present" }

Log "=== DONE ==="

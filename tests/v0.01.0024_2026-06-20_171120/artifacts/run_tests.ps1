# v0.01.0024 — vertical pinyin chooser + regression
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$EDGE = "com.microsoft.emmx"
$APK = "D:\Study\InputMethod\ChinesePinyinIME\app\build\outputs\apk\debug\app-debug.apk"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0024_2026-06-20_171120"
$DEVICE = "7fbf2094"

# T9 grid — shifted right by 64dp (256px) on 1440-wide IME
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 404; $C1 = 700; $C2 = 996; $C3 = 1292
$PINYIN_X = 128
$NI_Y = 2440; $MI_Y = 2520
$SPACE_Y = 2940; $SPACE_X = 665
$SYM_TOGGLE_X = 131; $BOTTOM_Y = 2940

# 26-key approx (OnePlus 7 Pro, from prior sessions)
$N_X = 1100; $I_X = 1242
$ROW2_Y = 2556
$KEY_123_X = 131

function Shot($name, $subdir) {
    $local = Join-Path $TEST_DIR "screenshots/$subdir/$name"
    & $ADB -s $DEVICE shell screencap -p /sdcard/$name | Out-Null
    & $ADB -s $DEVICE pull /sdcard/$name $local 2>&1 | Out-Null
    Write-Host "shot $subdir/$name"
}

function Tap($x, $y, $label) {
    Write-Host "tap $label ($x,$y)"
    & $ADB -s $DEVICE shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 750
}

function FocusEdgeSearch() {
    & $ADB -s $DEVICE shell am force-stop $EDGE | Out-Null
    Start-Sleep -Milliseconds 400
    & $ADB -s $DEVICE shell monkey -p $EDGE -c android.intent.category.LAUNCHER 1 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    Tap 720 1780 "dismiss copilot"
    Tap 720 200 "edge search bar"
}

function EnsureT9() {
    & $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
    Start-Sleep -Seconds 2
    & $ADB -s $DEVICE shell uiautomator dump /sdcard/settings.xml | Out-Null
    & $ADB -s $DEVICE pull /sdcard/settings.xml (Join-Path $TEST_DIR "ui_dumps/settings.xml") 2>&1 | Out-Null
    $xml = Get-Content (Join-Path $TEST_DIR "ui_dumps/settings.xml") -Raw -ErrorAction SilentlyContinue
    if ($xml -notmatch '9 键') {
        if ($xml -match 'keyboard_layout_toggle_button[^>]+bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
            $x = [int](([int]$Matches[1] + [int]$Matches[3]) / 2)
            $y = [int](([int]$Matches[2] + [int]$Matches[4]) / 2)
            Tap $x $y "toggle 9-key"
        } else { Tap 720 1180 "toggle 9-key fallback" }
        Start-Sleep -Milliseconds 500
    }
}

function Input64() {
    Tap $C2 $R2Y "6"
    Tap $C0 $R2Y "4"
    Start-Sleep -Milliseconds 500
}

Write-Host "=== Install ==="
& $ADB -s $DEVICE install -r $APK

Write-Host "=== Settings + version ==="
EnsureT9
Shot "s01_version_9key.png" "01_settings"

Write-Host "=== T01 vertical list 64 ==="
FocusEdgeSearch
Input64
Shot "t01_64_vertical_ni_mi.png" "02_t01_t02_vertical_list"

Write-Host "=== T02 click mi ==="
Tap $PINYIN_X $MI_Y "mi in left list"
Start-Sleep -Milliseconds 500
Shot "t02_mi_selected_candidates.png" "02_t01_t02_vertical_list"

Write-Host "=== T03 append 9 clears selection ==="
Tap $C2 $R3Y "append 9"
Start-Sleep -Milliseconds 500
Shot "t03_after_append_649.png" "03_t03_t05_behavior"

Write-Host "=== T05 empty / unambiguous blank column ==="
Tap $C3 $R2Y "重输"
Start-Sleep -Milliseconds 400
Shot "t05_empty_blank_column.png" "03_t03_t05_behavior"
Tap $C2 $R3Y "9"
Tap $C2 $R1Y "3"
Tap $C2 $R2Y "6"
Start-Sleep -Milliseconds 500
Shot "t05_unambiguous_936_blank.png" "03_t03_t05_behavior"

Write-Host "=== T04 DEL / long DEL / 重输 ==="
Tap $C3 $R2Y "重输"
Input64
Shot "t04_before_del.png" "04_t04_del_retype"
Tap $C3 $R1Y "DEL once"
Start-Sleep -Milliseconds 500
Shot "t04_after_del_one.png" "04_t04_del_retype"
Input64
& $ADB -s $DEVICE shell input swipe $C3 $R1Y $C3 $R1Y 1200
Start-Sleep -Seconds 1
Shot "t04_after_long_del.png" "04_t04_del_retype"
Tap $C3 $R2Y "重输"
Start-Sleep -Milliseconds 500
Shot "t04_after_retype_clear.png" "04_t04_del_retype"

Write-Host "=== T06 scroll (try digit 2) ==="
FocusEdgeSearch
Tap $C1 $R1Y "2"
Start-Sleep -Milliseconds 500
Shot "t06_digit2_pinyin_list.png" "05_t06_scroll"
# try scroll left column
& $ADB -s $DEVICE shell input swipe $PINYIN_X 2700 $PINYIN_X 2300 400
Start-Sleep -Milliseconds 500
Shot "t06_after_scroll.png" "05_t06_scroll"

Write-Host "=== T07 9-key regression ==="
Tap $C3 $R2Y "clear"
Tap $C2 $R1Y "9"; Tap $C1 $R2Y "4"; Tap $C2 $R2Y "6"; Tap $C2 $R2Y "6"; Tap $C0 $R2Y "4"
Start-Sleep -Milliseconds 600
Shot "t07_94664_zhong.png" "06_t07_t9_regression"
Tap $C3 $R2Y "重输"
Tap $C3 $R3Y "0 space"
Start-Sleep -Milliseconds 500
Shot "t07_zero_space.png" "06_t07_t9_regression"
Tap $C0 $R1Y "1 symbol"
Start-Sleep -Milliseconds 500
Shot "t07_symbol_mode.png" "06_t07_t9_regression"
Tap $SYM_TOGGLE_X $BOTTOM_Y "9键 return"
Start-Sleep -Milliseconds 500
Shot "t07_back_t9.png" "06_t07_t9_regression"

Write-Host "=== T08 26-key smoke ==="
EnsureT9
$xml = Get-Content (Join-Path $TEST_DIR "ui_dumps/settings.xml") -Raw
if ($xml -match '切换为 9 键') {
    if ($xml -match 'keyboard_layout_toggle_button[^>]+bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        Tap ([int](([int]$Matches[1]+[int]$Matches[3])/2)) ([int](([int]$Matches[2]+[int]$Matches[4])/2)) "toggle 26-key"
    } else { Tap 720 1180 "toggle 26-key" }
    Start-Sleep 1
    & $ADB -s $DEVICE shell uiautomator dump /sdcard/settings26.xml | Out-Null
    & $ADB -s $DEVICE pull /sdcard/settings26.xml (Join-Path $TEST_DIR "ui_dumps/settings_26key.xml") 2>&1 | Out-Null
}
Shot "s02_26key_mode.png" "01_settings"
FocusEdgeSearch
Tap $N_X $ROW2_Y "n"
Tap $I_X $ROW2_Y "i"
Start-Sleep -Milliseconds 600
Shot "t08_ni_composing.png" "07_t08_26key_smoke"
Tap $SPACE_X $SPACE_Y "space commit"
Start-Sleep -Milliseconds 500
Shot "t08_ni_committed.png" "07_t08_26key_smoke"
Tap $KEY_123_X $BOTTOM_Y "123 symbol"
Start-Sleep -Milliseconds 500
Shot "t08_symbol_keyboard.png" "07_t08_26key_smoke"
Tap $KEY_123_X $BOTTOM_Y "ABC back"
Tap $C3 $R1Y "DEL"  # wrong - use delete on 26key
# DEL on 26-key row3 right
Tap 1292 2748 "DEL 26key"
Start-Sleep -Milliseconds 500
Shot "t08_after_del.png" "07_t08_26key_smoke"

Get-Content $MyInvocation.MyCommand.Path | Out-File -Encoding utf8 (Join-Path $TEST_DIR "artifacts/adb_commands.txt")
Write-Host "=== Done ==="
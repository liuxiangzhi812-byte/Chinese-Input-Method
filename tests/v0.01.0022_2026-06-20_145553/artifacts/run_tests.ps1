# v0.01.0022 device test runner
$ErrorActionPreference = "Continue"
$ADB = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$PKG = "com.mercury.chinesepinyinime"
$APK = "D:\Study\InputMethod\ChinesePinyinIME\app\build\outputs\apk\debug\app-debug.apk"
$TEST_DIR = "D:\Study\InputMethod\tests\v0.01.0022_2026-06-20_145553"
$DEVICE = "7fbf2094"

function Shot($name, $subdir) {
    $remote = "/sdcard/$name"
    $local = Join-Path $TEST_DIR "screenshots/$subdir/$name"
    & $ADB -s $DEVICE shell screencap -p $remote | Out-Null
    & $ADB -s $DEVICE pull $remote $local 2>&1 | Out-Null
    Write-Host "screenshot: $subdir/$name"
}

function Tap($x, $y, $label) {
    Write-Host "tap $label ($x,$y)"
    & $ADB -s $DEVICE shell input tap $x $y | Out-Null
    Start-Sleep -Milliseconds 600
}

function FocusSms() {
    & $ADB -s $DEVICE shell am force-stop com.android.mms | Out-Null
    Start-Sleep -Milliseconds 400
    & $ADB -s $DEVICE shell am start -a android.intent.action.SENDTO -d sms: | Out-Null
    Start-Sleep -Seconds 2
    Tap 720 2650 "sms body"
}

function GetPerfLog() {
    $out = & $ADB -s $DEVICE logcat -d -s PinyinDictPerf:I 2>&1
    return ($out | Select-String "load took")
}

function ColdStartPerf([int]$run) {
    & $ADB -s $DEVICE shell am force-stop $PKG | Out-Null
    Start-Sleep -Milliseconds 800
    & $ADB -s $DEVICE logcat -c | Out-Null
    FocusSms
    Start-Sleep -Seconds 4
    $line = GetPerfLog
    $line | Out-File -Append (Join-Path $TEST_DIR "artifacts/perf_results.txt")
    Write-Host "perf run $run : $line"
    Shot ("perf_run${run}.png") "02_dict_perf"
}

# T9 grid coords (OnePlus 7 Pro, from v0.01.0021)
$R1Y = 2364; $R2Y = 2556; $R3Y = 2748
$C0 = 198; $C1 = 546; $C2 = 894; $C3 = 1242
$SPACE_Y = 2940; $SPACE_X = 665

Write-Host "=== Install APK ==="
& $ADB -s $DEVICE install -r $APK

Write-Host "=== Baseline: settings page ==="
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 2
Shot "s01_main_activity.png" "01_baseline"

# Dump settings UI for toggle button bounds
& $ADB -s $DEVICE shell uiautomator dump /sdcard/settings_ui.xml | Out-Null
& $ADB -s $DEVICE pull /sdcard/settings_ui.xml (Join-Path $TEST_DIR "ui_dumps/settings_ui.xml") 2>&1 | Out-Null

Write-Host "=== Dictionary load performance (5 cold process runs) ==="
"" | Out-File (Join-Path $TEST_DIR "artifacts/perf_results.txt")
1..5 | ForEach-Object { ColdStartPerf $_ }

Write-Host "=== Switch to 9-key on settings page ==="
& $ADB -s $DEVICE shell am start -n "$PKG/.MainActivity" | Out-Null
Start-Sleep -Seconds 2
# toggle button center from typical layout ~720,650; calibrate via dump
Tap 720 1180 "toggle to 9-key"
Start-Sleep -Milliseconds 500
Shot "s02_t9_enabled.png" "01_baseline"

Write-Host "=== T9 pinyin choice bar tests ==="
FocusSms
Tap $C2 $R2Y "digit 6"
Tap $C0 $R1Y "digit 4"
Start-Sleep -Milliseconds 500
Shot "t01_digits_64_choice_bar.png" "03_t9_pinyin_choice"

# pinyin choice bar y ~ 2180; ni ~120, mi ~280 (estimate, calibrate from screenshot)
Tap 280 2180 "pinyin choice mi"
Start-Sleep -Milliseconds 500
Shot "t02_mi_selected.png" "03_t9_pinyin_choice"

Tap $C2 $R3Y "append digit 9"
Start-Sleep -Milliseconds 500
Shot "t03_after_append_649.png" "03_t9_pinyin_choice"

Tap $C3 $R2Y "clear all 重输"
Tap $C2 $R1Y "digit 9"
Tap $C1 $R2Y "digit 3"
Tap $C2 $R2Y "digit 6"
Start-Sleep -Milliseconds 500
Shot "t04_unambiguous_936.png" "03_t9_pinyin_choice"

Write-Host "=== Deferred T13: empty buffer 0 key inserts space ==="
Tap $C3 $R2Y "clear digits"
Start-Sleep -Milliseconds 300
Tap $C3 $R3Y "digit 0 / space"
Start-Sleep -Milliseconds 500
Shot "t05_empty_buffer_zero_space.png" "04_deferred"

Write-Host "=== Save adb commands log ==="
Get-Content $MyInvocation.MyCommand.Path | Out-File (Join-Path $TEST_DIR "artifacts/adb_commands.txt")

Write-Host "=== Done ==="
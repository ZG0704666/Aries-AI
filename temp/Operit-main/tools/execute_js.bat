@echo off
setlocal enabledelayedexpansion

REM Check parameters
if "%~2"=="" (
    echo Usage: %0 ^<JS_file_path^> ^<function_name^> [parameters_JSON]
    echo Example: %0 example.js hello_world "{\"name\":\"John\"}"
    exit /b 1
)

REM Parameters
set "FILE_PATH=%~1"
set "FUNCTION_NAME=%~2"
if "%~3"=="" (
    set "PARAMS={}"
) else (
    set "PARAMS=%~3"
)

REM Check if file exists
if not exist "%FILE_PATH%" (
    echo Error: File does not exist - %FILE_PATH%
    exit /b 1
)

REM Check ADB availability
adb version >nul 2>&1 || (
    echo Error: ADB command not found.
    echo Make sure Android SDK is installed and adb is in PATH
    exit /b 1
)

REM Device detection
set "DEVICE_SERIAL="
set "DEVICE_COUNT=0"
echo Checking connected devices...

REM Get valid devices list
for /f "skip=1 tokens=1,2" %%a in ('adb devices') do (
    if "%%b" == "device" (
        set /a DEVICE_COUNT+=1
        set "DEVICE_!DEVICE_COUNT!=%%a"
        echo [!DEVICE_COUNT!] %%a
    )
)

if %DEVICE_COUNT% equ 0 (
    echo Error: No authorized devices found
    exit /b 1
)

REM Device selection
if %DEVICE_COUNT% equ 1 (
    set "DEVICE_SERIAL=!DEVICE_1!"
    echo Using the only connected device: !DEVICE_SERIAL!
) else (
    :device_menu
    echo Multiple devices detected:
    for /l %%i in (1,1,%DEVICE_COUNT%) do echo   %%i. !DEVICE_%%i!
    set/p "CHOICE=Select device (1-%DEVICE_COUNT%): "
    
    REM Validate input
    echo !CHOICE!|findstr /r "^[1-9][0-9]*$" >nul || (
        echo Invalid input. Numbers only.
        goto :device_menu
    )
    set /a CHOICE=!CHOICE! >nul
    if !CHOICE! lss 1 (
        echo Number too small
        goto :device_menu
    )
    if !CHOICE! gtr %DEVICE_COUNT% (
        echo Number too large
        goto :device_menu
    )
    
    for %%i in (!CHOICE!) do set "DEVICE_SERIAL=!DEVICE_%%i!"
    echo Selected device: !DEVICE_SERIAL!
)

REM File operations
echo Creating directory structure...
adb -s "!DEVICE_SERIAL!" shell mkdir -p "/sdcard/Android/data/com.ai.assistance.operit/js_temp"

for %%F in ("%FILE_PATH%") do set "TARGET_FILE=/sdcard/Android/data/com.ai.assistance.operit/js_temp/%%~nxF"

echo Pushing [%FILE_PATH%] to device...
adb -s "!DEVICE_SERIAL!" push "%FILE_PATH%" "!TARGET_FILE!"
if errorlevel 1 (
    echo Error: Failed to push file
    exit /b 1
)

REM Escape JSON quotes
set "PARAMS=!PARAMS:"=\"!"

REM Execute JS function
echo Executing [!FUNCTION_NAME!] with params: !PARAMS!
adb -s "!DEVICE_SERIAL!" shell "am broadcast -a com.ai.assistance.operit.EXECUTE_JS -n com.ai.assistance.operit/.core.tools.javascript.ScriptExecutionReceiver --include-stopped-packages --es file_path '!TARGET_FILE!' --es function_name '!FUNCTION_NAME!' --es params '!PARAMS!' --ez temp_file true"

echo Waiting for execution to complete...
timeout /t 2 >nul

echo Capturing logcat output for JsEngine tag (Press Ctrl+C to stop):
adb -s "!DEVICE_SERIAL!" logcat -s JsEngine:*
@echo off
setlocal enabledelayedexpansion

set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "GRADLE_USER_HOME=D:\hub\Aries-AI\.gradle"
set "ANDROID_USER_HOME=D:\hub\Aries-AI\.android"
set "ANDROID_SDK_ROOT=C:\Users\xuany\AppData\Local\Android\Sdk"

cd /d "D:\hub\Aries-AI"
call gradlew.bat assembleDebug > build-output.txt 2>&1
echo Build completed with exit code: %ERRORLEVEL%
type build-output.txt

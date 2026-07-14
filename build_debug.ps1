$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = 'D:\hub\Aries-AI\.gradle'
$env:ANDROID_USER_HOME = 'D:\hub\Aries-AI\.android'
$env:ANDROID_SDK_ROOT = 'C:\Users\xuany\AppData\Local\Android\Sdk'
& '.\gradlew.bat' assembleDebug 2>&1 | Select-Object -Last 30

$adb = 'C:\Users\xuany\AppData\Local\Android\Sdk\platform-tools\adb.exe'
$pid = (& $adb shell pidof com.ai.phoneagent).Trim()
Write-Output "App PID: $pid"
$log = & $adb shell logcat -d -t 100 --pid=$pid 2>&1
$log | Select-Object -Last 40

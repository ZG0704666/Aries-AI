$adb = 'C:\Users\xuany\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb shell am start -n com.ai.phoneagent/.MainActivity
Start-Sleep 3
$pid = (& $adb shell pidof com.ai.phoneagent)
Write-Output "PID: $pid"
$log = & $adb shell logcat -d -t 200 --pid=$pid 2>&1
$crash = $log | Select-String 'FATAL|AndroidRuntime'
Write-Output "Crash lines: $($crash.Count)"
$crash | Select-Object -First 10

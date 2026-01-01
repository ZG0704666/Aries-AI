cd 'G:\Li Zexiang 251 Class\527Lab\Phone Agent\Open-AutoGLM-main'
# 通过 WiFi 连接, 改成手机显示的 IP 地址和端口
adb connect 192.168.3.106:37651

# 验证连接
adb devices
# 应显示：192.168.3.106:37651    device
# 列出所有已连接设备
adb devices

# 连接远程设备
adb connect 192.168.3.106:37651

# 指定设备执行任务
python main.py --base-url https://open.bigmodel.cn/api/paas/v4 --model "autoglm-phone" --apikey "03ec743d8d6740e0a30aaa604df09d40.oPMH5Ir85Ii2moj3" "打开铁路12306，帮我订一张到北京的火车票（直接一步到位最后付款界面）" 

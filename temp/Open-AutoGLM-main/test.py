from phone_agent import PhoneAgent
from phone_agent.model import ModelConfig

#无线连接校验（命令行输入）每次注意更换IP和端口号
'''
# 验证连接
adb devices
# 应显示：192.168.3.106:41879    device

# 列出所有已连接设备
adb devices

# 连接远程设备
adb connect 192.168.3.106:41879
'''

# Configure model
model_config = ModelConfig(
    base_url="https://open.bigmodel.cn/api/paas/v4",
    model_name="autoglm-phone",
    api_key="03ec743d8d6740e0a30aaa604df09d40.oPMH5Ir85Ii2moj3"
)

# 创建 Agent
agent = PhoneAgent(model_config=model_config)

# 执行任务（后续做成接口，用户语音输入后自动转文字）
result = agent.run("打开淘宝，搜索蓝牙耳机，选择销量最高的直接到最后的付款界面即可。")
print(result)
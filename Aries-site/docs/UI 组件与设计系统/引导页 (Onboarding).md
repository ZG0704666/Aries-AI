# 引导页 (Onboarding)

当前 Compose 引导实现在 `app/src/main/java/com/ai/phoneagent/ui/onboarding/OnboardingScreen.kt`，入口是 `OnboardingRoute`。导航支持 `onboarding` 和 `onboarding?flow={flow}`。

## 三种流程

| 参数 | 流程 |
|---|---|
| 默认或未知值 | ONBOARDING，完整新手引导 |
| view_only | VIEW_ONLY，从协议页面开始阅读 |
| permission_only | PERMISSION_ONLY，从权限页面开始 |

完整流程包含欢迎、协议和权限步骤。协议面板的 `accepted` 初始为 false，需要用户勾选后继续；只读模式不要求重新接受协议。不能把“已打开协议页”视为接受协议。

权限页根据当前设备状态展示操作入口，涉及无障碍、悬浮窗、麦克风及 Shizuku 等能力。返回应用后需刷新状态；权限页展示的状态不替代执行任务时的运行前检查。

页面复用 `AriesPageTopBar`、`AriesGlassCard` 和 `AriesGlassButton`。历史的 `MainOnboardingOverlay` 文件仍存在，排查时应从实际调用入口确认使用路径，而不是只按文件名判断。

相关：[环境与权限配置](../快速开始/环境与权限配置.md)。

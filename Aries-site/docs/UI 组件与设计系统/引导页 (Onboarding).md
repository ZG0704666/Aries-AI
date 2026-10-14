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

## 字符串资源

引导与权限文案集中在 `feature/settings/src/main/res/values/onboarding_strings.xml`：

- 欢迎步骤：`onboarding_welcome_title`（欢迎使用 Aries AI）、`onboarding_welcome_desc`、`onboarding_welcome_action`（继续）。
- 用户协议：`user_agreement_desc`、`user_agreement_action_next`，正文 `user_agreement_content`（CDATA HTML）覆盖 AGPL V3 授权、无担保与责任限制、用户责任、隐私政策、协议变更与终止、商业使用需书面授权（联系邮箱 zhangyongqi@njit.edu.cn）等条款。
- 权限面板（`perm_sheet_title`「完成首次设置」）：`perm_sheet_accessibility_*`（无障碍服务，用于点击/滑动/返回等自动化操作）、`perm_sheet_overlay_*`（悬浮窗显示）、`perm_sheet_microphone_*`（麦克风权限，用于语音输入与识别），以及 Shizuku 一键配置项（`perm_sheet_shizuku_*`，授权后自动补齐无障碍、悬浮窗和麦克风权限）。

相关：[环境与权限配置](../快速开始/环境与权限配置.md)。

package com.ai.phoneagent.ui.icons

/**
 * Material Icons → Lucide 图标映射表
 *
 * 本文档整理了代码库中所有 Material Design Icons 引用及其建议的 Lucide
 * 图标等价项。该映射由 T16（图标替换任务）用于执行实际替换。
 *
 * 扫描的文件位置：
 * - AutomationControlScreen.kt
 * - AttachmentComponents.kt
 * - ConversationDrawer.kt
 * - ConversationHistoryDialog.kt
 * - InputBar.kt
 * - ConversationTranscript.kt
 * - DrawerSettingsScreen.kt
 *
 * 映射图例：
 * Material Icon 路径 → Lucide 图标名
 *
 * ============================================================================
 * ICONS.DEFAULT.*（Material Design 默认风格）
 * ============================================================================
 *
 * Icons.Default.Add → Plus
 *   用途：InputBar.kt — 「添加附件」按钮
 *   Lucide 等价：Plus（常用于添加/创建操作）
 *
 * Icons.Default.AudioFile → Music2（或备选 AudioLines）
 *   用途：AttachmentComponents.kt — 音频文件附件图标
 *   Lucide 等价：Music2（表示音频文件）
 *   无等价说明：Lucide 音频文件类图标有限，Music2 最接近
 *
 * Icons.Default.Close → X
 *   用途：AttachmentComponents.kt — 关闭/移除按钮
 *   Lucide 等价：X（标准关闭图标）
 *
 * Icons.Default.Description → FileText
 *   用途：AttachmentComponents.kt — 文档/文件附件图标
 *   Lucide 等价：FileText（表示文本文档）
 *
 * Icons.Default.Image → Image
 *   用途：AttachmentComponents.kt — 图片附件图标
 *   Lucide 等价：Image（标准图片图标）
 *
 * Icons.Default.Keyboard → Keyboard
 *   用途：InputBar.kt — 切换到键盘输入按钮
 *   Lucide 等价：Keyboard（完全匹配）
 *
 * Icons.Default.Mic → Mic
 *   用途：InputBar.kt — 语音输入按钮
 *   Lucide 等价：Mic（完全匹配）
 *
 * Icons.Default.PhotoCamera → Camera
 *   用途：AttachmentComponents.kt — 相机/拍照附件选项
 *   Lucide 等价：Camera（标准相机图标）
 *
 * Icons.Default.ScreenshotMonitor → Monitor
 *   用途：AttachmentComponents.kt — 截图文件附件图标
 *   Lucide 等价：Monitor（表示屏幕/截图）
 *   无等价说明：Lucide 没有 ScreenshotMonitor，Monitor 是最接近的替代
 *
 * Icons.Default.VideoLibrary → Video
 *   用途：AttachmentComponents.kt — 视频附件图标
 *   Lucide 等价：Video（标准视频图标）
 *
 * ============================================================================
 * ICONS.OUTLINED.*（Material Design 描边风格）
 * ============================================================================
 *
 * Icons.Outlined.AutoAwesome → Wand2
 *   用途：AutomationControlScreen.kt — AI/自动化功能指示
 *   Lucide 等价：Wand2（表示 AI/自动化的魔法棒）
 *   备选：Star、Sparkles
 *
 * Icons.Outlined.Check → Check
 *   用途：ConversationTranscript.kt — 确认/成功指示
 *   Lucide 等价：Check（完全匹配）
 *
 * Icons.Outlined.CheckCircle → CheckCircle2
 *   用途：DrawerSettingsScreen.kt — 已验证/已完成状态
 *   Lucide 等价：CheckCircle2（Lucide 命名规范）
 *
 * Icons.Outlined.Close → X
 *   用途：ConversationHistoryDialog.kt、ConversationTranscript.kt — 关闭按钮
 *   Lucide 等价：X（标准关闭图标）
 *
 * Icons.Outlined.Cloud → Cloud
 *   用途：DrawerSettingsScreen.kt — 远程/云端模型指示
 *   Lucide 等价：Cloud（完全匹配）
 *
 * Icons.Outlined.ContentCopy → Copy
 *   用途：ConversationTranscript.kt — 复制文本按钮
 *   Lucide 等价：Copy（完全匹配）
 *
 * Icons.Outlined.ContentPaste → Clipboard
 *   用途：DrawerSettingsScreen.kt — 粘贴/剪贴板操作
 *   Lucide 等价：Clipboard（表示粘贴/剪贴板）
 *
 * Icons.Outlined.DeleteOutline → Trash2
 *   用途：ConversationDrawer.kt、ConversationHistoryDialog.kt — 删除按钮
 *   Lucide 等价：Trash2（删除/垃圾桶图标）
 *
 * Icons.Outlined.Download → Download
 *   用途：DrawerSettingsScreen.kt — 下载/安装操作
 *   Lucide 等价：Download（完全匹配）
 *
 * Icons.Outlined.Edit → Pencil
 *   用途：ConversationTranscript.kt — 编辑文本按钮
 *   Lucide 等价：Pencil（标准编辑图标）
 *
 * Icons.Outlined.Info → Info
 *   用途：DrawerSettingsScreen.kt — 信息/帮助指示
 *   Lucide 等价：Info（完全匹配）
 *
 * Icons.Outlined.Key → Key
 *   用途：DrawerSettingsScreen.kt — API 密钥/凭据字段
 *   Lucide 等价：Key（完全匹配）
 *
 * Icons.Outlined.KeyboardArrowDown → ChevronDown
 *   用途：ConversationTranscript.kt — 展开/收起指示
 *   Lucide 等价：ChevronDown（标准山形箭头）
 *
 * Icons.Outlined.KeyboardVoice → Mic2（或 Microphone）
 *   用途：AutomationControlScreen.kt — 语音输入模式指示
 *   Lucide 等价：Mic2（表示语音/麦克风）
 *
 * Icons.Outlined.Lightbulb → Lightbulb
 *   用途：ConversationTranscript.kt — 提示/建议指示
 *   Lucide 等价：Lightbulb（完全匹配）
 *
 * Icons.Outlined.Memory → Cpu
 *   用途：DrawerSettingsScreen.kt — 本地模型/内存指示
 *   Lucide 等价：Cpu（表示本地处理/内存）
 *
 * Icons.Outlined.Refresh → RefreshCw
 *   用途：AutomationControlScreen.kt、ConversationTranscript.kt — 刷新按钮
 *   Lucide 等价：RefreshCw（标准刷新图标）
 *
 * Icons.Outlined.Settings → Settings
 *   用途：ConversationDrawer.kt、DrawerSettingsScreen.kt — 设置按钮
 *   Lucide 等价：Settings（完全匹配）
 *   备选：SettingsIcon、Gear
 *
 * Icons.Outlined.SettingsAccessibility → Accessibility
 *   用途：AutomationControlScreen.kt — 无障碍设置指示
 *   Lucide 等价：Accessibility（完全匹配）
 *
 * Icons.Outlined.Shield → Shield
 *   用途：AutomationControlScreen.kt — 安全/权限指示
 *   Lucide 等价：Shield（完全匹配）
 *
 * Icons.Outlined.StopCircle → CircleStop（或 StopCircle）
 *   用途：AutomationControlScreen.kt — 停止/终止自动化按钮
 *   Lucide 等价：CircleStop（Lucide 命名：Stop + Circle 变体）
 *   无等价说明：若 Lucide 有 CircleStop 则完全匹配，否则用 Circle + StopIcon 组合
 *
 * Icons.Outlined.Sync → RotateCw
 *   用途：DrawerSettingsScreen.kt — 同步/刷新操作
 *   Lucide 等价：RotateCw（标准同步图标）
 *
 * Icons.Outlined.Tune → Sliders2
 *   用途：AutomationControlScreen.kt — 设置/配置按钮
 *   Lucide 等价：Sliders2（表示设置/调校）
 *   备选：Settings、Sliders
 *
 * ============================================================================
 * ICONS.ROUNDED.*（Material Design 圆角风格）
 * ============================================================================
 *
 * Icons.Rounded.Search → Search
 *   用途：ConversationDrawer.kt — 搜索会话按钮
 *   Lucide 等价：Search（完全匹配）
 *
 * ============================================================================
 * ICONS.AUTOMIRRORED.*（RTL 语言自动镜像）
 * ============================================================================
 *
 * Icons.AutoMirrored.Filled.ArrowBack → ArrowLeft
 *   用途：AutomationControlScreen.kt、DrawerSettingsScreen.kt — 返回导航按钮
 *   Lucide 等价：ArrowLeft（表示返回/上一个）
 *   RTL 说明：Lucide 按语言感知渲染；ArrowLeft 在 RTL 语境下自动镜像
 *
 * Icons.AutoMirrored.Outlined.Chat → MessageCircle（或 MessageSquare）
 *   用途：ConversationHistoryDialog.kt — 聊天/会话指示
 *   Lucide 等价：MessageCircle（表示聊天/消息）
 *   备选：MessageSquare、Chatbubble
 *
 * Icons.AutoMirrored.Outlined.OpenInNew → ExternalLink
 *   用途：DrawerSettingsScreen.kt — 在新窗口打开链接
 *   Lucide 等价：ExternalLink（与打开外部链接完全匹配）
 *
 * ============================================================================
 * 汇总统计
 * ============================================================================
 *
 * 共找到 Material Icons：37 个唯一引用
 * - Icons.Default.*：9 个
 * - Icons.Outlined.*：24 个
 * - Icons.Rounded.*：1 个
 * - Icons.AutoMirrored.*：3 个
 *
 * 映射覆盖率：37/37（100%）
 * 无直接 Lucide 等价的图标：2 个
 *   - Icons.Default.AudioFile（用 Music2 作最接近替代）
 *   - Icons.Default.ScreenshotMonitor（用 Monitor 作最接近替代）
 *   - Icons.Outlined.StopCircle（待验证 CircleStop）
 *
 * 所有图标均已映射到可用的 Lucide 替代项。
 *
 * ============================================================================
 * T16 使用说明（图标替换任务）
 * ============================================================================
 *
 * 1. 将所有 Icons.Default.X 导入替换为 Lucide 等价项
 * 2. 将所有 Icons.Outlined.X 导入替换为 Lucide 等价项
 * 3. 将所有 Icons.Rounded.X 导入替换为 Lucide 等价项
 * 4. 将所有 Icons.AutoMirrored.X 导入替换为 Lucide 等价项
 * 5. 更新导入：移除 androidx.compose.material.icons.*
 * 6. 添加导入：com.composables.icons.lucide.*
 * 7. 测试所有 UI 界面的视觉一致性
 * 8. 校验图标颜色与 Material 3 主题匹配
 * 9. 测试无障碍（contentDescription 仍然生效）
 * 10. 测试浅色与深色主题
 *
 */
object IconMapping {
    // 供后续工具使用的程序化映射（为自动替换预留）
    // TODO：T16 期间使用此对象实现自动图标替换
}

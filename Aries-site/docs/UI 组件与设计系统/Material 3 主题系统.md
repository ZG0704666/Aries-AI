# Material 3 主题系统

主题入口是 `core/designsystem/src/main/java/com/ai/phoneagent/core/designsystem/theme/AriesMaterialTheme.kt`。页面从 MaterialTheme 获取颜色、排版和形状，公共资源由 `:core:designsystem` 提供。

## 主题组成

- `AriesMaterialTheme` 解析明暗模式、颜色风格、动态色、AMOLED 深色和字体缩放。
- 默认字体族 `AriesFontFamily` 使用项目内置字体；调用方也可传入字体族。
- `AriesBackdrop` 通过 `LocalAriesBackdrop` 获取主题状态，提供公共页面背景。
- `ariesGlass` 修饰器与 `AriesGlassCard`、`AriesGlassButton` 提供复用的表面样式。
- `AriesPageTopBar` 位于 app 的 `ui/components` 中，用于任务、设置、更新与引导等页面的标题栏。

## 资源与维护

颜色与尺寸优先使用 `core/designsystem/src/main/res/values/` 下的 `m3t.xml`、`ui_v2.xml` 以及对应夜间资源。新增公共样式先定义 token，再由页面通过资源或 MaterialTheme 引用；用户可见文本放入字符串资源。

验证主题修改时覆盖浅色、深色、AMOLED、系统动态色、较大字体与键盘弹出。背景装饰不应妨碍正文对比度、触摸目标或滚动。

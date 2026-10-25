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

## Android 主题资源层（app 模块）

除 Compose 主题外，app 模块还有一套 XML 主题与传统资源 token（`app/src/main/res/values/themes.xml`，夜间为 `values-night/themes.xml`）：

- `Theme.Aries` / `Theme.Aries.App` / `Theme.Aries.TransparentLaunch` 均映射到 `Theme.M3t.*` 系列父主题；`Theme.PhoneAgent` 继承 `Theme.M3t.App`。明暗两个资源目录中主题名一致，由系统夜间模式自动切换。
- 对话框与底部弹窗样式：`BlueGlassAlertDialog`（父 `Theme.M3t.Dialog`）与 `RoundedBottomSheetDialog`（父 `Theme.M3t.BottomSheet`）。
- 传统颜色 token 位于 `app/src/main/res/values/colors.xml`：核心品牌色 `blue_glass_primary`（#6BA8FF）、`blue_glass_primary_variant`（#4F8EF5）、`blue_glass_primary_dark`（#3D7DD9），强调色 `teal_accent`（#26A69A）、`purple_accent`（#7E57C2）、`orange_accent`（#FF7043），以及文本层级 `text_primary`/`text_secondary`/`text_tertiary`/`text_disabled`、语义状态色（success/warning/error/info）与暗色模式色。
- `app/src/main/res/values/m3t.xml`（对应 `values-night/m3t.xml`）定义 `m3t_*` 前缀的颜色、尺寸与 `TextAppearance.M3t.*` 文字样式（如 `m3t_drawer_*`、`m3t_input_bar_*`、`m3t_message_*` 等）。

Compose 层依赖由 `gradle/libs.versions.toml` 统一管理：`composeBom = "2024.12.01"`（material3 由 BoM 管理版本）、`material = "1.13.0"`（传统 View 体系 Material 组件）。

## 验证清单

验证主题修改时覆盖浅色、深色、AMOLED、系统动态色、较大字体与键盘弹出。背景装饰不应妨碍正文对比度、触摸目标或滚动。

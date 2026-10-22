# Material 3 主题系统

主题入口是 `core/designsystem/src/main/java/com/ai/phoneagent/core/designsystem/theme/AriesMaterialTheme.kt`。页面从 MaterialTheme 获取颜色、排版和形状，公共资源由 `:core:designsystem` 提供。

## 主题组成

- `AriesMaterialTheme` 解析明暗模式、颜色风格、动态色、AMOLED 深色和字体缩放。
- 默认字体族 `AriesFontFamily` 使用项目内置字体；调用方也可传入字体族。
- `AriesBackdrop` 通过 `LocalAriesBackdrop` 获取主题状态，提供公共页面背景。
- `ariesGlass` 修饰器与 `AriesGlassCard`、`AriesGlassButton` 提供复用的表面样式。
- `AriesPageTopBar` 位于 app 的 `ui/components` 中，用于任务、设置、更新与引导等页面的标题栏。

## 液态玻璃

`AriesGlass.kt` 的 `Modifier.ariesGlass(shape, selected, elevated)` 是全 App 统一的玻璃入口：

- 亮色：玻璃底色为淡冷灰（`aries_glass_light`），叠加一根 1dp 内描边（左上高光 → 右下暗部的渐变）与柔和外投影，避免在浅色背景上与背景混成一片。
- 暗色：底色大幅降低不透明度（`aries_glass_dark`），并提升采样背景的对比度与饱和度，保留背景色彩层次，再叠加折射、镜面高光与内侧暗角。
- 折射使用 `lens(refractionHeight, refractionAmount, depthEffect = true)`，仅在 API 33+ 且外形为圆角矩形时生效；`Highlight.Default` 在 API 33+ 使用运行时着色器，低版本回退为普通描边高光。
- `AriesBackdrop` 在亮色下也会绘制极淡光晕，让玻璃有可折射的明度变化；深色保留原有强光晕，AMOLED 纯黑模式下不绘制。
- 没有可用背景层（API 30、独立窗口）时回退到静态渐变玻璃，保留描边与外侧羽化阴影。
- `elevated = false` 可关闭外投影，用于列表中大量气泡等高频元素以节省图层。

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

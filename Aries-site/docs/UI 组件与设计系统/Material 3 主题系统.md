# 设计系统、图标与资源

本页汇总 Material 3 主题、设计 token、图标、响应式尺寸、动画资源和附件展示组件的资源入口。

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

---

## 图标与资源

Compose 界面使用 `com.composables.icons.lucide` 图标。依赖由 `gradle/libs.versions.toml` 中的 `compose-icons-lucide` 管理，当前版本为 1.1.0。

### 当前使用方式

主页标签使用 History、House、Settings；附件面板使用 Camera、Image、FileText、Monitor、Music2、Video、X 等。图标通过 Compose `Icon` 绘制，颜色和尺寸从主题及公共资源获取。

`app/src/main/java/com/ai/phoneagent/ui/icons/IconMapping.kt` 是 Material 到 Lucide 的迁移映射说明，不是运行时自动替换器。实际图标由各组件的 import 和 Icon 调用决定。

### 新增图标

- 优先复用 Lucide 中符合动作语义的图标。
- 纯装饰图标可使用空 contentDescription；独立操作按钮应有可读的本地化说明。
- 图标旁有标签时，确保整个控件的语义和触摸区域清晰。
- 明暗主题统一使用 MaterialTheme 或资源 token，不单独硬编码颜色。

官网图标数据在 `Aries-site/site/src/data/apps.json` 维护，静态图标位于 `Aries-site/site/public/icons/`；这与 Android Compose 图标依赖是不同实现。

### XML 矢量图标与启动图标

除 Lucide 外，`app/src/main/res/drawable/` 下还有一组 24dp XML 矢量图标（`ic_add_24.xml`、`ic_close_24.xml`、`ic_history_24.xml`、`ic_floating_window_24.xml`、`ic_home_24.xml` 等），颜色引用主题 token（如 `@color/m3t_primary`、`@color/m3t_on_surface_variant`），随明暗主题自动切换。菜单（如 `top_app_bar.xml` 的 `action_history`）即使用这些矢量资源。

启动图标为自适应图标（API 26+，`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` 与 `ic_launcher_round.xml`）：background 使用透明资源，foreground 使用彩色 Aries AI 标志，monochrome 使用透明底单色标志，支持主题化单色图标。

### 背景形状资源

- `bg_circle_glass.xml`：oval 实色，引用 `@color/m3t_circle_glass_bg`（玻璃圆形背景）。
- `bg_circle_light.xml`：oval 实色，引用 `@color/m3t_surface_container`。
- `bg_bottom_sheet_white.xml`：顶部 32dp 圆角矩形，实色 `@color/m3t_surface`，用于底部弹窗背景。
- 状态圆形 `bg_circle_green.xml`/`bg_circle_red.xml`/`bg_circle_yellow.xml`；推荐卡渐变 `bg_recommend_gradient.xml`（45° 线性渐变 `m3t_recommend_grad_start` → `m3t_recommend_grad_end`，圆角 `@dimen/m3t_radius_md`）与 `bg_recommend_inner.xml`（实色 `@color/m3t_surface`）。

### 响应式尺寸

`values-sw360dp/dimens.xml` 与 `values-sw600dp/dimens.xml` 按"最小宽度"限定符提供两套布局尺寸：小屏（page_padding 12dp、button_height 44dp、app_icon_size 64dp）与大屏/平板（24dp/56dp/96dp，card_corner 28dp）。

### 动画资源

`app/src/main/res/anim/` 提供 `m3t_slide_in_left.xml` / `m3t_slide_in_right.xml`（280ms 平移，fast_out_slow_in 插值）、`button_scale_up.xml` / `button_scale_down.xml`（按压缩放）与 `fade_in.xml` / `fade_out.xml` 等过渡动画。

---

## 附件组件

附件入口和预览组件位于 `app/src/main/java/com/ai/phoneagent/ui/components/AttachmentComponents.kt`，处理逻辑位于 `helper/AttachmentManager.kt`，附件元数据由 `data/AttachmentInfo.kt` 定义。

### 处理链路

1. `AttachmentSelectorPanel` 展示选择入口，使用 Activity Result 合约获取图片或文件 URI。
2. 图片可使用系统 Photo Picker；通用图片/文件路径使用多文件选择合约。
3. 选中 URI 交给 `AttachmentManager.handleAttachment` 异步处理，再用于消息附件展示。
4. 拍照路径需要相机权限与 FileProvider URI；应检查用户取消和失败返回。
5. `AttachmentParser` 识别消息中的 `<attachment .../>` 引用，提取 id、filename、type、size 和可选 content，也支持从正文移除引用标签。

附件入口存在，不代表每个模型都能直接理解对应媒体。发送内容取决于模型能力、解析结果和请求构造；文件 URI 也不能直接当作可永久读取的普通路径。

### 维护入口

- `MainActivity.setupAriesAttachment`：Activity 级附件选择与拍照回调
- `helper/AttachmentManager.kt`：附件处理
- `helper/AttachmentParser.kt`：消息引用解析
- `ui/components/AttachmentComponents.kt`：选择面板和文件展示

相关：[消息转录与卡片系统](../对话系统/消息转录与卡片系统.md)。

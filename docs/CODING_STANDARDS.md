# Phone Agent 代码规范

> 本文档定义Phone Agent项目的代码编写规范，确保团队代码风格一致性和可维护性。

---

## 📋 目录

- [一、Kotlin代码规范](#一kotlin代码规范)
- [二、Android资源规范](#二android资源规范)
- [三、Git提交规范](#三git提交规范)
- [四、测试规范](#四测试规范)
- [五、文档规范](#五文档规范)

---

## 一、Kotlin代码规范

### 1.1 命名规范

#### 1.1.1 类和接口命名

```kotlin
// ✅ 正确：PascalCase
class PhoneAgentService { }
interface IToolExecutor { }
data class ScreenshotData { }
object AppPackageManager { }

// ❌ 错误：小写开头
class phoneAgentService { }
interface iToolExecutor { }
```

#### 1.1.2 函数和变量命名

```kotlin
// ✅ 正确：camelCase
fun getUiHierarchy() { }
val screenshotCache = ScreenshotCache()
var lastScreenshotTime = 0L

// ❌ 错误：PascalCase
fun GetUiHierarchy() { }
val ScreenshotCache = ScreenshotCache()
```

#### 1.1.3 常量命名

```kotlin
// ✅ 正确：UPPER_SNAKE_CASE
const val MAX_CACHE_SIZE = 3
const val SCREENSHOT_QUALITY = 75
const val DEFAULT_TIMEOUT = 5000L

// ❌ 错误：小写或驼峰
const val maxCacheSize = 3
const val screenshotQuality = 75
```

#### 1.1.4 私有属性命名

```kotlin
// ✅ 正确：下划线开头
private val _context: Context
private val _isBound = false

// ❌ 错误：无下划线
private val context: Context
private val isBound = false
```

### 1.2 文件注释规范

#### 1.2.1 类注释（必须）

```kotlin
/**
 * UI层次结构管理器
 * 负责与无障碍服务通信，获取UI树
 * 
 * @author 张三
 * @since 2026-01-09
 * @see UIHierarchyManager
 */
class UIHierarchyManager {
    // ...
}
```

#### 1.2.2 函数注释（复杂函数必须）

```kotlin
/**
 * 获取UI层次结构
 * 
 * @param format 输出格式(xml/json)
 * @param detail 详细程度(minimal/summary/full)
 * @return UI树字符串
 * @throws IllegalStateException 当无障碍服务未启用时
 */
suspend fun getUiHierarchy(
    format: String = "xml",
    detail: String = "summary"
): String {
    // ...
}
```

#### 1.2.3 行内注释（关键逻辑必须）

```kotlin
if (shouldTakeScreenshot()) {
    // 节流：避免频繁截图导致性能下降
    return cachedScreenshot
}

// 检查缓存是否过期
if (System.currentTimeMillis() - timestamp > TTL) {
    cache.remove(key)
}
```

### 1.3 代码格式规范

#### 1.3.1 缩进

```kotlin
// 使用4个空格缩进
class PhoneAgentService {
    private val cache = ScreenshotCache()
    
    fun execute() {
        val result = cache.get("key")
        return result
    }
}
```

#### 1.3.2 大括号

```kotlin
// ✅ 正确：左大括号不换行
if (condition) {
    doSomething()
}

// ❌ 错误：左大括号换行
if (condition)
{
    doSomething()
}
```

#### 1.3.3 空行

```kotlin
// ✅ 正确：适当使用空行
class PhoneAgentService {
    private val cache = ScreenshotCache()
    
    fun execute() {
        val result = cache.get("key")
        
        if (result != null) {
            return result
        }
        
        return defaultValue
    }
}

// ❌ 错误：过多空行
class PhoneAgentService {
    private val cache = ScreenshotCache()
    
    
    fun execute() {
        val result = cache.get("key")
        
        
        if (result != null) {
            return result
        }
        
        
        return defaultValue
    }
}
```

### 1.4 异常处理规范

#### 1.4.1 异常捕获

```kotlin
// ✅ 正确：捕获特定异常
try {
    val result = service.getUiHierarchy()
    return result
} catch (e: AccessibilityServiceException) {
    AppLogger.e(TAG, "无障碍服务异常", e)
    return null
}

// ❌ 错误：捕获所有异常
try {
    val result = service.getUiHierarchy()
    return result
} catch (e: Exception) {
    AppLogger.e(TAG, "异常", e)
    return null
}
```

#### 1.4.2 异常抛出

```kotlin
// ✅ 正确：抛出具体异常
fun getUiHierarchy(): String {
    val service = PhoneAgentAccessibilityService.instance
        ?: throw IllegalStateException("无障碍服务未启用")
    
    return service.dumpUiTree()
}

// ❌ 错误：抛出通用异常
fun getUiHierarchy(): String {
    val service = PhoneAgentAccessibilityService.instance
        ?: throw Exception("无障碍服务未启用")
    
    return service.dumpUiTree()
}
```

### 1.5 协程使用规范

#### 1.5.1 协程作用域

```kotlin
// ✅ 正确：使用viewModelScope
class AutomationViewModel : ViewModel() {
    fun startAutomation() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                service.executeAction()
            }
            _uiState.value = result
        }
    }
}

// ❌ 错误：使用GlobalScope（除非特殊场景）
class AutomationViewModel : ViewModel() {
    fun startAutomation() {
        GlobalScope.launch {
            val result = service.executeAction()
            _uiState.value = result
        }
    }
}
```

#### 1.5.2 协程上下文切换

```kotlin
// ✅ 正确：明确切换上下文
suspend fun executeAction(): Result<String> {
    return withContext(Dispatchers.IO) {
        // IO操作
        val result = networkCall()
        
        withContext(Dispatchers.Main) {
            // UI更新
            updateUI(result)
        }
        
        Result.success(result)
    }
}
```

---

## 二、Android资源规范

### 2.1 布局文件命名

```xml
<!-- ✅ 正确：小写+下划线 -->
activity_main.xml
fragment_automation.xml
item_screenshot.xml
dialog_permission.xml

<!-- ❌ 错误：大写或驼峰 -->
ActivityMain.xml
FragmentAutomation.xml
ItemScreenshot.xml
```

### 2.2 ID命名规范

```xml
<!-- ✅ 正确：前缀+下划线+驼峰 -->
<LinearLayout
    <Button
        android:id="@+id/btn_submit"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/btn_submit" />
    
    <EditText
        android:id="@+id/et_search"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:hint="@string/hint_search" />
    
    <TextView
        android:id="@+id/tv_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/tv_title" />
</LinearLayout>

<!-- ❌ 错误：无前缀或驼峰 -->
<LinearLayout>
    <Button
        android:id="@+id/BtnSubmit"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="@string/BtnSubmit" />
</LinearLayout>
```

### 2.3 字符串资源命名

```xml
<!-- ✅ 正确：前缀+下划线+驼峰 -->
<resources>
    <string name="btn_submit">提交</string>
    <string name="et_search_hint">搜索</string>
    <string name="tv_title">标题</string>
    <string name="msg_success">操作成功</string>
    <string name="error_network">网络错误</string>
</resources>

<!-- ❌ 错误：无前缀或大写 -->
<resources>
    <string name="Submit">提交</string>
    <string name="SearchHint">搜索</string>
    <string name="Title">标题</string>
</resources>
```

### 2.4 颜色资源命名

```xml
<!-- ✅ 正确：前缀+下划线+驼峰 -->
<resources>
    <color name="color_primary">#6200EE</color>
    <color name="color_secondary">#03DAC6</color>
    <color name="color_success">#4CAF50</color>
    <color name="color_error">#F44336</color>
    <color name="color_background">#FFFFFF</color>
</resources>

<!-- ❌ 错误：无前缀或大写 -->
<resources>
    <color name="Primary">#6200EE</color>
    <color name="Secondary">#03DAC6</color>
</resources>
```

### 2.5 尺寸资源命名

```xml
<!-- ✅ 正确：前缀+下划线+驼峰 -->
<resources>
    <dimen name="margin_small">8dp</dimen>
    <dimen name="margin_medium">16dp</dimen>
    <dimen name="margin_large">24dp</dimen>
    <dimen name="text_size_small">14sp</dimen>
    <dimen name="text_size_medium">16sp</dimen>
    <dimen name="text_size_large">18sp</dimen>
</resources>

<!-- ❌ 错误：无前缀或大写 -->
<resources>
    <dimen name="Small">8dp</dimen>
    <dimen name="Medium">16dp</dimen>
</resources>
```

---

## 三、Git提交规范

### 3.1 提交信息格式

```bash
# 标准格式
<type>(<scope>): <subject>

<body>

<footer>
```

#### 3.1.1 类型（type）

| 类型 | 说明 | 示例 |
|------|------|------|
| feat | 新功能 | feat(tool): 新增get_page_info工具 |
| fix | 修复bug | fix(ui): 修复UI树解析失败 |
| perf | 性能优化 | perf(cache): 优化截图缓存策略 |
| refactor | 重构代码 | refactor(service): 重构无障碍服务 |
| docs | 文档更新 | docs(readme): 更新README |
| test | 测试相关 | test(unit): 添加单元测试 |
| chore | 构建/工具链 | chore(deps): 更新依赖版本 |

#### 3.1.2 范围（scope）

| 范围 | 说明 | 示例 |
|------|------|------|
| tool | 工具相关 | feat(tool): 新增工具 |
| ui | UI相关 | fix(ui): 修复布局问题 |
| service | 服务相关 | perf(service): 优化服务性能 |
| cache | 缓存相关 | feat(cache): 新增缓存机制 |
| agent | Agent相关 | refactor(agent): 重构Agent逻辑 |
| config | 配置相关 | chore(config): 更新配置 |

#### 3.1.3 主题（subject）

```bash
# ✅ 正确：简洁明了，不超过50字符
feat(tool): 新增get_page_info工具

# ❌ 错误：过长或模糊
feat(tool): 新增get_page_info工具用于获取页面信息包括package和activity和UI树支持xml和json格式
```

#### 3.1.4 正文（body）

```bash
# ✅ 正确：详细说明变更内容
feat(tool): 新增get_page_info工具

- 支持获取页面信息(package+activity+UI树)
- 支持format参数(xml/json)
- 支持detail参数(minimal/summary/full)
- 对齐Operit工具接口

Closes #001

# ❌ 错误：无详细说明
feat(tool): 新增get_page_info工具

Closes #001
```

#### 3.1.5 页脚（footer）

```bash
# ✅ 正确：关联Issue或PR
Closes #001
Related to #002
Refs #003

# ❌ 错误：无关联信息
```

### 3.2 提交示例

```bash
# 功能开发
git add .
git commit -m "feat(tool): 新增click_element工具-张三

- 支持resourceId/text/className/index点击
- selector优先，坐标兜底
- 支持模糊匹配(partialMatch)
- 对齐Operit工具接口

Closes #005"

# Bug修复
git add .
git commit -m "fix(ui): 修复UI树解析失败-李四

- XML格式不兼容，调整解析器
- 添加异常处理
- 增加单元测试

Fixes #003"

# 性能优化
git add .
git commit -m "perf(cache): 优化截图缓存策略-王五

- 调整TTL从2秒到1.5秒
- 增加LRU淘汰策略
- 缓存命中率从20%提升到35%

Related to #013"
```

### 3.3 分支命名规范

```bash
# 功能分支
feature/xxx-开发者名
feature/ui-tree-张三
feature/tool-click-element-李四
feature/perf-cache-王五

# 修复分支
fix/xxx-开发者名
fix/ui-parse-error-张三
fix/cache-bug-李四

# 热修复分支
hotfix/xxx-开发者名
hotfix/crash-fix-张三
hotfix/memory-leak-李四
```

---

## 四、测试规范

### 4.1 单元测试规范

#### 4.1.1 测试类命名

```kotlin
// ✅ 正确：类名+Test
class ScreenshotCacheTest { }
class PhoneAgentServiceTest { }
class ToolRegistrationTest { }

// ❌ 错误：Test前缀
class TestScreenshotCache { }
class TestPhoneAgentService { }
```

#### 4.1.2 测试方法命名

```kotlin
// ✅ 正确：test + 方法名
@Test
fun `test cache hit`() { }

@Test
fun `test cache eviction`() { }

@Test
fun `test cache expiration`() { }

// ❌ 错误：无test前缀
@Test
fun `cache hit`() { }

@Test
fun `cache eviction`() { }
```

#### 4.1.3 测试示例

```kotlin
class ScreenshotCacheTest {
    private lateinit var cache: ScreenshotCache
    
    @Before
    fun setup() {
        cache = ScreenshotCache(maxSize = 3)
    }
    
    @Test
    fun `test cache hit`() {
        // Given
        val data = ScreenshotData("base64", System.currentTimeMillis(), "hash")
        cache.put("key1", data)
        
        // When
        val result = cache.get("key1")
        
        // Then
        assertNotNull(result)
        assertEquals("base64", result.base64)
    }
    
    @Test
    fun `test cache eviction`() {
        // Given
        val data1 = ScreenshotData("base64_1", System.currentTimeMillis(), "hash1")
        val data2 = ScreenshotData("base64_2", System.currentTimeMillis(), "hash2")
        val data3 = ScreenshotData("base64_3", System.currentTimeMillis(), "hash3")
        
        cache.put("key1", data1)
        cache.put("key2", data2)
        cache.put("key3", data3)
        
        // When
        val result1 = cache.get("key1")
        val result2 = cache.get("key2")
        val result3 = cache.get("key3")
        
        // Then
        assertNull(result1) // 应该被淘汰
        assertNotNull(result2)
        assertNotNull(result3)
    }
    
    @Test
    fun `test cache expiration`() {
        // Given
        val oldData = ScreenshotData("old", System.currentTimeMillis() - 3000, "hash")
        val newData = ScreenshotData("new", System.currentTimeMillis(), "hash")
        
        cache.put("key", oldData)
        Thread.sleep(2500) // 等待超过TTL
        
        // When
        val result = cache.get("key")
        
        // Then
        assertNull(result) // 应该过期
    }
}
```

### 4.2 集成测试规范

```kotlin
@RunWith(AndroidJUnit4::class)
@LargeTest
class AutomationFlowTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(AutomationActivity::class.java)
    
    @Test
    fun `test complete automation flow`() {
        // Given
        val scenario = activityRule.scenario
        scenario.moveToState(Lifecycle.State.RESUMED)
        
        // When
        onView(withId(R.id.btn_launch)).perform(click())
        
        // Then
        onView(withId(R.id.btn_search)).check(matches(isDisplayed()))
        
        // When
        onView(withId(R.id.et_search)).perform(typeText("测试"))
        
        // Then
        onView(withId(R.id.btn_submit)).perform(click())
        
        // Verify
        onView(withText("成功")).check(matches(isDisplayed()))
    }
}
```

### 4.3 测试覆盖率要求

| 模块 | 最低覆盖率 | 推荐覆盖率 |
|------|-----------|-----------|
| 核心模块（Service、Agent） | 70% | 80% |
| 工具模块（Tools） | 60% | 70% |
| UI模块（Activity、Fragment） | 50% | 60% |
| 工具类（Utils） | 40% | 50% |

运行测试覆盖率：
```bash
# Windows
.\gradlew jacocoTestReport

# Linux/Mac
./gradlew jacocoTestReport
```

---

## 五、文档规范

### 5.1 代码文档

#### 5.1.1 公共API文档

```kotlin
/**
 * 获取UI层次结构
 * 
 * 支持XML和JSON两种格式输出
 * 
 * @param format 输出格式，可选值：xml, json，默认xml
 * @param detail 详细程度，可选值：minimal, summary, full，默认summary
 * @return UI树字符串，格式取决于format参数
 * 
 * @throws IllegalStateException 当无障碍服务未启用时抛出
 * 
 * @see UIHierarchyManager
 * @since 1.0.0
 */
suspend fun getUiHierarchy(
    format: String = "xml",
    detail: String = "summary"
): String
```

#### 5.1.2 复杂逻辑文档

```kotlin
/**
 * 执行智能等待策略
 * 
 * 根据动作类型动态调整等待时间：
 * - launch: 500ms - 应用启动需要较长时间
 * - tap/click: 100ms - 点击操作响应快
 * - type/input: 200ms - 输入操作需要等待
 * - swipe/scroll: 300ms - 滑动操作需要动画时间
 * - back/home: 150ms - 系统按键响应快
 * - long_press: 400ms - 长按需要等待
 * - double_tap: 150ms - 双击需要快速响应
 * 
 * @param actionName 动作名称
 * @return 等待时间（毫秒）
 */
private fun getActionDelay(actionName: String): Long {
    return when (actionName) {
        "launch" -> 500L
        "tap", "click" -> 100L
        "type", "input" -> 200L
        "swipe", "scroll" -> 300L
        "back" -> 150L
        "home" -> 200L
        "long_press" -> 400L
        "double_tap" -> 150L
        else -> 200L
    }
}
```

### 5.2 README文档

#### 5.2.1 项目概述

```markdown
# Phone Agent

基于安卓无障碍功能的AI手机自动化助手，通过智谱AI模型实现智能任务执行。

## 特性

- ✅ 智能UI理解：通过视觉语言模型理解屏幕内容
- ✅ 自动化操作：支持点击、滑动、输入等操作
- ✅ 多应用支持：支持微信、淘宝、美团等100+应用
- ✅ 性能优化：截图缓存、智能节流、流式响应
- ✅ 工具系统：25+工具，支持灵活扩展

## 快速开始

\`\`\`bash
# 1. 克隆项目
git clone https://github.com/your-org/phone-agent.git
cd phone-agent

# 2. 安装依赖
./gradlew build

# 3. 运行应用
./gradlew installDebug
\`\`\`

## 开发指南

详见 [BUILDING.md](./BUILDING.md)

## 代码规范

详见 [CODING_STANDARDS.md](docs/CODING_STANDARDS.md)

## 贡献指南

详见 [GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md)
```

---

## 📋 检查清单

在提交代码前，请确认：

### 代码质量
- [ ] 代码符合命名规范
- [ ] 公共API有完整注释
- [ ] 复杂逻辑有详细说明
- [ ] 异常处理完善
- [ ] 无硬编码（除常量）
- [ ] 无调试代码（System.out.println等）

### 测试要求
- [ ] 新功能有单元测试
- [ ] 测试覆盖率≥70%
- [ ] 测试通过
- [ ] 无测试失败

### 文档要求
- [ ] 公共API有文档
- [ ] README已更新
- [ ] 变更说明完整

### Git要求
- [ ] 提交信息符合规范
- [ ] 分支命名正确
- [ ] 无敏感信息提交
- [ ] 合并前已更新文档

---

**文档版本**：v1.3
**最后更新**：2026-02-28
**维护人**：ZG0704666
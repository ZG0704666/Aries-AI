# Checklist

## Phase 1：安全加固（P0）

- [x] `FilePathValidator` 工具类已创建，实现路径规范化、穿越检测、白名单校验
- [x] `FileToolExecutor` 的所有文件操作（read_file、write_file、delete、move、compress）已接入路径校验
- [x] 尝试访问系统敏感路径时操作被正确拒绝
- [x] 尝试路径穿越攻击时操作被正确拒绝
- [x] `NetworkSecurityValidator` 工具类已创建，实现域名白名单和内网 IP 段过滤
- [x] `NetworkToolExecutor` 的 http_get、http_post 已接入安全校验
- [x] 尝试访问内网 IP 地址时请求被正确拒绝
- [x] `write_file` 的 `dangerCheck` 已改为 `true`
- [x] `delete`、`move`、`compress` 的 `dangerCheck` 已改为 `true`
- [x] 权限系统正确拦截标记为危险的操作
- [x] `LogMaskingUtil` 工具类已创建，实现 API Key 脱敏
- [x] `AutoGlmClient` 的日志输出已接入脱敏处理
- [x] 脱敏后的 API Key 格式为 `前4位****后4位`
- [x] `FilePathValidator` 单元测试已编写并通过
- [x] `NetworkSecurityValidator` 单元测试已编写并通过
- [x] `LogMaskingUtil` 单元测试已编写并通过

## Phase 2：架构优化（P1）

- [x] `NetworkModule` 中已配置统一的 OkHttpClient（连接池、超时、日志拦截器）
- [x] `NetworkModule` 中已配置 fast OkHttpClient（短超时，用于自动化场景）
- [x] `AutoGlmClient` 中的 `SharedHttpClient` 已移除，改为通过 Koin 获取
- [x] `NetworkToolExecutor` 中的独立 client 已移除，改为通过 Koin 获取
- [x] 所有网络请求使用共享 OkHttpClient 实例
- [x] `UiModule` 中 `ReleaseRepository()` 直接实例化已改为 Koin 注入
- [x] `AppModule` 中 `ReleaseRepository` 已注册为 Koin 单例
- [x] `ActionType` 枚举已创建，定义动作类型分类
- [x] `ActionExecutorRouter` 已创建，作为路由层委托给 ActionExecutor
- [x] 原有 `ActionExecutor` 功能完整保留，无功能丢失

## Phase 3：测试增强（P1-P2）

- [x] `AIToolHandler` 单元测试已编写（20 个测试，覆盖注册、注销、执行、危险操作检查）
- [x] `ToolPermissionSystem` 单元测试已编写（16 个测试，覆盖 PermissionLevel 枚举和类结构）
- [x] `ActionType` 单元测试已编写（23 个测试，覆盖所有动作名称解析）
- [x] `ActionExecutorRouter` 单元测试已编写（8 个测试，覆盖路由和委托逻辑）
- [x] `FilePathValidator` 测试已补充（18 个测试，覆盖路径穿越、符号链接等）
- [x] `NetworkSecurityValidator` 测试已补充（38 个测试，覆盖 IPv4 边界、端口、URL 编码）
- [x] `CacheManager` 单元测试已编写（5 个测试，覆盖注册、清理、内存压力）
- [x] 配置 `testOptions.unitTests.isReturnDefaultValues = true` 解决 Android Log 模拟问题
- [x] 修复 `CoreModuleTest` 中 2 处预先存在的断言失效
- [x] 所有单元测试通过 `./gradlew testDebugUnitTest`（247 个测试全部通过）

## Phase 4：长期优化（P2-P3）

- [x] `AIToolHandler` 已是 class（private constructor + getInstance），无需重构
- [x] `ToolPermissionSystem` 已是 class（private constructor + getInstance），无需重构
- [ ] `AppPackageManager` 从 object 改为 class（推迟：影响面广，需后续处理）
- [ ] `FileToolExecutor` 从 object 改为 class（推迟：影响面广，需后续处理）
- [ ] `NetworkToolExecutor` 从 object 改为 class（推迟：影响面广，需后续处理）
- [x] `CacheManager` 统一管理器已创建，实现 `ComponentCallbacks2` 内存压力监听
- [x] `AppPackageManager.appCache` 已设置最大容量限制（512 条 LRU）
- [x] `AppPackageManager` 已实现 `EvictableCache` 接口
- [x] `ScreenshotCache` 已接入 `CacheManager`（实现 EvictableCache 接口）
- [x] 内存压力触发清理机制已实现（onTrimMemory 分级清理 + onLowMemory 紧急清理）
- [x] `CacheManager` 已在 `AriesAgentApp.onCreate` 中初始化
- [x] `highPriorityKeywords` 映射数据已导出为 `assets/app_package_mapping.json`
- [x] `AppPackageMappingLoader` 已创建，运行时从 JSON 加载映射（含 fallback）
- [x] `AppPackageManager` 已更新使用 `AppPackageMappingLoader`
- [x] 保留原 `highPriorityKeywords` 作为 fallback（确保向后兼容）
- [x] Dokka 1.9.20 插件已在根 `build.gradle.kts` 中配置
- [x] Dokka 已在 `app/build.gradle.kts` 中应用并配置（moduleName、可见性、外部链接）
- [x] 验证 Dokka 任务可用（8 个文档任务注册成功）

## 整体验证

- [x] `./gradlew assembleDebug` 构建成功
- [x] `./gradlew testDebugUnitTest` 所有测试通过（247 个测试）
- [x] 安全漏洞已消除（文件操作路径限制、网络请求域名过滤）
- [x] 核心模块测试覆盖率显著提升（新增 87+ 个测试）
- [x] OkHttpClient 统一管理，消除重复创建
- [x] DI 模块迁移完成，ReleaseRepository 通过 Koin 注入
- [x] 缓存统一管理，内存压力触发清理
- [x] 硬编码映射外部化，支持 JSON 配置文件
- [x] Dokka API 文档工具已配置

# Tasks

## Phase 1：安全加固（P0 - 立即执行）

- [x] Task 1: 文件操作路径安全限制
  - [x] SubTask 1.1: 创建 `FilePathValidator` 工具类，实现路径白名单校验逻辑（规范化路径、检测路径穿越、校验是否在允许目录内）
  - [x] SubTask 1.2: 在 `FileToolExecutor.readFile` 中接入路径校验
  - [x] SubTask 1.3: 在 `FileToolExecutor.writeFile` 中接入路径校验
  - [x] SubTask 1.4: 在 `FileToolExecutor` 的 delete、move、compress 方法中接入路径校验
  - [x] SubTask 1.5: 编写 `FilePathValidator` 单元测试（覆盖正常路径、敏感路径、路径穿越场景）

- [x] Task 2: 网络请求域名安全限制
  - [x] SubTask 2.1: 创建 `NetworkSecurityValidator` 工具类，实现域名白名单和内网 IP 段过滤（10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、127.0.0.0/8）
  - [x] SubTask 2.2: 在 `NetworkToolExecutor.httpGet` 中接入安全校验
  - [x] SubTask 2.3: 在 `NetworkToolExecutor.httpPost` 中接入安全校验
  - [x] SubTask 2.4: 编写 `NetworkSecurityValidator` 单元测试

- [x] Task 3: 危险操作标记修正
  - [x] SubTask 3.1: 将 `FileToolExecutor` 中 `write_file` 的 `dangerCheck` 改为 `true`
  - [x] SubTask 3.2: 将 `delete`、`move`、`compress` 的 `dangerCheck` 改为 `true`
  - [x] SubTask 3.3: 验证权限系统正确拦截危险操作

- [x] Task 4: API Key 日志脱敏
  - [x] SubTask 4.1: 创建 `LogMaskingUtil` 工具类，实现 API Key 脱敏逻辑（前4位+****+后4位）
  - [x] SubTask 4.2: 在 `AutoGlmClient` 的日志输出处接入脱敏处理
  - [x] SubTask 4.3: 编写 `LogMaskingUtil` 单元测试

## Phase 2：架构优化（P1 - 1-2周内）

- [x] Task 5: OkHttpClient 统一管理
  - [x] SubTask 5.1: 在 `NetworkModule` 中配置统一的 OkHttpClient（连接池、超时、日志拦截器）
  - [x] SubTask 5.2: 移除 `AutoGlmClient` 中的 `SharedHttpClient`，改为通过 Koin 注入
  - [x] SubTask 5.3: 移除 `NetworkToolExecutor` 中的独立 client 创建，改为通过 Koin 注入
  - [x] SubTask 5.4: 验证所有网络请求使用共享实例

- [x] Task 6: DI 模块完成迁移
  - [x] SubTask 6.1: 将 `UiModule` 中 `ReleaseRepository()` 等直接实例化改为 Koin 注入
  - [x] SubTask 6.2: 将 `AppModule` 中 object 单例（`AIToolHandler`、`ToolPermissionSystem`、`AppPackageManager`）注册为 Koin 实例
  - [x] SubTask 6.3: 更新 `KoinScopeTest` 验证新的 DI 配置

- [x] Task 7: ActionExecutor 拆分
  - [x] SubTask 7.1: 创建 `ActionType` 枚举，定义动作类型分类
  - [x] SubTask 7.2: 创建 `ActionExecutorRouter`，作为路由层委托给 ActionExecutor
  - [x] SubTask 7.3: 验证构建通过（渐进式重构，保留原有 ActionExecutor 不变）

## Phase 3：测试增强（P1-P2 - 2-4周内）

- [x] Task 8: 工具执行器单元测试
  - [x] SubTask 8.1: 编写 `AIToolHandler` 单元测试（20 个测试，覆盖注册、注销、执行、危险操作检查）
  - [x] SubTask 8.2: 编写 `ToolPermissionSystem` 单元测试（16 个测试，覆盖 PermissionLevel 枚举和类结构）
  - [x] SubTask 8.3: 配置 `testOptions.unitTests.isReturnDefaultValues = true` 解决 Android Log 模拟问题

- [x] Task 9: 核心业务逻辑测试
  - [x] SubTask 9.1: 编写 `ActionType` 单元测试（23 个测试，覆盖所有动作名称解析）
  - [x] SubTask 9.2: 编写 `ActionExecutorRouter` 单元测试（8 个测试，覆盖路由和委托逻辑）
  - [x] SubTask 9.3: 补充 `FilePathValidator` 测试（18 个测试，覆盖路径穿越、符号链接等）
  - [x] SubTask 9.4: 补充 `NetworkSecurityValidator` 测试（38 个测试，覆盖 IPv4 边界、端口、URL 编码）
  - [x] SubTask 9.5: 修复 `CoreModuleTest` 中 2 处预先存在的断言失效

## Phase 4：长期优化（P2-P3 - 4周+）

- [ ] Task 10: 单例模式重构（推迟：高风险，AIToolHandler 和 ToolPermissionSystem 已是 class）
  - [x] SubTask 10.1: `AIToolHandler` 已是 class（private constructor + getInstance），无需重构
  - [x] SubTask 10.2: `ToolPermissionSystem` 已是 class（private constructor + getInstance），无需重构
  - [ ] SubTask 10.3: 将 `AppPackageManager` 从 object 改为 class（推迟：影响面广）
  - [ ] SubTask 10.4: 将 `FileToolExecutor` 从 object 改为 class（推迟：影响面广）
  - [ ] SubTask 10.5: 将 `NetworkToolExecutor` 从 object 改为 class（推迟：影响面广）

- [x] Task 11: 缓存统一管理
  - [x] SubTask 11.1: 创建 `CacheManager` 统一管理器，实现内存压力监听（`ComponentCallbacks2`）
  - [x] SubTask 11.2: 为 `AppPackageManager.appCache` 设置最大容量限制（512 条 LRU）
  - [x] SubTask 11.3: 将 `ScreenshotCache` 接入 `CacheManager`（实现 EvictableCache 接口）
  - [x] SubTask 11.4: 实现内存压力触发清理机制（onTrimMemory 分级清理）
  - [x] SubTask 11.5: 在 `AriesAgentApp.onCreate` 中初始化 CacheManager

- [x] Task 12: 硬编码映射外部化
  - [x] SubTask 12.1: 将 `highPriorityKeywords` 映射数据导出为 `assets/app_package_mapping.json`
  - [x] SubTask 12.2: 创建 `AppPackageMappingLoader`，运行时从 JSON 加载映射（含 fallback）
  - [x] SubTask 12.3: 更新 `AppPackageManager` 使用新的加载器
  - [x] SubTask 12.4: 保留原 `highPriorityKeywords` 作为 fallback（不删除，确保向后兼容）

- [x] Task 13: Dokka API 文档配置
  - [x] SubTask 13.1: 在根 `build.gradle.kts` 添加 Dokka 1.9.20 插件
  - [x] SubTask 13.2: 在 `app/build.gradle.kts` 应用 Dokka 并配置（moduleName、可见性、外部链接）
  - [x] SubTask 13.3: 验证 Dokka 任务可用（8 个文档任务注册成功）

# Task Dependencies
- [Task 5] depends on [Task 1, Task 2]（安全校验需先完成）
- [Task 6] depends on [Task 5]（OkHttpClient 统一后进行 DI 迁移）
- [Task 7] 可与 [Task 5, Task 6] 并行执行
- [Task 8] depends on [Task 1, Task 2, Task 3]（安全功能完成后测试）
- [Task 9] depends on [Task 7]（ActionExecutor 拆分完成后测试）
- [Task 10] depends on [Task 6]（DI 迁移完成后重构单例）
- [Task 11] 可与 [Task 10] 并行执行
- [Task 12] depends on [Task 10]（AppPackageManager 重构后迁移映射）
- [Task 13] 可独立执行

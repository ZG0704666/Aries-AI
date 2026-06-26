# 项目全面改进计划 Spec

## Why
基于对 Aries AI 项目的全面系统性扫描，发现项目在安全漏洞、测试覆盖率、架构设计和技术债务方面存在明显短板。其中文件读写无路径限制、网络请求无域名白名单等高危安全漏洞需立即修复；核心业务模块（ActionExecutor、AIToolHandler、ToolPermissionSystem）测试覆盖率为零，存在回归风险；DI 模块未完全迁移、OkHttpClient 重复创建等问题增加了维护成本和资源浪费。本计划旨在通过分阶段改进，系统性消除这些问题。

## What Changes
- **安全加固（P0）**：为 `FileToolExecutor` 添加路径白名单限制，为 `NetworkToolExecutor` 添加域名白名单和内网 IP 段过滤，修正危险操作标记，对 API Key 日志进行脱敏处理
- **架构优化（P1）**：统一 OkHttpClient 管理（移除 `AutoGlmClient` 和 `NetworkToolExecutor` 中的重复创建），完成 DI 模块迁移（`UiModule` 中的直接实例化改为 Koin 注入），拆分过大的 `ActionExecutor` 类
- **测试增强（P1-P2）**：为 `FileToolExecutor`、`NetworkToolExecutor`、`AIToolHandler`、`ToolPermissionSystem` 添加单元测试，补充 UI 集成测试
- **长期优化（P2-P3）**：重构单例模式（object → class），统一缓存管理（内存压力触发清理），硬编码映射外部化（迁移到 JSON 配置文件），文档整合与 API 文档生成

## Impact
- Affected specs: 安全策略、工具执行器规范、DI 配置规范、测试规范
- Affected code:
  - `app/src/main/java/com/ai/phoneagent/core/tools/file/FileToolExecutor.kt`
  - `app/src/main/java/com/ai/phoneagent/core/tools/network/NetworkToolExecutor.kt`
  - `app/src/main/java/com/ai/phoneagent/net/AutoGlmClient.kt`
  - `app/src/main/java/com/ai/phoneagent/di/NetworkModule.kt`
  - `app/src/main/java/com/ai/phoneagent/di/UiModule.kt`
  - `app/src/main/java/com/ai/phoneagent/core/executor/ActionExecutor.kt`
  - `app/src/main/java/com/ai/phoneagent/core/tools/AIToolHandler.kt`
  - `app/src/main/java/com/ai/phoneagent/permissions/ToolPermissionSystem.kt`
  - `app/src/main/java/com/ai/phoneagent/core/tools/AppPackageManager.kt`
  - `app/src/main/java/com/ai/phoneagent/AppPackageMapping.kt`
  - `app/src/test/` 目录（新增测试文件）

## ADDED Requirements

### Requirement: 文件操作路径安全限制
系统 SHALL 对 `FileToolExecutor` 的所有文件操作（read_file、write_file、delete、move、compress）实施路径白名单限制，只允许访问应用私有目录（`context.filesDir`、`context.cacheDir`、`context.getExternalFilesDir`）及用户明确授权的目录。

#### Scenario: 尝试访问私有目录内文件
- **WHEN** AI 工具请求读取 `context.filesDir/test.txt`
- **THEN** 操作成功执行

#### Scenario: 尝试访问系统敏感路径
- **WHEN** AI 工具请求写入 `/data/system/sensitive.db`
- **THEN** 操作被拒绝，返回错误信息 "路径不在允许的范围内"

#### Scenario: 尝试路径穿越攻击
- **WHEN** AI 工具请求读取 `context.filesDir/../../../etc/passwd`
- **THEN** 规范化路径后检测到越界，操作被拒绝

### Requirement: 网络请求域名安全限制
系统 SHALL 对 `NetworkToolExecutor` 的所有网络请求（http_get、http_post）实施域名白名单和内网 IP 段过滤，禁止访问私有 IP 地址段（10.0.0.0/8、172.16.0.0/12、192.168.0.0/16、127.0.0.0/8）。

#### Scenario: 请求白名单域名
- **WHEN** AI 工具请求访问 `https://api.example.com/data`
- **THEN** 请求正常发送

#### Scenario: 请求内网 IP 地址
- **WHEN** AI 工具请求访问 `http://192.168.1.1/admin`
- **THEN** 请求被拒绝，返回错误信息 "禁止访问内网地址"

### Requirement: 危险操作标记修正
系统 SHALL 将 `write_file`、`delete`、`move`、`compress` 操作的 `dangerCheck` 标记为 `true`，确保这些操作经过权限系统审批。

#### Scenario: 执行写入操作
- **WHEN** AI 工具请求执行 `write_file`
- **THEN** 权限系统提示用户确认，确认后才执行

### Requirement: API Key 日志脱敏
系统 SHALL 在所有日志输出中对 API Key 进行脱敏处理，仅显示前4位和后4位，中间用 `****` 替代。

#### Scenario: 调试模式日志输出
- **WHEN** DEBUG 模式下记录网络请求
- **THEN** API Key 显示为 `sk-a****b123` 格式，而非完整值

### Requirement: OkHttpClient 统一管理
系统 SHALL 通过 Koin 注入统一的 OkHttpClient 实例，移除 `AutoGlmClient` 中的 `SharedHttpClient` 和 `NetworkToolExecutor` 中的独立 client 创建。

#### Scenario: 网络请求复用连接池
- **WHEN** 任意模块发起网络请求
- **THEN** 使用 Koin 注入的共享 OkHttpClient 实例，复用连接池

### Requirement: ActionExecutor 拆分
系统 SHALL 将 `ActionExecutor` 按动作类型拆分为独立的执行器类（TapActionExecutor、SwipeActionExecutor、TextActionExecutor 等），每个执行器负责单一动作类型。

#### Scenario: 执行点击操作
- **WHEN** 系统需要执行 tap 动作
- **THEN** 由 `TapActionExecutor` 处理，而非庞大的 `ActionExecutor` 类

### Requirement: 工具执行器单元测试
系统 SHALL 为 `FileToolExecutor`、`NetworkToolExecutor`、`AIToolHandler`、`ToolPermissionSystem` 提供单元测试，覆盖正常流程和异常场景。

#### Scenario: 测试文件路径限制
- **WHEN** 单元测试模拟访问非法路径
- **THEN** 测试验证操作被正确拒绝

## MODIFIED Requirements

### Requirement: DI 模块配置
`UiModule` SHALL 通过 Koin 注入所有依赖，移除 `ReleaseRepository()` 等直接实例化代码。`AppModule` SHALL 将所有 object 单例注册为 Koin 管理的实例。

### Requirement: 缓存管理
`AppPackageManager` 的 `appCache` SHALL 设置最大容量限制，并在内存压力时触发清理。所有缓存模块 SHALL 接入统一的缓存管理器。

## REMOVED Requirements

### Requirement: AppPackageMapping 硬编码映射
**Reason**: 数百行硬编码映射维护成本高，扩展性差
**Migration**: 将映射数据迁移到 `assets/app_package_mapping.json`，运行时加载

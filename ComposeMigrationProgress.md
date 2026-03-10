# Compose 迁移进度

最后更新：2026-03-10  
当前分支：`codex/refactor-md3-modular`

## 当前进度

- 可见界面迁移进度：`100%`
- 底层架构迁移进度：`100%`

## 本轮完成

- 主消息列表样式按新的审美约束重做为 Compose Material 3 版本：
  - 去掉普通模型消息卡片
  - 去掉用户角色标识
  - 模型标识统一为 `Aries AI`
  - 思考内容改为可展开 / 收起的独立思考卡片
  - 去掉描边和阴影式消息装饰
- `AGENTS.md` 已补充仓库级 UI 约束：
  - 默认使用 Compose
  - 默认遵循官方 Material 3
  - 默认避免描边和阴影
- 偏好数据存储已引入并接入主链：
  - `Preferences DataStore` 用于保存活动会话和思考展开偏好
- 结构化会话数据已引入并接入主链：
  - `Room` 用于本地持久化会话
  - `kotlinx.serialization` 用于会话消息 JSON 编解码
- 旧的会话 `SharedPreferences + Gson` 已降级为一次性迁移入口，不再作为主存储路径

## 已完成范围

- 主界面顶栏：Compose
- 主界面侧边栏：Compose
- 主界面消息显示：Compose
- 主界面输入区：Compose
- 设置界面：Compose
- 关于界面：Compose
- 自动化控制面板：Compose
- 主界面会话持久化：Room + serialization
- 主界面轻量偏好：Preferences DataStore

## 当前剩余工作

- 继续拆分超长的 `MainActivity.kt`
- 将部分页面状态和业务逻辑继续下沉到独立 state holder / repository / feature 模块
- 继续清理未参与运行的历史代码与资源

## 实时更新规则

- 每次推进 Compose 或底层架构迁移后，同步更新本文件
- 至少记录三项：
  - 本轮完成内容
  - 当前整体进度
  - 下一步重点

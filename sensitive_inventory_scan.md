# 敏感项清单（深度扫描：包含被忽略目录与二进制）

日期: 2026-01-14

已执行操作：对仓库进行了并行正则扫描（包含 includeIgnoredFiles=true），查找私钥 PEM、云平台 key、OpenAI/其它 API key 模式、client_secret、keystore/证书文件以及 release 输出目录。

高风险（需要立即处理）
- g:\Aries AI project\temp\Operit-main\examples\jmcomic.ts (lines ~273-276、~410)
  - 包含硬编码常量：
    - `APP_TOKEN_SECRET: '18comicAPP'`
    - `APP_TOKEN_SECRET_2: '18comicAPPContent'`
    - `APP_DATA_SECRET: '185Hcomic3PAPP7R'`
  - 风险：真实应用密钥/签名常量泄露。建议立即旋转这些密钥并从 Git 历史中移除（git-filter-repo / BFG）。

中等风险（可能泄露，需验证）
- g:\Aries AI project\temp\Operit-main\app\build.gradle.kts (line ~66)
  - 通过 `localProperties.getProperty("GITHUB_CLIENT_SECRET")` 将 `GITHUB_CLIENT_SECRET` 注入 `BuildConfig`。
  - 通过 `localProperties.getProperty("RELEASE_STORE_PASSWORD")` / `RELEASE_KEY_PASSWORD` 用于签名配置。
  - 风险：如果 `local.properties` 被误提交，签名口令与客户端 secret 将泄露。检查仓库中是否存在已提交的 `local.properties` 或 `.env` 文件。

- g:\Aries AI project\temp\Operit-main\app\src\main\java\...\GitHubAuthPreferences.kt / GitHubApiService.kt
  - 使用 `BuildConfig.GITHUB_CLIENT_SECRET` 并将 `client_secret` 作为请求参数发送（需确保 secret 不在仓库中）。

低/信息性（常见但非直接泄露）
- 大量脚本与示例使用环境变量（良好实践）：例如 `AI_API_KEY`, `GITHUB_TOKEN`, `GH_TOKEN`, `GITHUB_PAT` 出现在 `tools/github/*.py`, `tools/hotbuild/*` 等位置，均通过 env 读取或提示使用 `.env`（应确保 `.env` 被 gitignore）。
- 大型资源/词表文件（如 `app/src/main/assets/tokenizer.json`）包含诸如 "password"/"secret" 等词汇，但这些是词表 token，不是凭据。
- 文档/依赖树中存在第三方包（例如 workspace 的 .venv 中 openai 包），会出现 `client_secret` 等术语，属于库实现，不是项目泄露。

未发现（在本次包含忽略目录的扫描中）
- 未在可访问文件中找到明文 PEM 私钥块（`-----BEGIN PRIVATE KEY-----` / `-----BEGIN RSA PRIVATE KEY-----` 未命中）。
- 未找到仓库根下显式的 `.jks` / `.keystore` / `.p12` / `.pfx` 等证书文件（file-search 未返回匹配）。

下一步建议（已在待办中）：
1. 立即：对 `jmcomic` 示例中的硬编码密钥进行处理——旋转密钥、从远端撤销并从 Git 历史中清除。优先级最高。
2. 验证：确认 `local.properties` / `.env` 未被提交；若被提交，删除并清理历史、并旋转相关密钥（签名口令、第三方 client secrets）。
3. 修复：将任何硬编码凭据替换为环境变量或密钥管理服务（示例：`BuildConfig` 从 CI secret 注入，`.env.example` 提供模板），并确保 `.gitignore` 屏蔽 local.properties、*.jks、release/ 等。
4. 历史清理：准备 `git-filter-repo` 或 BFG 脚本清理已泄露值并提示密钥旋转步骤。
5. 可选：对二进制 blob 做熵/签名扫描（detect embedded PEM/base64 keys）-- 需更专用二进制扫描工具或启用更深层次的扫描（例如扫描 release/zip/aar 文件内容）。

如果要，我可以：
- 生成完整逐条清单（文件路径 + 行号 + 行片段）并保存为仓库文件；
- 生成用于清理 Git 历史的 `git-filter-repo` 脚本模板并说明操作步骤；
- 生成 `.env.example` 与 `.gitignore` 建议变更。


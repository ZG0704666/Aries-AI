# Aries Telemetry Worker

这个 Worker 用来接收 Android App 的前台心跳，并聚合出当前在线用户量、近 1 小时/24 小时/7 天活跃情况。

## 接口

- `POST /v1/telemetry/heartbeat`
  - App 端匿名心跳写入。
  - 请求体示例：

```json
{
  "installIdHash": "<sha256>",
  "sessionId": "<uuid>",
  "platform": "android",
  "appVersion": "v1.4.2-xyla.alpha",
  "versionCode": 17,
  "locale": "zh-CN",
  "source": "foreground"
}
```

- `GET /v1/telemetry/stats/summary`
  - 管理端汇总接口。
  - 需要请求头：`Authorization: Bearer <STATS_API_TOKEN>`。

- `GET /dashboard`
  - 简单管理页，直接展示当前在线、24 小时活跃和趋势图。
  - 使用浏览器 Basic Auth 登录：用户名固定为 `admin`，密码为 `STATS_API_TOKEN`。

- `GET /healthz`
  - 健康检查。

## 部署

1. 进入目录：`cd telemetry-worker`
2. 安装依赖：`npm install`
3. 设置管理查询密钥：`npx wrangler secret put STATS_API_TOKEN`
4. 首次部署：`npx wrangler deploy`
5. 远端执行数据库迁移：`npx wrangler d1 migrations apply aries-telemetry --remote`

如果你的 Wrangler 版本没有自动创建 D1 绑定，请先运行：

`npx wrangler d1 create aries-telemetry`

然后把返回的 `database_id` 回填到 [telemetry-worker/wrangler.jsonc](telemetry-worker/wrangler.jsonc) 的 `d1_databases` 配置里，再重新执行部署和迁移。

## 查询示例

```bash
curl -H "Authorization: Bearer $STATS_API_TOKEN" \
  https://oiariesapi.xuanyu.online/v1/telemetry/stats/summary
```

管理页可直接在浏览器打开：

`https://oiariesapi.xuanyu.online/dashboard`

或者直接打开根路径，它会自动跳到 dashboard：

`https://oiariesapi.xuanyu.online/`

返回体里包含：

- `summary.currentUsers`: 当前窗口内在线用户数，默认窗口 5 分钟
- `summary.activeUsers1h`: 最近 1 小时活跃安装数
- `summary.activeUsers24h`: 最近 24 小时活跃安装数
- `summary.activeUsers7d`: 最近 7 天活跃安装数
- `recentTrend`: 最近 24 小时按 5 分钟桶聚合的活跃趋势

## 数据策略

- App 只上传匿名安装 ID 的 SHA-256 摘要，不上传原始设备标识。
- 服务端以 5 分钟桶去重，避免同一安装在短时间内重复放大计数。
- 定时任务每 6 小时清理一次超过 30 天的桶数据。

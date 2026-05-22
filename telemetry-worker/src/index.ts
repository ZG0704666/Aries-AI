interface D1PreparedStatement {
  bind(...values: unknown[]): D1PreparedStatement;
  first<T = Record<string, unknown>>(): Promise<T | null>;
  run(): Promise<unknown>;
  all<T = Record<string, unknown>>(): Promise<{ results: T[] }>;
}

interface D1DatabaseBinding {
  prepare(query: string): D1PreparedStatement;
  batch(statements: D1PreparedStatement[]): Promise<unknown>;
}

interface ExecutionContext {
  waitUntil(promise: Promise<unknown>): void;
}

interface ScheduledController {
  cron: string;
  scheduledTime: number;
}

interface Env {
  DB: D1DatabaseBinding;
  STATS_API_TOKEN: string;
  CURRENT_WINDOW_MINUTES?: number | string;
  HEARTBEAT_BUCKET_MINUTES?: number | string;
  RETENTION_DAYS?: number | string;
}

interface HeartbeatPayload {
  installIdHash: string;
  sessionId: string;
  platform?: string;
  appVersion?: string;
  versionCode?: number;
  locale?: string;
  source?: string;
}

interface RequestWithCf extends Request {
  cf?: {
    country?: string;
  };
}

const JSON_HEADERS = {
  "Content-Type": "application/json; charset=utf-8",
  "Cache-Control": "no-store",
} as const;

const HTML_HEADERS = {
  "Content-Type": "text/html; charset=utf-8",
  "Cache-Control": "no-store",
} as const;

const HEARTBEAT_PATH = "/v1/telemetry/heartbeat";
const STATS_SUMMARY_PATH = "/v1/telemetry/stats/summary";
const DASHBOARD_PATH = "/dashboard";
const DASHBOARD_BASIC_USERNAME = "admin";

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return withCors(new Response(null, { status: 204 }), request);
    }

    try {
      if (request.method === "GET" && url.pathname === "/") {
        return Response.redirect(`${url.origin}${DASHBOARD_PATH}`, 302);
      }

      if (request.method === "GET" && url.pathname === "/healthz") {
        return jsonResponse(request, { ok: true, service: "aries-telemetry" });
      }

      if (request.method === "GET" && url.pathname === DASHBOARD_PATH) {
        if (!hasStatsAccess(request, env)) {
          return unauthorizedResponse(true);
        }
        return htmlResponse(renderDashboardPage());
      }

      if (request.method === "POST" && url.pathname === HEARTBEAT_PATH) {
        return await handleHeartbeat(request as RequestWithCf, env);
      }

      if (request.method === "GET" && url.pathname === STATS_SUMMARY_PATH) {
        return await handleStatsSummary(request, env);
      }

      return jsonResponse(request, { error: "not_found" }, 404);
    } catch (error) {
      console.error("telemetry_request_failed", error);
      ctx.waitUntil(pruneOldHeartbeatBuckets(env));
      return jsonResponse(request, { error: "internal_error" }, 500);
    }
  },

  async scheduled(_controller: ScheduledController, env: Env): Promise<void> {
    await pruneOldHeartbeatBuckets(env);
  },
};

async function handleHeartbeat(request: RequestWithCf, env: Env): Promise<Response> {
  const body = await parseJson<HeartbeatPayload>(request);
  if (!body) {
    return jsonResponse(request, { error: "invalid_json" }, 400);
  }

  const installIdHash = normalizeInstallIdHash(body.installIdHash);
  const sessionId = normalizeText(body.sessionId, 128);
  if (!installIdHash || !sessionId) {
    return jsonResponse(request, { error: "invalid_payload" }, 400);
  }

  const now = Date.now();
  const bucketMinutes = readPositiveInt(env.HEARTBEAT_BUCKET_MINUTES, 5);
  const bucketSizeMs = bucketMinutes * 60_000;
  const bucketStartMs = Math.floor(now / bucketSizeMs) * bucketSizeMs;
  const platform = normalizeText(body.platform ?? "android", 32) ?? "android";
  const appVersion = normalizeText(body.appVersion, 64);
  const versionCode = normalizeInteger(body.versionCode);
  const locale = normalizeText(body.locale, 32);
  const source = normalizeText(body.source, 32) ?? "foreground";
  const country = normalizeText(request.cf?.country, 8);

  const existing = await env.DB.prepare(
    "SELECT last_session_id AS lastSessionId FROM telemetry_clients WHERE install_id_hash = ?"
  )
    .bind(installIdHash)
    .first<{ lastSessionId?: string | null }>();

  const isNewSession = !existing || existing.lastSessionId !== sessionId;
  const increment = isNewSession ? 1 : 0;

  await env.DB.batch([
    env.DB.prepare(
      `INSERT INTO telemetry_clients (
        install_id_hash,
        platform,
        first_seen_at,
        last_seen_at,
        last_session_id,
        last_session_started_at,
        total_sessions,
        app_version,
        version_code,
        locale,
        country,
        last_source
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(install_id_hash) DO UPDATE SET
        platform = excluded.platform,
        last_seen_at = excluded.last_seen_at,
        total_sessions = telemetry_clients.total_sessions + ?,
        last_session_id = CASE WHEN ? = 1 THEN excluded.last_session_id ELSE telemetry_clients.last_session_id END,
        last_session_started_at = CASE WHEN ? = 1 THEN excluded.last_session_started_at ELSE telemetry_clients.last_session_started_at END,
        app_version = excluded.app_version,
        version_code = excluded.version_code,
        locale = excluded.locale,
        country = excluded.country,
        last_source = excluded.last_source`
    ).bind(
      installIdHash,
      platform,
      now,
      now,
      sessionId,
      now,
      1,
      appVersion,
      versionCode,
      locale,
      country,
      source,
      increment,
      increment,
      increment
    ),
    env.DB.prepare(
      `INSERT INTO telemetry_heartbeat_buckets (
        install_id_hash,
        bucket_5m,
        received_at,
        session_id,
        app_version,
        version_code,
        locale,
        country
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT(install_id_hash, bucket_5m) DO UPDATE SET
        received_at = excluded.received_at,
        session_id = excluded.session_id,
        app_version = excluded.app_version,
        version_code = excluded.version_code,
        locale = excluded.locale,
        country = excluded.country`
    ).bind(
      installIdHash,
      bucketStartMs,
      now,
      sessionId,
      appVersion,
      versionCode,
      locale,
      country
    ),
  ]);

  return jsonResponse(request, {
    ok: true,
    bucketStartMs,
    newSession: isNewSession,
  });
}

async function handleStatsSummary(request: Request, env: Env): Promise<Response> {
  if (!hasStatsAccess(request, env)) {
    return unauthorizedResponse(false, request);
  }

  const now = Date.now();
  const currentWindowMs = readPositiveInt(env.CURRENT_WINDOW_MINUTES, 5) * 60_000;
  const lastHourMs = 60 * 60_000;
  const lastDayMs = 24 * lastHourMs;
  const lastWeekMs = 7 * lastDayMs;

  const overview = await env.DB.prepare(
    `SELECT
      COUNT(*) AS totalInstalls,
      COALESCE(SUM(CASE WHEN last_seen_at >= ? THEN 1 ELSE 0 END), 0) AS currentUsers,
      COALESCE(SUM(CASE WHEN last_seen_at >= ? THEN 1 ELSE 0 END), 0) AS activeUsers1h,
      COALESCE(SUM(CASE WHEN last_seen_at >= ? THEN 1 ELSE 0 END), 0) AS activeUsers24h,
      COALESCE(SUM(CASE WHEN last_seen_at >= ? THEN 1 ELSE 0 END), 0) AS activeUsers7d,
      COALESCE(SUM(CASE WHEN first_seen_at >= ? THEN 1 ELSE 0 END), 0) AS newInstalls24h
    FROM telemetry_clients`
  )
    .bind(now - currentWindowMs, now - lastHourMs, now - lastDayMs, now - lastWeekMs, now - lastDayMs)
    .first<Record<string, unknown>>();

  const versionRows = await env.DB.prepare(
    `SELECT COALESCE(app_version, 'unknown') AS appVersion, COUNT(*) AS installs
     FROM telemetry_clients
     WHERE last_seen_at >= ?
     GROUP BY COALESCE(app_version, 'unknown')
     ORDER BY installs DESC
     LIMIT 10`
  )
    .bind(now - lastDayMs)
    .all<{ appVersion: string; installs: number | string }>();

  const countryRows = await env.DB.prepare(
    `SELECT COALESCE(country, 'unknown') AS country, COUNT(*) AS installs
     FROM telemetry_clients
     WHERE last_seen_at >= ?
     GROUP BY COALESCE(country, 'unknown')
     ORDER BY installs DESC
     LIMIT 10`
  )
    .bind(now - lastDayMs)
    .all<{ country: string; installs: number | string }>();

  const trendRows = await env.DB.prepare(
    `SELECT bucket_5m AS bucketStartMs, COUNT(DISTINCT install_id_hash) AS activeUsers
     FROM telemetry_heartbeat_buckets
     WHERE bucket_5m >= ?
     GROUP BY bucket_5m
     ORDER BY bucket_5m ASC`
  )
    .bind(now - lastDayMs)
    .all<{ bucketStartMs: number | string; activeUsers: number | string }>();

  return jsonResponse(request, {
    ok: true,
    generatedAt: now,
    windows: {
      currentMinutes: readPositiveInt(env.CURRENT_WINDOW_MINUTES, 5),
      activeHours: [1, 24, 168],
    },
    summary: {
      totalInstalls: toNumber(overview?.totalInstalls),
      currentUsers: toNumber(overview?.currentUsers),
      activeUsers1h: toNumber(overview?.activeUsers1h),
      activeUsers24h: toNumber(overview?.activeUsers24h),
      activeUsers7d: toNumber(overview?.activeUsers7d),
      newInstalls24h: toNumber(overview?.newInstalls24h),
    },
    topVersions: versionRows.results.map((row) => ({
      appVersion: row.appVersion,
      installs: toNumber(row.installs),
    })),
    topCountries: countryRows.results.map((row) => ({
      country: row.country,
      installs: toNumber(row.installs),
    })),
    recentTrend: trendRows.results.map((row) => ({
      bucketStartMs: toNumber(row.bucketStartMs),
      activeUsers: toNumber(row.activeUsers),
    })),
  });
}

async function pruneOldHeartbeatBuckets(env: Env): Promise<void> {
  const retentionDays = readPositiveInt(env.RETENTION_DAYS, 30);
  const cutoffMs = Date.now() - retentionDays * 24 * 60 * 60_000;
  await env.DB.prepare(
    "DELETE FROM telemetry_heartbeat_buckets WHERE received_at < ?"
  )
    .bind(cutoffMs)
    .run();
}

function hasStatsAccess(request: Request, env: Env): boolean {
  const configuredToken = env.STATS_API_TOKEN?.trim();
  if (!configuredToken) return false;

  const authHeader = request.headers.get("Authorization")?.trim() ?? "";
  if (authHeader.startsWith("Bearer ")) {
    return authHeader.slice("Bearer ".length).trim() === configuredToken;
  }

  if (!authHeader.startsWith("Basic ")) return false;
  const basicCredentials = decodeBasicAuthorization(authHeader.slice("Basic ".length).trim());
  if (!basicCredentials) return false;
  return (
    basicCredentials.username === DASHBOARD_BASIC_USERNAME &&
    basicCredentials.password === configuredToken
  );
}

function decodeBasicAuthorization(value: string): { username: string; password: string } | null {
  try {
    const decoded = atob(value);
    const separatorIndex = decoded.indexOf(":");
    if (separatorIndex <= 0) return null;
    return {
      username: decoded.slice(0, separatorIndex),
      password: decoded.slice(separatorIndex + 1),
    };
  } catch {
    return null;
  }
}

async function parseJson<T>(request: Request): Promise<T | null> {
  try {
    return (await request.json()) as T;
  } catch {
    return null;
  }
}

function normalizeInstallIdHash(value: string | undefined): string | null {
  const normalized = value?.trim().toLowerCase() ?? "";
  return /^[a-f0-9]{64}$/.test(normalized) ? normalized : null;
}

function normalizeText(value: string | undefined, maxLength: number): string | null {
  const normalized = value?.trim();
  if (!normalized) return null;
  return normalized.slice(0, maxLength);
}

function normalizeInteger(value: number | undefined): number | null {
  if (typeof value !== "number" || !Number.isFinite(value)) return null;
  return Math.trunc(value);
}

function readPositiveInt(value: number | string | undefined, fallback: number): number {
  if (typeof value === "number" && Number.isFinite(value) && value > 0) {
    return Math.trunc(value);
  }
  if (typeof value === "string") {
    const parsed = Number.parseInt(value, 10);
    if (Number.isFinite(parsed) && parsed > 0) {
      return parsed;
    }
  }
  return fallback;
}

function toNumber(value: unknown): number {
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return 0;
}

function jsonResponse(request: Request, payload: unknown, status = 200): Response {
  return withCors(
    new Response(JSON.stringify(payload), {
      status,
      headers: JSON_HEADERS,
    }),
    request
  );
}

function withCors(response: Response, request: Request): Response {
  const headers = new Headers(response.headers);
  headers.set("Access-Control-Allow-Origin", request.headers.get("Origin") ?? "*");
  headers.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
  headers.set("Access-Control-Allow-Headers", "Content-Type,Authorization");
  headers.set("Vary", "Origin");
  return new Response(response.body, {
    status: response.status,
    headers,
  });
}

function unauthorizedResponse(asHtml: boolean, request?: Request): Response {
  const headers = new Headers({
    "WWW-Authenticate": `Basic realm="Aries Telemetry Dashboard", charset="UTF-8"`,
    "Cache-Control": "no-store",
  });

  if (asHtml) {
    headers.set("Content-Type", "text/html; charset=utf-8");
    return new Response(
      "<h1>401 Unauthorized</h1><p>请使用 Basic Auth 登录，用户名 admin，密码为当前 STATS_API_TOKEN。</p>",
      {
        status: 401,
        headers,
      }
    );
  }

  const response = new Response(JSON.stringify({ error: "unauthorized" }), {
    status: 401,
    headers,
  });
  return request ? withCors(response, request) : response;
}

function htmlResponse(html: string): Response {
  return new Response(html, {
    status: 200,
    headers: HTML_HEADERS,
  });
}

function renderDashboardPage(): string {
  return String.raw`<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Aries Telemetry Dashboard</title>
    <style>
      :root {
        color-scheme: dark;
        --bg: #08111f;
        --bg-accent: #14213a;
        --panel: rgba(10, 20, 37, 0.86);
        --panel-strong: rgba(14, 28, 49, 0.96);
        --border: rgba(149, 179, 255, 0.14);
        --text: #eff5ff;
        --muted: #8ea1be;
        --accent: #53c7ff;
        --accent-soft: rgba(83, 199, 255, 0.18);
        --good: #54e0a4;
        --warn: #ffcc66;
      }

      * {
        box-sizing: border-box;
      }

      body {
        margin: 0;
        min-height: 100vh;
        font-family: "SF Pro Display", "PingFang SC", "Helvetica Neue", sans-serif;
        color: var(--text);
        background:
          radial-gradient(circle at top left, rgba(83, 199, 255, 0.18), transparent 24%),
          radial-gradient(circle at top right, rgba(84, 224, 164, 0.13), transparent 22%),
          linear-gradient(180deg, var(--bg-accent), var(--bg));
      }

      .page {
        max-width: 1220px;
        margin: 0 auto;
        padding: 32px 20px 48px;
      }

      .header {
        display: flex;
        align-items: flex-end;
        justify-content: space-between;
        gap: 16px;
        margin-bottom: 24px;
      }

      .title {
        margin: 0;
        font-size: clamp(28px, 4vw, 40px);
        font-weight: 720;
        letter-spacing: -0.04em;
      }

      .subtitle {
        margin: 10px 0 0;
        color: var(--muted);
        font-size: 14px;
      }

      .status {
        display: inline-flex;
        align-items: center;
        gap: 8px;
        padding: 10px 14px;
        border-radius: 999px;
        background: rgba(255, 255, 255, 0.05);
        border: 1px solid var(--border);
        color: var(--muted);
        font-size: 13px;
      }

      .status-dot {
        width: 10px;
        height: 10px;
        border-radius: 50%;
        background: var(--good);
        box-shadow: 0 0 0 6px rgba(84, 224, 164, 0.14);
      }

      .grid {
        display: grid;
        gap: 16px;
      }

      .cards {
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        margin-bottom: 16px;
      }

      .main {
        grid-template-columns: minmax(0, 1.8fr) minmax(280px, 0.9fr);
      }

      .panel {
        position: relative;
        overflow: hidden;
        border-radius: 24px;
        padding: 22px;
        background: linear-gradient(180deg, var(--panel-strong), var(--panel));
        border: 1px solid var(--border);
        backdrop-filter: blur(10px);
      }

      .panel::before {
        content: "";
        position: absolute;
        inset: 0;
        background: linear-gradient(135deg, rgba(83, 199, 255, 0.08), transparent 40%, rgba(84, 224, 164, 0.06));
        pointer-events: none;
      }

      .panel > * {
        position: relative;
        z-index: 1;
      }

      .metric-label {
        color: var(--muted);
        font-size: 13px;
      }

      .metric-value {
        margin-top: 12px;
        font-size: clamp(36px, 5vw, 52px);
        line-height: 1;
        font-weight: 760;
        letter-spacing: -0.05em;
      }

      .metric-note {
        margin-top: 10px;
        color: var(--muted);
        font-size: 13px;
      }

      .chart-header,
      .side-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        margin-bottom: 16px;
      }

      .panel-title {
        margin: 0;
        font-size: 18px;
        font-weight: 680;
      }

      .panel-hint {
        color: var(--muted);
        font-size: 13px;
      }

      .chart-wrap {
        position: relative;
        height: 300px;
        border-radius: 20px;
        background: rgba(255, 255, 255, 0.025);
        border: 1px solid rgba(255, 255, 255, 0.05);
      }

      .chart-wrap svg {
        width: 100%;
        height: 100%;
        display: block;
      }

      .chart-empty {
        position: absolute;
        inset: 0;
        display: none;
        align-items: center;
        justify-content: center;
        color: var(--muted);
        font-size: 14px;
      }

      .lists {
        display: grid;
        gap: 16px;
      }

      .list {
        display: grid;
        gap: 10px;
      }

      .list-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto;
        gap: 12px;
        align-items: center;
        padding: 12px 14px;
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.035);
        border: 1px solid rgba(255, 255, 255, 0.05);
      }

      .list-name {
        font-size: 14px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .list-value {
        color: var(--accent);
        font-weight: 700;
      }

      .footer {
        margin-top: 16px;
        color: var(--muted);
        font-size: 12px;
      }

      @media (max-width: 940px) {
        .main {
          grid-template-columns: 1fr;
        }

        .header {
          align-items: flex-start;
          flex-direction: column;
        }
      }
    </style>
  </head>
  <body>
    <div class="page">
      <div class="header">
        <div>
          <h1 class="title">Aries 实时遥测面板</h1>
          <p class="subtitle">展示当前在线人数、24 小时活跃和最近 24 小时趋势。页面每 60 秒自动刷新一次。</p>
        </div>
        <div class="status">
          <span class="status-dot"></span>
          <span id="status-text">正在加载</span>
        </div>
      </div>

      <div class="grid cards">
        <section class="panel">
          <div class="metric-label">当前在线</div>
          <div class="metric-value" id="metric-current">--</div>
          <div class="metric-note">按最近 <span id="metric-window">5</span> 分钟窗口去重</div>
        </section>
        <section class="panel">
          <div class="metric-label">24 小时活跃</div>
          <div class="metric-value" id="metric-24h">--</div>
          <div class="metric-note">过去 24 小时至少上报一次心跳的安装数</div>
        </section>
        <section class="panel">
          <div class="metric-label">总安装量</div>
          <div class="metric-value" id="metric-total">--</div>
          <div class="metric-note">自遥测启用以来首次上报的累计安装数</div>
        </section>
      </div>

      <div class="grid main">
        <section class="panel">
          <div class="chart-header">
            <div>
              <h2 class="panel-title">24 小时活跃趋势</h2>
              <div class="panel-hint" id="chart-range">最近 24 小时，按 5 分钟桶聚合</div>
            </div>
            <div class="panel-hint" id="last-updated">--</div>
          </div>
          <div class="chart-wrap">
            <svg id="trend-chart" viewBox="0 0 900 300" preserveAspectRatio="none" aria-label="活跃趋势图"></svg>
            <div class="chart-empty" id="chart-empty">还没有足够的数据生成趋势图</div>
          </div>
        </section>

        <div class="lists">
          <section class="panel">
            <div class="side-header">
              <h2 class="panel-title">最近活跃版本</h2>
              <div class="panel-hint">近 24 小时</div>
            </div>
            <div class="list" id="version-list"></div>
          </section>

          <section class="panel">
            <div class="side-header">
              <h2 class="panel-title">最近活跃地区</h2>
              <div class="panel-hint">近 24 小时</div>
            </div>
            <div class="list" id="country-list"></div>
          </section>
        </div>
      </div>

      <div class="footer">登录方式：Basic Auth。用户名固定为 admin，密码为当前 STATS_API_TOKEN。</div>
    </div>

    <script>
      const summaryUrl = "/v1/telemetry/stats/summary";
      const refreshIntervalMs = 60_000;
      const statusTextEl = document.getElementById("status-text");
      const metricCurrentEl = document.getElementById("metric-current");
      const metric24hEl = document.getElementById("metric-24h");
      const metricTotalEl = document.getElementById("metric-total");
      const metricWindowEl = document.getElementById("metric-window");
      const lastUpdatedEl = document.getElementById("last-updated");
      const chartRangeEl = document.getElementById("chart-range");
      const chartEl = document.getElementById("trend-chart");
      const chartEmptyEl = document.getElementById("chart-empty");
      const versionListEl = document.getElementById("version-list");
      const countryListEl = document.getElementById("country-list");

      function formatNumber(value) {
        return new Intl.NumberFormat("zh-CN").format(value || 0);
      }

      function formatDateTime(value) {
        const date = new Date(value);
        return date.toLocaleString("zh-CN", {
          month: "2-digit",
          day: "2-digit",
          hour: "2-digit",
          minute: "2-digit",
        });
      }

      function formatTime(value) {
        const date = new Date(value);
        return date.toLocaleTimeString("zh-CN", {
          hour: "2-digit",
          minute: "2-digit",
        });
      }

      function renderList(container, rows, emptyLabelKey, valueKey) {
        if (!rows || !rows.length) {
          container.innerHTML = '<div class="list-row"><div class="list-name">暂无数据</div><div class="list-value">0</div></div>';
          return;
        }

        container.innerHTML = rows.slice(0, 5).map((row) => {
          const label = row[emptyLabelKey] || "unknown";
          const value = formatNumber(row[valueKey] || 0);
          return '<div class="list-row"><div class="list-name">' + escapeHtml(String(label)) + '</div><div class="list-value">' + value + '</div></div>';
        }).join("");
      }

      function renderTrend(rows) {
        if (!rows || !rows.length) {
          chartEl.innerHTML = "";
          chartEmptyEl.style.display = "flex";
          return;
        }

        chartEmptyEl.style.display = "none";

        const width = 900;
        const height = 300;
        const padX = 36;
        const padY = 26;
        const usableWidth = width - padX * 2;
        const usableHeight = height - padY * 2;
        const maxValue = Math.max(1, ...rows.map((row) => Number(row.activeUsers) || 0));

        const points = rows.map((row, index) => {
          const x = padX + (rows.length === 1 ? usableWidth / 2 : (index / (rows.length - 1)) * usableWidth);
          const y = height - padY - ((Number(row.activeUsers) || 0) / maxValue) * usableHeight;
          return { x, y, value: Number(row.activeUsers) || 0, bucketStartMs: Number(row.bucketStartMs) || 0 };
        });

        const polylinePoints = points.map((point) => point.x.toFixed(1) + ',' + point.y.toFixed(1)).join(' ');
        const areaPath = [
          'M ' + points[0].x.toFixed(1) + ' ' + (height - padY),
          ...points.map((point) => 'L ' + point.x.toFixed(1) + ' ' + point.y.toFixed(1)),
          'L ' + points[points.length - 1].x.toFixed(1) + ' ' + (height - padY),
          'Z'
        ].join(' ');

        const yGuides = Array.from({ length: 4 }, (_, index) => {
          const value = Math.round((maxValue / 3) * index);
          const y = height - padY - (value / maxValue) * usableHeight;
          return '<g><line x1="' + padX + '" y1="' + y.toFixed(1) + '" x2="' + (width - padX) + '" y2="' + y.toFixed(1) + '" stroke="rgba(255,255,255,0.08)" stroke-dasharray="4 8" /><text x="8" y="' + (y + 4).toFixed(1) + '" fill="rgba(142,161,190,0.9)" font-size="11">' + escapeHtml(String(value)) + '</text></g>';
        }).join('');

        const labelIndexes = [0, Math.floor((rows.length - 1) / 2), rows.length - 1]
          .filter((value, index, array) => array.indexOf(value) === index);

        const xLabels = labelIndexes.map((index) => {
          const point = points[index];
          return '<text x="' + point.x.toFixed(1) + '" y="286" text-anchor="middle" fill="rgba(142,161,190,0.9)" font-size="11">' + escapeHtml(formatTime(point.bucketStartMs)) + '</text>';
        }).join('');

        const dots = points.filter((_point, index) => index % Math.max(1, Math.round(points.length / 18)) === 0 || index === points.length - 1)
          .map((point) => '<circle cx="' + point.x.toFixed(1) + '" cy="' + point.y.toFixed(1) + '" r="3.5" fill="#53c7ff"><title>' + escapeHtml(formatDateTime(point.bucketStartMs) + ' · ' + point.value + ' 人') + '</title></circle>')
          .join('');

        chartEl.innerHTML = [
          yGuides,
          '<path d="' + areaPath + '" fill="rgba(83,199,255,0.16)"></path>',
          '<polyline points="' + polylinePoints + '" fill="none" stroke="#53c7ff" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"></polyline>',
          dots,
          xLabels,
        ].join('');
      }

      function escapeHtml(value) {
        return value
          .replaceAll('&', '&amp;')
          .replaceAll('<', '&lt;')
          .replaceAll('>', '&gt;')
          .replaceAll('"', '&quot;')
          .replaceAll("'", '&#39;');
      }

      async function loadSummary() {
        statusTextEl.textContent = '同步中';
        try {
          const response = await fetch(summaryUrl, { cache: 'no-store', credentials: 'same-origin' });
          if (!response.ok) {
            throw new Error('HTTP ' + response.status);
          }

          const data = await response.json();
          metricCurrentEl.textContent = formatNumber(data.summary?.currentUsers || 0);
          metric24hEl.textContent = formatNumber(data.summary?.activeUsers24h || 0);
          metricTotalEl.textContent = formatNumber(data.summary?.totalInstalls || 0);
          metricWindowEl.textContent = String(data.windows?.currentMinutes || 5);
          lastUpdatedEl.textContent = '更新时间 ' + formatDateTime(data.generatedAt || Date.now());
          chartRangeEl.textContent = '最近 24 小时，按 ' + String(data.windows?.currentMinutes || 5) + ' 分钟窗口展示当前在线变化';
          renderTrend(data.recentTrend || []);
          renderList(versionListEl, data.topVersions || [], 'appVersion', 'installs');
          renderList(countryListEl, data.topCountries || [], 'country', 'installs');
          statusTextEl.textContent = '运行中';
        } catch (error) {
          console.error('dashboard_load_failed', error);
          statusTextEl.textContent = '加载失败';
          chartEl.innerHTML = '';
          chartEmptyEl.style.display = 'flex';
          chartEmptyEl.textContent = '加载统计数据失败，请刷新或重新登录';
        }
      }

      loadSummary();
      setInterval(loadSummary, refreshIntervalMs);
    </script>
  </body>
</html>`;
}

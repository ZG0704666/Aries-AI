#!/usr/bin/env node
/**
 * 生成旧链接兼容层。
 *
 * 1. site/public/docs-center.html：旧 hash 路由 `#/<id>` 的 JS 跳转壳
 *    （hash 不会发往服务端，Cloudflare 无法做 301）。
 * 2. site/public/_redirects：旧 `/docs/*.md` 直链 → 新文档 URL 的 301。
 */
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { buildLegacyRouteMap, readJson, siteRoot, stripBom, VITEPRESS_DIR } from './lib/docs-data.mjs';

const DOCS_ORIGIN = 'https://docs.aries-agent.com';

function renderShell(routes) {
  const payload = JSON.stringify(routes);

  return `<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <meta name="robots" content="noindex, follow" />
    <title>正在跳转到 Aries AI 文档…</title>
    <link rel="canonical" href="${DOCS_ORIGIN}/" />
    <link rel="icon" type="image/png" href="/favicon.png" />
    <style>
      :root { color-scheme: dark; }
      body {
        display: grid;
        place-items: center;
        min-height: 100vh;
        margin: 0;
        background: #05060a;
        color: #c9d0e0;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
          "Microsoft YaHei", sans-serif;
        text-align: center;
      }
      a { color: #7aa2ff; }
    </style>
  </head>
  <body>
    <main>
      <p>正在跳转到新版文档…</p>
      <p><a id="fallback" href="${DOCS_ORIGIN}/">如果没有自动跳转，请点击这里</a></p>
    </main>
    <script>
      (function () {
        var ORIGIN = ${JSON.stringify(DOCS_ORIGIN)};
        var ROUTES = ${payload};

        function resolve() {
          var raw = (location.hash || '').replace(/^#\\/?/, '').trim();
          if (!raw || raw === 'home' || raw === '/') return ORIGIN + '/';

          var id = raw;
          try {
            id = decodeURIComponent(raw);
          } catch (error) {
            /* 保留原字符串 */
          }

          var slug = ROUTES[id] || ROUTES[raw] || '';
          return ORIGIN + (slug ? '/' + slug : '/');
        }

        var target = resolve();
        var fallback = document.getElementById('fallback');
        if (fallback) fallback.href = target;
        location.replace(target);
      })();
    </script>
  </body>
</html>
`;
}

/** 旧站文档直链：/docs/<中文路径>.md → https://docs.aries-agent.com/<slug> */
async function renderRedirects(legacyRoutes) {
  const slugs = await readJson(path.join(VITEPRESS_DIR, 'slugs.json'));

  const rules = [
    '# 由 Aries-site/scripts/build-legacy.mjs 生成，请勿手动编辑',
    '',
    '# 旧文档中心（hash 路由由跳转壳处理）',
    '/docs-center            /docs-center.html   200',
    '/docs-center.html       /docs-center.html   200',
    '',
    '# 旧文档目录与首页',
    '/docs                   ' + DOCS_ORIGIN + '/   301',
    '/docs/                  ' + DOCS_ORIGIN + '/   301',
    '/index.html             /   301',
    '/download.html          /download   301',
    '',
  ];

  if (legacyRoutes.removed.length) {
    rules.push(
      `# 已删除的文档（跳转到语音输入章节）：${legacyRoutes.removed.join('、')}`,
      '',
    );
  }

  rules.push('# 旧 markdown 直链 → 新文档页');

  for (const [file, slug] of Object.entries(slugs).sort(([a], [b]) => a.localeCompare(b))) {
    const target = `${DOCS_ORIGIN}/${slug}`;
    // Cloudflare `_redirects` 以空白分隔列，中文路径必须百分号编码。
    rules.push(`${encodeURI(`/docs/${file}.md`)}  ${target}  301`);
  }

  rules.push(
    '',
    '# 其余按旧文档目录猜测的链接统一回文档首页',
    `/docs/*  ${DOCS_ORIGIN}/  302`,
    '',
    '# 兜底 404',
    '/*  /404.html  404',
    '',
  );

  return rules.join('\n');
}

async function main() {
  const legacy = await buildLegacyRouteMap();
  const shell = renderShell(legacy.routes);
  const redirects = await renderRedirects(legacy);

  await writeFile(path.join(siteRoot, 'site/public/docs-center.html'), stripBom(shell), 'utf8');
  await writeFile(path.join(siteRoot, 'site/public/_redirects'), stripBom(redirects), 'utf8');

  console.log(
    `[legacy] ${Object.keys(legacy.routes).length} 条旧 hash 路由，${legacy.removed.length} 篇已删除文档已回退`,
  );
}

main().catch((error) => {
  console.error('[legacy] 失败：', error);
  process.exitCode = 1;
});

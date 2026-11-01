#!/usr/bin/env node
/**
 * 生成机器可读路由索引（给 IDE、脚本和 AI Agent 使用）。
 *
 * 输出：
 * - Aries-site/docs/.vitepress/route-map.json   构建期索引
 * - Aries-site/docs/public/route-map.json       对外可访问副本
 *
 * 人类可读说明见 Aries-site/docs/开发规范与工具/官网与文档站代码索引.md。
 */
import { writeFile } from 'node:fs/promises';
import path from 'node:path';
import {
  buildLegacyRouteMap,
  DOCS_DIR,
  listDocs,
  readDocTitle,
  readJson,
  siteRoot,
  VITEPRESS_DIR,
} from './lib/docs-data.mjs';

const DOCS_ORIGIN = 'https://docs.aries-agent.com';
const SITE_ORIGIN = 'https://aries-agent.com';

async function main() {
  const slugs = await readJson(path.join(VITEPRESS_DIR, 'slugs.json'));
  const categories = await readJson(path.join(VITEPRESS_DIR, 'categories.json'));
  const docs = await listDocs();
  const legacy = await buildLegacyRouteMap();

  const seen = new Map();
  for (const [file, slug] of Object.entries(slugs)) {
    if (seen.has(slug)) {
      throw new Error(`slug 重复：${slug}（${file} 与 ${seen.get(slug)}）`);
    }
    seen.set(slug, file);
  }

  const entries = [];
  for (const doc of docs) {
    const slug = slugs[doc.file];
    if (!slug) {
      throw new Error(`文档缺少 slug：${doc.file}`);
    }
    entries.push({
      file: doc.file,
      title: (await readDocTitle(doc)) ?? doc.file,
      slug,
      url: `${DOCS_ORIGIN}/${slug}`,
      source: `Aries-site/docs/${doc.file}.md`,
    });
  }

  entries.sort((a, b) => a.slug.localeCompare(b.slug));

  const routeMap = {
    generatedAt: new Date().toISOString(),
    origins: { site: SITE_ORIGIN, docs: DOCS_ORIGIN },
    counts: {
      docs: entries.length,
      categories: categories.length,
      legacyRoutes: Object.keys(legacy.routes).length,
      removedDocs: legacy.removed.length,
    },
    categories,
    docs: entries,
    legacyRoutes: legacy.routes,
    removedDocs: legacy.removed,
  };

  const payload = `${JSON.stringify(routeMap, null, 2)}\n`;
  await writeFile(path.join(VITEPRESS_DIR, 'route-map.json'), payload, 'utf8');
  await writeFile(path.join(DOCS_DIR, 'public/route-map.json'), payload, 'utf8');

  console.log(
    `[index] ${entries.length} 篇文档 / ${categories.length} 个分类 / ${Object.keys(legacy.routes).length} 条旧路由 → ${path.relative(siteRoot, path.join(VITEPRESS_DIR, 'route-map.json'))}`,
  );
}

main().catch((error) => {
  console.error('[index] 失败：', error);
  process.exitCode = 1;
});

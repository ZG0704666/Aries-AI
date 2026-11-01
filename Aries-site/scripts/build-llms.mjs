#!/usr/bin/env node
/**
 * 生成 docs/public/llms.txt —— 给大模型/爬虫阅读的文档索引。
 * 规范参考：https://llmstxt.org/
 */
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { DOCS_DIR, listDocs, readDocTitle, readJson, VITEPRESS_DIR } from './lib/docs-data.mjs';

const DOCS_ORIGIN = 'https://docs.aries-agent.com';
const SITE_ORIGIN = 'https://aries-agent.com';

async function main() {
  const slugs = await readJson(path.join(VITEPRESS_DIR, 'slugs.json'));
  const categories = await readJson(path.join(VITEPRESS_DIR, 'categories.json'));
  const docs = await listDocs();
  const titles = new Map();

  for (const doc of docs) {
    titles.set(doc.file, (await readDocTitle(doc)) ?? doc.file);
  }

  const lines = [
    '# Aries AI',
    '',
    '> Aries AI 是面向 Android 11+ 的开源 AI 手机助手：用多模态模型理解屏幕，跨应用自动完成订票、购物、社交等任务；',
    '> 支持离线语音识别与虚拟屏后台隔离。本文档覆盖架构设计、对话系统、自动化引擎、虚拟屏、多模型接入与开发规范。',
    '',
    '## 官方入口',
    '',
    `- [官网](${SITE_ORIGIN}/)`,
    `- [下载安装](${SITE_ORIGIN}/download)`,
    '- [GitHub 仓库](https://github.com/ZG0704666/Aries-AI)',
    '- [版本发布](https://github.com/ZG0704666/Aries-AI/releases)',
    `- [机器可读索引](${DOCS_ORIGIN}/route-map.json)`,
    '',
  ];

  for (const category of categories) {
    lines.push(`## ${category.title}`, '');

    for (const item of category.items) {
      const slug = slugs[item.file];
      if (!slug) continue;
      const title = titles.get(item.file) ?? item.title;
      lines.push(`- [${title}](${DOCS_ORIGIN}/${slug})`);
    }

    lines.push('');
  }

  const target = path.join(DOCS_DIR, 'public/llms.txt');
  await writeFile(target, `${lines.join('\n').trimEnd()}\n`, 'utf8');

  console.log(`[llms] 已生成 ${path.relative(process.cwd(), target)}（${titles.size} 篇文档）`);
}

main().catch((error) => {
  console.error('[llms] 失败：', error);
  process.exitCode = 1;
});

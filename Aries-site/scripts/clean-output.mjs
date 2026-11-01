#!/usr/bin/env node
/**
 * 清理 macOS 在 NTFS / exFAT 卷上生成的 AppleDouble 文件（`._*`）。
 * 这些文件会被一并发布到 Cloudflare Pages，需要在产物里剔除。
 */
import { readdir, rm } from 'node:fs/promises';
import path from 'node:path';
import { siteRoot } from './lib/docs-data.mjs';

const TARGETS = [
  path.join(siteRoot, 'dist'),
  // 旧产物位置：清理可能残留的历史输出
  path.join(siteRoot, 'site/dist'),
  path.join(siteRoot, 'docs/.vitepress/dist'),
];

async function clean(dir) {
  let removed = 0;

  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return 0;
  }

  for (const entry of entries) {
    const target = path.join(dir, entry.name);

    if (entry.name.startsWith('._')) {
      try {
        await rm(target, { recursive: true, force: true });
        removed += 1;
      } catch (error) {
        console.warn(`[clean] 无法删除 ${target}：${error.code ?? error.message}`);
      }
      continue;
    }

    if (entry.isDirectory()) {
      removed += await clean(target);
    }
  }

  return removed;
}

async function main() {
  let total = 0;
  for (const target of TARGETS) {
    total += await clean(target);
  }
  console.log(`[clean] 清理 ${total} 个 AppleDouble 文件`);
}

main().catch((error) => {
  console.error('[clean] 失败：', error);
  process.exitCode = 1;
});
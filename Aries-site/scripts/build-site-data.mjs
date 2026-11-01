#!/usr/bin/env node
/**
 * 同步站点版本数据。
 *
 * 数据来源：
 * 1. GitHub Releases 最新正式版（tag、APK、更新日志）
 * 2. app/build.gradle.kts 的开发版 versionName / versionCode
 *
 * 网络不可用时保留已有 JSON，只更新开发版字段，避免构建被外部服务阻塞。
 */
import { readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { repoRoot, siteRoot } from './lib/docs-data.mjs';

const REPO = 'ZG0704666/Aries-AI';
const dataFile = path.join(siteRoot, 'site/src/data/site.generated.json');

async function readDevVersion() {
  const gradle = await readFile(path.join(repoRoot, 'app/build.gradle.kts'), 'utf8');
  const versionName = gradle.match(/versionName\s*=\s*"([^"]+)"/)?.[1];
  const versionCode = Number(gradle.match(/versionCode\s*=\s*(\d+)/)?.[1]);

  if (!versionName || !Number.isFinite(versionCode)) {
    throw new Error('无法从 app/build.gradle.kts 解析 versionName / versionCode');
  }

  return { versionName, versionCode };
}

async function fetchLatestRelease() {
  const headers = {
    Accept: 'application/vnd.github+json',
    'User-Agent': 'aries-site-build',
  };
  if (process.env.GITHUB_TOKEN) {
    headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
  }

  const response = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, {
    headers,
    signal: AbortSignal.timeout(20_000),
  });

  if (!response.ok) {
    throw new Error(`GitHub API 返回 ${response.status}`);
  }

  return response.json();
}

function normalizeRelease(release) {
  const apk = (release.assets ?? []).find((asset) => asset.name.endsWith('.apk'));

  return {
    tag: release.tag_name,
    name: release.name || release.tag_name,
    publishedAt: release.published_at,
    apkSize: apk?.size ?? 0,
    apkUrl: apk?.browser_download_url || `https://github.com/${REPO}/releases/latest/download/app-release.apk`,
    htmlUrl: release.html_url,
    body: release.body || '',
  };
}

async function main() {
  const current = JSON.parse(await readFile(dataFile, 'utf8'));
  const dev = await readDevVersion();

  let release = current.release;
  let source = '本地缓存';

  if (process.argv.includes('--offline')) {
    console.warn('[site-data] --offline：跳过 GitHub Release 拉取');
  } else {
    try {
      release = normalizeRelease(await fetchLatestRelease());
      source = 'GitHub Releases';
    } catch (error) {
      console.warn(`[site-data] 拉取最新 Release 失败，沿用本地缓存：${error.message}`);
    }
  }

  const next = {
    ...current,
    dev,
    release,
    generatedAt: new Date().toISOString(),
  };

  await writeFile(dataFile, `${JSON.stringify(next, null, 2)}\n`, 'utf8');

  console.log(
    `[site-data] ${source} → ${release.tag}（${(release.apkSize / 1024 / 1024).toFixed(0)} MB），开发版 ${dev.versionName} (${dev.versionCode})`,
  );
}

main().catch((error) => {
  console.error('[site-data] 失败：', error);
  process.exitCode = 1;
});

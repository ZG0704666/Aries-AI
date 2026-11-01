import { existsSync } from 'node:fs';
import { readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

/** Aries-site/ 目录 */
export const siteRoot = path.resolve(here, '../..');
/** 仓库根目录 */
export const repoRoot = path.resolve(siteRoot, '..');

export const DOCS_DIR = path.join(siteRoot, 'docs');
export const VITEPRESS_DIR = path.join(DOCS_DIR, '.vitepress');

/** 旧文档站跳转页里指向的已删除文档，统一落到当前最接近的页面。 */
export const REMOVED_DOC_FALLBACKS = {
  '对话系统/离线语音识别 (Sherpa-ncnn)': 'chat/input-waveform',
  '对话系统/语音输入与液态玻璃专项交接 2026-10-31': 'chat/input-waveform',
  '对话系统/离线ASR模型调研 2026-10-31': 'chat/input-waveform',
  '对话系统/X-ASR-zh-en 模型替换计划 2026-10-31': 'chat/input-waveform',
};

export async function readJson(file) {
  return JSON.parse(stripBom(await readFile(file, 'utf8')));
}

export async function writeJson(file, value) {
  const { writeFile, mkdir } = await import('node:fs/promises');
  await mkdir(path.dirname(file), { recursive: true });
  await writeFile(file, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

export function stripBom(text) {
  return text.replace(/^\uFEFF/, '');
}

/** 去掉 frontmatter，返回正文。 */
export function stripFrontmatter(text) {
  return stripBom(text).replace(/^---\r?\n[\s\S]*?\r?\n---\r?\n/, '');
}

/** 提取文档的一级标题。 */
export function extractTitle(text) {
  const body = stripFrontmatter(text);
  const match = body.match(/^\s{0,3}#\s+(.+?)\s*$/m);
  return match ? match[1].replace(/\s+#+\s*$/, '').trim() : null;
}

/** 是否存在一级标题（用于构建期校验）。 */
export function hasH1(text) {
  return extractTitle(text) !== null;
}

async function walk(dir, prefix = '') {
  const entries = await readdir(dir, { withFileTypes: true });
  const files = [];

  for (const entry of entries) {
    if (entry.name.startsWith('.') || entry.name === 'public' || entry.name === 'node_modules') {
      continue;
    }

    if (entry.isDirectory()) {
      files.push(...(await walk(path.join(dir, entry.name), `${prefix}${entry.name}/`)));
      continue;
    }

    if (entry.isFile() && entry.name.endsWith('.md') && !entry.name.startsWith('._')) {
      files.push(`${prefix}${entry.name}`);
    }
  }

  return files;
}

/** 列出 docs/ 下全部文档（不含 docs/index.md 首页）。 */
export async function listDocs() {
  const files = await walk(DOCS_DIR);
  return files
    .filter((file) => file !== 'index.md')
    .sort()
    .map((file) => ({
      file: file.replace(/\.md$/, ''),
      path: path.join(DOCS_DIR, file),
    }));
}

/** 读取文档标题。 */
export async function readDocTitle(doc) {
  return extractTitle(await readFile(doc.path, 'utf8'));
}

/**
 * 解析旧文档站的路由表。
 * 优先读冻结的 JSON 快照（旧 docs-center.html 删除后仍可构建），
 * 没有快照时回退到解析 `docs-center.html`。
 */
export async function readLegacyRoutes() {
  const snapshot = path.join(VITEPRESS_DIR, 'legacy-routes.json');

  if (existsSync(snapshot)) {
    return await readJson(snapshot);
  }

  const html = await readFile(path.join(siteRoot, 'docs-center.html'), 'utf8');

  const items = [];
  const itemPattern = /^\s*\{ id: '([^']+)',[^\n]*?docPath: '([^']+)'/gm;
  for (const match of html.matchAll(itemPattern)) {
    items.push({ id: match[1], file: match[2] });
  }

  const aliasBlock = html.slice(
    html.indexOf('const ROUTE_ALIASES = {'),
    html.indexOf('const HOME_CARDS'),
  );
  const aliases = {};
  for (const match of aliasBlock.matchAll(/(?:'([^']+)'|([A-Za-z_$][\w$]*))\s*:\s*'([^']+)'/g)) {
    aliases[match[1] ?? match[2]] = match[3];
  }

  const parsed = { items, aliases };
  await writeJson(snapshot, parsed);
  return parsed;
}

/** 读取旧站 hash 路由 id → 新站 slug 的完整映射。 */
export async function buildLegacyRouteMap() {
  const slugs = await readJson(path.join(VITEPRESS_DIR, 'slugs.json'));
  const { items, aliases } = await readLegacyRoutes();

  const byId = new Map();
  const removed = [];

  for (const item of items) {
    const slug = slugs[item.file];
    if (slug) {
      byId.set(item.id, slug);
    } else if (REMOVED_DOC_FALLBACKS[item.file]) {
      byId.set(item.id, REMOVED_DOC_FALLBACKS[item.file]);
      removed.push(item.file);
    }
  }

  for (const [alias, target] of Object.entries(aliases)) {
    const slug = byId.get(target);
    if (slug) byId.set(alias, slug);
  }

  return {
    routes: Object.fromEntries([...byId.entries()].sort(([a], [b]) => a.localeCompare(b))),
    removed: [...new Set(removed)],
    items,
    aliases,
  };
}

#!/usr/bin/env node
/**
 * 站点一致性校验：在构建前发现路由、侧栏、图标与文档死链问题。
 *
 * 用法：npm run check
 */
import { access, readFile, readdir } from 'node:fs/promises';
import path from 'node:path';
import {
  buildLegacyRouteMap,
  DOCS_DIR,
  listDocs,
  readDocTitle,
  readJson,
  repoRoot,
  siteRoot,
  VITEPRESS_DIR,
} from './lib/docs-data.mjs';

const errors = [];
const warnings = [];
const checks = [];

function fail(message) {
  errors.push(message);
}

function warn(message) {
  warnings.push(message);
}

function pass(message) {
  checks.push(message);
}

async function fileExistsExact(file) {
  try {
    await access(file);
  } catch {
    return false;
  }

  const entries = await readdir(path.dirname(file));
  return entries.includes(path.basename(file));
}

function isExternal(target) {
  return (
    /^(?:[a-z][a-z\d+.-]*:)/i.test(target) ||
    target.startsWith('#') ||
    target.startsWith('//')
  );
}

/** 解析一行里 Markdown 链接的目标，支持文件名中的成对括号。 */
function parseMarkdownTargets(line) {
  const targets = [];
  let cursor = 0;

  while ((cursor = line.indexOf('](', cursor)) !== -1) {
    let depth = 1;
    let index = cursor + 2;
    let value = '';

    while (index < line.length) {
      const char = line[index];

      if (char === '\\') {
        value += line[index + 1] ?? '';
        index += 2;
        continue;
      }

      if (char === '(') {
        depth += 1;
        value += char;
        index += 1;
        continue;
      }

      if (char === ')') {
        depth -= 1;
        if (depth === 0) break;
        value += char;
        index += 1;
        continue;
      }

      value += char;
      index += 1;
    }

    if (depth === 0) {
      const destination = value.trim().replace(/^<|>$/g, '');
      // 去掉可选的 title："path" / 'path' / (path)
      const cleaned = destination.split(/\s+(?=["'(])/)[0];
      if (cleaned) targets.push(cleaned);
    }

    cursor = index + 1;
  }

  return targets;
}

/** VitePress 会把 markdown 编译成 Vue 模板，未闭合的 HTML 标签会直接构建失败。 */
const VOID_TAGS = new Set([
  'area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input',
  'link', 'meta', 'param', 'source', 'track', 'wbr',
]);

function checkHtmlBalance(doc, text) {
  const lines = text.split(/\r?\n/);
  const stack = [];
  let inFence = false;
  let inComment = false;

  for (let index = 0; index < lines.length; index += 1) {
    let line = lines[index];

    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;

    line = line.replace(/`[^`]*`/g, '').replace(/\\</g, '');

    if (inComment) {
      if (line.includes('-->')) inComment = false;
      continue;
    }
    if (line.includes('<!--')) {
      inComment = !line.includes('-->', line.indexOf('<!--'));
      continue;
    }

    for (const match of line.matchAll(/<(\/?)([a-zA-Z][a-zA-Z0-9-]*)([^>]*)>/g)) {
      const [, closing, rawName, rest] = match;
      const name = rawName.toLowerCase();

      // 跳过 <https://...> 与 <mailto:...> 这类自动链接
      const inner = match[0].slice(1, -1);
      if (/^[a-z][a-z0-9+.-]*:\/\//i.test(inner) || /^[^\s<>@]+@[^\s<>]+$/.test(inner)) {
        continue;
      }

      if (VOID_TAGS.has(name) || rest.trimEnd().endsWith('/')) continue;

      if (closing) {
        if (stack.at(-1)?.name === name) {
          stack.pop();
        } else {
          fail(`HTML 标签未正确闭合：docs/${doc.file}.md:${index + 1} → </${name}>`);
        }
      } else {
        stack.push({ name, line: index + 1 });
      }
    }
  }

  for (const open of stack) {
    fail(`HTML 标签缺少结束标签：docs/${doc.file}.md:${open.line} → <${open.name}>`);
  }
}

/** 检查单个 Markdown 文件里的相对链接与图片。 */
async function checkDocLinks(doc) {
  const text = (await readFile(doc.path, 'utf8')).replace(/^\uFEFF/, '');
  const lines = text.split(/\r?\n/);
  const dir = path.dirname(doc.path);
  let inFence = false;
  let count = 0;

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];

    if (/^\s*(```|~~~)/.test(line)) {
      inFence = !inFence;
      continue;
    }
    if (inFence) continue;

    const cleaned = line.replace(/`[^`]*`/g, '');
    const targets = [
      ...parseMarkdownTargets(cleaned),
      ...cleaned.matchAll(/\b(?:src|href)="([^"]+)"/g),
    ].map((match) => (typeof match === 'string' ? match : match[1]));

    for (const rawTarget of targets) {
      if (isExternal(rawTarget) || !rawTarget) continue;

      const target = decodeURIComponent(rawTarget.replace(/^<|>$/g, '').split('#')[0].split('?')[0]);
      if (!target) continue;

      count += 1;
      const resolved = target.startsWith('/')
        ? path.join(DOCS_DIR, target)
        : path.resolve(dir, target);

      if (!(await fileExistsExact(resolved))) {
        fail(`死链：docs/${doc.file}.md:${index + 1} → ${rawTarget}`);
      } else if (!path.resolve(resolved).startsWith(DOCS_DIR + path.sep)) {
        warn(`链接指向文档目录之外（构建后失效）：docs/${doc.file}.md:${index + 1} → ${rawTarget}`);
      }
    }
  }

  checkHtmlBalance(doc, text);

  return count;
}

async function main() {
  const slugs = await readJson(path.join(VITEPRESS_DIR, 'slugs.json'));
  const categories = await readJson(path.join(VITEPRESS_DIR, 'categories.json'));
  const docs = await listDocs();
  const docFiles = new Set(docs.map((doc) => doc.file));

  // 1. slug 覆盖
  for (const file of docFiles) {
    if (!slugs[file]) fail(`文档缺少 slug：${file}`);
  }
  for (const file of Object.keys(slugs)) {
    if (!docFiles.has(file)) fail(`slugs.json 存在失效条目：${file}`);
  }

  const slugOwner = new Map();
  for (const [file, slug] of Object.entries(slugs)) {
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*\/[a-z0-9]+(?:-[a-z0-9]+)*$/.test(slug)) {
      fail(`slug 不是英文短横线格式：${file} → ${slug}`);
    }
    if (slugOwner.has(slug)) {
      fail(`slug 重复：${slug}（${file} 与 ${slugOwner.get(slug)}）`);
    }
    slugOwner.set(slug, file);
  }
  pass(`${docs.length} 篇文档，${Object.keys(slugs).length} 个 slug，无重复`);

  // 2. 分类与侧栏
  const seen = new Set();
  for (const category of categories) {
    for (const item of category.items) {
      if (!docFiles.has(item.file)) {
        fail(`分类「${category.title}」引用了不存在的文档：${item.file}`);
        continue;
      }
      if (seen.has(item.file)) fail(`文档在侧栏中重复出现：${item.file}`);
      seen.add(item.file);
      if (!slugs[item.file]) fail(`分类条目缺少 slug：${item.file}`);
    }
  }
  for (const file of docFiles) {
    if (!seen.has(file)) fail(`文档未出现在侧栏：${file}`);
  }
  pass(`${categories.length} 个分类覆盖全部文档`);

  // 3. 标题与 BOM
  for (const doc of docs) {
    const raw = await readFile(doc.path, 'utf8');
    if (raw.startsWith('\uFEFF')) {
      warn(`文件含 BOM，建议转为无 BOM UTF-8：docs/${doc.file}.md`);
    }
    if (!(await readDocTitle(doc))) {
      fail(`缺少一级标题：docs/${doc.file}.md`);
    }
  }

  // 4. 文档内链接
  let linkCount = 0;
  for (const doc of docs) {
    linkCount += await checkDocLinks(doc);
  }
  pass(`检查 ${linkCount} 条站内链接`);

  // 5. 应用图标
  const apps = await readJson(path.join(siteRoot, 'site/src/data/apps.json'));
  if (!Array.isArray(apps) || apps.length === 0) {
    fail('apps.json 为空');
  } else {
    for (const app of apps) {
      if (!app.name || !app.icon) {
        fail(`apps.json 条目缺少 name/icon：${JSON.stringify(app)}`);
        continue;
      }
      const icon = path.join(siteRoot, 'site/public', app.icon.replace(/^\//, ''));
      if (!(await fileExistsExact(icon))) {
        fail(`应用图标不存在：${app.name} → ${app.icon}`);
      }
    }
    pass(`${apps.length} 个应用图标全部存在`);
  }

  // 6. 版本数据
  const site = await readJson(path.join(siteRoot, 'site/src/data/site.generated.json'));
  for (const key of ['domain', 'docsUrl', 'github', 'githubReleases', 'release', 'dev']) {
    if (!site[key]) fail(`site.generated.json 缺少字段：${key}`);
  }
  if (site.release && !site.release.apkUrl) fail('site.generated.json 缺少 release.apkUrl');
  pass(`版本数据：${site.release?.tag} / 开发版 ${site.dev?.versionName}`);

  // 7. 旧路由兼容层
  const legacy = await buildLegacyRouteMap();
  const legacyCount = Object.keys(legacy.routes).length;
  if (legacyCount === 0) fail('旧路由映射为空');
  for (const [id, slug] of Object.entries(legacy.routes)) {
    if (!slugOwner.has(slug)) fail(`旧路由 ${id} 指向不存在的 slug：${slug}`);
  }
  pass(`${legacyCount} 条旧 hash 路由全部有落点（已删除文档 ${legacy.removed.length} 篇）`);

  for (const artifact of ['site/public/docs-center.html', 'site/public/_redirects']) {
    try {
      await access(path.join(siteRoot, artifact));
    } catch {
      fail(`缺少生成产物：${artifact}（先运行 npm run build:legacy）`);
    }
  }

  // 8. 旧 README 里的文档链接
  try {
    const readme = await readFile(path.join(repoRoot, 'README.md'), 'utf8');
    for (const match of readme.matchAll(/\]\((Aries-site\/docs\/[^)\s]+\.md)\)/g)) {
      const target = decodeURIComponent(match[1]);
      if (!(await fileExistsExact(path.join(repoRoot, target)))) {
        fail(`README.md 指向不存在的文档：${target}`);
      }
    }
  } catch {
    warn('未找到 README.md，跳过检查');
  }

  for (const message of checks) console.log(`[check] ✓ ${message}`);
  for (const message of warnings) console.warn(`[check] ! ${message}`);
  for (const message of errors) console.error(`[check] ✗ ${message}`);

  if (errors.length) {
    console.error(`\n[check] 失败：${errors.length} 个错误 / ${warnings.length} 个警告`);
    process.exitCode = 1;
  } else {
    console.log(`\n[check] 通过（${warnings.length} 个警告）`);
  }
}

main().catch((error) => {
  console.error('[check] 执行失败：', error);
  process.exitCode = 1;
});

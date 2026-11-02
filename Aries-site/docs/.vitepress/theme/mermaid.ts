/**
 * Mermaid 按需渲染。
 *
 * 文档里有 190+ 张图，构建期全部渲染会显著拖慢构建，
 * 也会让首屏加载巨大的 SVG。这里保留 markdown 里的原始代码块，
 * 在浏览器端用 IntersectionObserver 只渲染进入视口附近的图表。
 */

import { createZoomChrome } from './zoom';

type MermaidApi = typeof import('mermaid').default;

const MERMAID_SELECTOR = 'div.language-mermaid';
const RENDERED_CLASS = 'mermaid-block';
const FONT_FAMILY =
  '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans SC", sans-serif';

const codeStore = new WeakMap<Element, string>();
const pending = new WeakSet<Element>();

let mermaidPromise: Promise<MermaidApi> | null = null;
let initializedTheme: 'dark' | 'light' | null = null;
let intersectionObserver: IntersectionObserver | null = null;
let mutationObserver: MutationObserver | null = null;
let scanFrame = 0;
let renderCount = 0;

function currentTheme(): 'dark' | 'light' {
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light';
}

function mermaidOptions(theme: 'dark' | 'light') {
  return {
    startOnLoad: false,
    securityLevel: 'strict' as const,
    theme: theme === 'dark' ? 'dark' : 'default',
    fontFamily: FONT_FAMILY,
    flowchart: { useMaxWidth: true, htmlLabels: true, curve: 'basis' as const },
    sequence: { useMaxWidth: true },
    gantt: { useMaxWidth: true },
    class: { useMaxWidth: true },
    state: { useMaxWidth: true },
    er: { useMaxWidth: true },
  };
}

async function loadMermaid(): Promise<MermaidApi> {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((module) => {
      const mermaid = module.default;
      mermaid.initialize(mermaidOptions(currentTheme()));
      initializedTheme = currentTheme();
      return mermaid;
    });
  }
  return mermaidPromise;
}

function ensureObserver(): IntersectionObserver {
  if (intersectionObserver) return intersectionObserver;

  intersectionObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        intersectionObserver?.unobserve(entry.target);
        const block = entry.target as HTMLElement;
        const code = codeStore.get(block);
        if (!code) continue;
        void renderBlock(block, code);
      }
    },
    { rootMargin: '400px 0px' },
  );

  return intersectionObserver;
}

function ensureMermaidCanvas(block: HTMLElement): HTMLElement {
  const existing = block.querySelector<HTMLElement>('.mermaid-canvas');
  if (existing) return existing;

  block.textContent = '';
  block.classList.add('md-zoom', 'mermaid-block--interactive');

  const canvas = document.createElement('div');
  canvas.className = 'mermaid-canvas';
  createZoomChrome(block, canvas, 'Mermaid 图表');
  return canvas;
}

async function renderBlock(block: HTMLElement, code: string): Promise<void> {
  if (pending.has(block)) return;
  pending.add(block);

  try {
    const mermaid = await loadMermaid();

    const theme = currentTheme();
    if (initializedTheme !== theme) {
      mermaid.initialize(mermaidOptions(theme));
      initializedTheme = theme;
    }

    renderCount += 1;
    const id = `aries-mermaid-${renderCount}`;
    const { svg } = await mermaid.render(id, code);

    const canvas = ensureMermaidCanvas(block);
    canvas.innerHTML = svg;
    block.dataset.state = 'done';
  } catch (error) {
    block.dataset.state = 'error';
    block.classList.add('mermaid-error');
    const canvas = block.querySelector<HTMLElement>('.mermaid-canvas');
    if (canvas) {
      canvas.innerHTML = '<div class="mermaid-loading">图表渲染失败，请查看控制台日志。</div>';
    }
    console.warn('[aries] Mermaid 渲染失败，已保留原始代码块', error);
  } finally {
    pending.delete(block);
  }
}

function scan(root: ParentNode): void {
  const blocks = root.querySelectorAll<HTMLElement>(MERMAID_SELECTOR);
  if (!blocks.length) return;

  const observer = ensureObserver();

  for (const block of blocks) {
    if (block.dataset.mermaidReady === 'true') continue;

    const code = block.querySelector('code')?.textContent ?? '';
    if (!code.trim()) continue;

    block.dataset.mermaidReady = 'true';
    block.dataset.state = 'idle';
    block.classList.add(RENDERED_CLASS);
    codeStore.set(block, code);

    // 原始代码块先保持在页面中，等 SVG 渲染完成再替换，避免无 JS 时白屏。
    observer.observe(block);
  }
}

function scheduleScan(): void {
  if (scanFrame) return;
  scanFrame = window.requestAnimationFrame(() => {
    scanFrame = 0;
    scan(document.body);
  });
}

function rerenderAll(): void {
  const blocks = document.querySelectorAll<HTMLElement>(`.${RENDERED_CLASS}[data-state="done"]`);
  for (const block of blocks) {
    const code = codeStore.get(block);
    const canvas = block.querySelector<HTMLElement>('.mermaid-canvas');
    if (!code || !canvas) continue;
    block.dataset.state = 'pending';
    canvas.innerHTML = '<div class="mermaid-loading">图表重绘中…</div>';
    void renderBlock(block, code);
  }
}

function watchTheme(): void {
  let last = currentTheme();
  mutationObserver ??= new MutationObserver(scheduleScan);

  const themeWatcher = new MutationObserver(() => {
    const next = currentTheme();
    if (next === last) return;
    last = next;
    rerenderAll();
  });

  themeWatcher.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class'],
  });
}

export function initMermaid(): void {
  if (typeof window === 'undefined') return;

  scan(document.body);

  if (!mutationObserver) {
    mutationObserver = new MutationObserver(scheduleScan);
    watchTheme();
  }

  mutationObserver.disconnect();
  mutationObserver.observe(document.body, { childList: true, subtree: true });

  // 路由切换后内容替换是一次性批量 DOM 变更，再补一次延迟扫描兜底。
  window.setTimeout(scheduleScan, 350);
  window.setTimeout(scheduleScan, 1200);
}

export function disposeMermaid(): void {
  intersectionObserver?.disconnect();
  intersectionObserver = null;
  mutationObserver?.disconnect();
  mutationObserver = null;
}

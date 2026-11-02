/**
 * Markdown 内容缩放/滚动增强（GitHub 式）。
 *
 * - Mermaid 图表在 mermaid.ts 渲染完成后调用 createZoomChrome，支持 50%–300% 缩放。
 * - 表格在客户端包一层滚动视口，支持同样的缩放和纵向滚动，并固定表头。
 *
 * 缩放机制按内容分两种：
 * - 表格用 CSS `zoom`：布局真实参与滚动尺寸计算，且不破坏 position:sticky 表头。
 *   （实测 Chromium 156 中 zoom 对「百分比宽度 + SVG 撑高的 auto 尺寸」元素不参与
 *   布局——mermaid 图表正是这种结构，放大后无法滚动，所以不能用同一条路。）
 * - Mermaid 用 transform: scale() + JS 尺寸补偿（.md-zoom-sizer 撑出真实滚动区域），
 *   canvas 脱离常规流，宽度由这里显式管理，双方向滚动在所有浏览器行为一致。
 */

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 3;
const ZOOM_STEP = 0.25;

type ZoomAction = 'in' | 'out' | 'reset';

/** canvas 缩放策略：fill = transform+sizer（mermaid）；flow = CSS zoom 参与布局（表格）。 */
type SizeMode = 'fill' | 'flow';

function createZoomButton(
  action: ZoomAction,
  label: string,
  text: string,
  className = 'md-zoom-btn',
): HTMLButtonElement {
  const button = document.createElement('button');
  button.type = 'button';
  button.className = className;
  button.dataset.zoom = action;
  button.setAttribute('aria-label', label);
  button.title = label;
  button.textContent = text;
  return button;
}

/**
 * 给一个容器挂上缩放工具栏和可滚动视口。
 *
 * fill 模式结构：viewport > sizer（撑出缩放后的滚动尺寸）> canvas（transform 缩放）。
 * canvas 脱离常规流改为绝对定位，宽度由这里按视口内容宽显式管理；
 * flow 模式保持 canvas 在常规流内，由 CSS `zoom` 直接缩放布局。
 */
export function createZoomChrome(
  frame: HTMLElement,
  canvas: HTMLElement,
  label: string,
  mode: SizeMode = 'fill',
): HTMLElement {
  const bar = document.createElement('div');
  bar.className = 'md-zoom-bar';

  const outButton = createZoomButton('out', '缩小', '−');
  const levelButton = createZoomButton('reset', '重置缩放', '100%', 'md-zoom-level');
  levelButton.setAttribute('aria-live', 'polite');
  const inButton = createZoomButton('in', '放大', '+');

  bar.append(outButton, levelButton, inButton);

  const viewport = document.createElement('div');
  viewport.className = 'md-zoom-viewport';
  viewport.tabIndex = 0;
  viewport.setAttribute('role', 'region');
  viewport.setAttribute('aria-label', `${label}，可缩放并使用方向键滚动查看`);

  let zoom = 1;
  let updateSizer: (() => void) | null = null;

  if (mode === 'fill') {
    canvas.classList.add('md-zoom-canvas');

    const sizer = document.createElement('div');
    sizer.className = 'md-zoom-sizer';
    sizer.append(canvas);
    viewport.append(sizer);

    /** 视口内容宽（扣除左右 padding，滚动条由 clientWidth 排除）。 */
    const availWidth = (): number => {
      const cs = getComputedStyle(viewport);
      return Math.max(
        0,
        viewport.clientWidth - parseFloat(cs.paddingLeft || '0') - parseFloat(cs.paddingRight || '0'),
      );
    };

    /** 重新测量 canvas 的自然布局宽度并落成显式 px。 */
    const layout = (): void => {
      canvas.style.width = `${availWidth()}px`;
      updateSizer?.();
    };

    /** sizer = canvas 布局尺寸 × zoom：offsetWidth/Height 不受 transform 影响。 */
    updateSizer = (): void => {
      sizer.style.width = `${canvas.offsetWidth * zoom}px`;
      sizer.style.height = `${canvas.offsetHeight * zoom}px`;
    };

    // 窗口/容器宽度变化、svg 重绘（高度变化）时重新测量。
    const resizeObserver = new ResizeObserver(() => layout());
    resizeObserver.observe(viewport);
    resizeObserver.observe(canvas);
    layout();
  } else {
    viewport.append(canvas);
  }

  frame.append(bar, viewport);

  const apply = (next: number): void => {
    zoom = Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, Math.round(next * 100) / 100));
    canvas.style.setProperty('--md-zoom', String(zoom));
    levelButton.textContent = `${Math.round(zoom * 100)}%`;
    levelButton.setAttribute(
      'aria-label',
      `当前缩放 ${Math.round(zoom * 100)}%，点击重置`,
    );
    frame.dataset.zoom = String(zoom);
    updateSizer?.();
  };

  bar.addEventListener('click', (event) => {
    const target = event.target as HTMLElement | null;
    const button = target?.closest<HTMLButtonElement>('button[data-zoom]');
    if (!button) return;

    event.preventDefault();
    const action = button.dataset.zoom as ZoomAction | undefined;
    if (action === 'in') apply(zoom + ZOOM_STEP);
    else if (action === 'out') apply(zoom - ZOOM_STEP);
    else apply(1);
  });

  apply(1);
  return viewport;
}

function enhanceTable(table: HTMLTableElement): void {
  if (table.closest('.md-zoom--table')) return;

  const parent = table.parentNode;
  if (!parent) return;

  const frame = document.createElement('div');
  frame.className = 'md-zoom md-zoom--table';

  const caption = table.querySelector('caption')?.textContent?.trim();
  const label = caption ? `表格：${caption}` : '表格';

  parent.insertBefore(frame, table);
  createZoomChrome(frame, table, label, 'flow');
}

let observer: MutationObserver | null = null;
let scanFrame = 0;

function scheduleScan(): void {
  if (scanFrame) return;
  scanFrame = window.requestAnimationFrame(() => {
    scanFrame = 0;
    document
      .querySelectorAll<HTMLTableElement>('.vp-doc table')
      .forEach(enhanceTable);
  });
}

export function initTableZoom(): void {
  if (typeof window === 'undefined') return;

  document.querySelectorAll<HTMLTableElement>('.vp-doc table').forEach(enhanceTable);

  observer ??= new MutationObserver(scheduleScan);
  observer.disconnect();
  observer.observe(document.body, { childList: true, subtree: true });
}

export function disposeTableZoom(): void {
  observer?.disconnect();
  observer = null;
  if (scanFrame) {
    window.cancelAnimationFrame(scanFrame);
    scanFrame = 0;
  }
}

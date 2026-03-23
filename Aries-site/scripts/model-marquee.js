/**
 * 模型提供商无限滚动展示模块
 * 使用官方品牌图标
 */

(function () {
  'use strict';

  const ICON_BASE = './assets/icons/models/';
  const MOBILE_BREAKPOINT = 640;
  const TABLET_BREAKPOINT = 1024;

  // 官方图标 - 使用可国内访问的镜像
  const OFFICIAL_ICONS = {
    Gemini: './assets/icons/models/gemini.svg',
    Llama: './assets/icons/models/meta.com.ico',
    // 小米 MIMO - 使用高清 Logo
    MIMO: './assets/icons/models/mimo.png',
    OpenAI: './assets/icons/models/openai.com.ico',
  };

  // 模型提供商数据
  const MODEL_PROVIDERS = [
    // 国内厂商 - 使用 qc-ai.cn
    { name: 'DeepSeek', icon: 'DeepSeek.svg' },
    { name: 'Ernie', icon: 'ERNIE.svg' },
    { name: 'GLM', icon: 'GLM.svg' },
    { name: 'HunYuan', icon: 'hunyuan-color.svg' },
    { name: 'Kimi', icon: 'kimi-k2.png' },
    { name: 'MiniMax', icon: 'minimax.jpg' },
    { name: 'Qwen', icon: 'Qwen.svg' },
    { name: 'Doubao', icon: 'doubao-color.svg' },
    { name: 'Kolors', icon: 'kolors-color.svg' },
    { name: '即梦', icon: 'jimeng-color.svg' },
    { name: 'bge', icon: 'BAAI.svg' },
    // 国际厂商 - 使用官方图标
    { name: 'OpenAI', icon: OFFICIAL_ICONS.OpenAI, type: 'official' },
    { name: 'Gemini', icon: OFFICIAL_ICONS.Gemini, type: 'official' },
    { name: 'Claude', icon: './assets/icons/models/anthropic.com.ico', type: 'external' },
    { name: 'Llama', icon: OFFICIAL_ICONS.Llama, type: 'official' },
    { name: 'Mistral', icon: './assets/icons/models/mistral.ai.ico', type: 'external' },
    { name: 'MIMO', icon: OFFICIAL_ICONS.MIMO, type: 'official' },
  ];

  /**
   * 创建模型提供商卡片 HTML
   */
  function createProviderCard(provider) {
    const iconUrl = provider.type === 'official' || provider.type === 'external'
      ? provider.icon
      : ICON_BASE + provider.icon;

    return `
      <div class="model-provider-item">
        <span class="model-provider-name">${provider.name}</span>
        <span class="model-provider-icon-shell" aria-hidden="true">
          <img class="model-provider-icon" src="${iconUrl}" alt="${provider.name}" width="68" height="68" loading="eager" decoding="async" onerror="this.parentElement.style.display='none'">
        </span>
      </div>
    `;
  }

  function getPixelsPerSecond() {
    if (window.innerWidth <= MOBILE_BREAKPOINT) return 52;
    if (window.innerWidth <= TABLET_BREAKPOINT) return 58;
    return 66;
  }

  function waitForImage(img) {
    if (img.complete && img.naturalWidth > 0) {
      return typeof img.decode === 'function' ? img.decode().catch(() => { }) : Promise.resolve();
    }

    return new Promise((resolve) => {
      let settled = false;
      const done = () => {
        if (settled) return;
        settled = true;
        if (typeof img.decode === 'function' && img.complete && img.naturalWidth > 0) {
          img.decode().catch(() => { }).finally(resolve);
          return;
        }
        resolve();
      };

      img.addEventListener('load', done, { once: true });
      img.addEventListener('error', done, { once: true });
    });
  }

  function waitForImages(root) {
    const images = Array.from(root.querySelectorAll('img'));
    if (!images.length) return Promise.resolve();
    return Promise.all(images.map(waitForImage)).then(() => { });
  }

  function waitForFonts() {
    if (!document.fonts || typeof document.fonts.ready?.then !== 'function') {
      return Promise.resolve();
    }

    return document.fonts.ready.catch(() => { });
  }

  function createSegment(items) {
    const segment = document.createElement('div');
    segment.className = 'model-marquee-segment';
    segment.innerHTML = items.map(createProviderCard).join('');
    return segment;
  }

  function randomizeProviders() {
    return [...MODEL_PROVIDERS]
      .map((provider) => ({ provider, sort: Math.random() }))
      .sort((a, b) => a.sort - b.sort)
      .map(({ provider }) => provider);
  }

  function updateTrackMetrics(track, segment) {
    const distance = segment.getBoundingClientRect().width;
    if (!distance) return;

    const duration = Math.max(distance / getPixelsPerSecond(), 24);
    track.style.setProperty('--marquee-distance', `${distance.toFixed(2)}px`);
    track.style.setProperty('--marquee-duration', `${duration.toFixed(2)}s`);
    track.classList.add('is-ready');
    track.style.animationPlayState = 'running';
  }

  /**
   * 初始化滚动
   */
  function initMarqueeRow(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const track = container.querySelector('.model-marquee-track');
    if (!track) return;

    if (container._modelMarqueeCleanup) {
      container._modelMarqueeCleanup();
    }

    track.classList.remove('is-ready');
    track.classList.add('scroll-left');
    track.style.animationPlayState = 'paused';

    const segment = createSegment(randomizeProviders());
    track.replaceChildren(segment);

    let resizeFrame = 0;
    const scheduleMeasure = () => {
      cancelAnimationFrame(resizeFrame);
      resizeFrame = requestAnimationFrame(() => updateTrackMetrics(track, segment));
    };

    const cloneAndMeasure = () => {
      const clone = segment.cloneNode(true);
      clone.setAttribute('aria-hidden', 'true');
      track.appendChild(clone);
      scheduleMeasure();
    };

    Promise.all([waitForImages(segment), waitForFonts()])
      .catch(() => { })
      .finally(() => {
        cloneAndMeasure();
      });

    let resizeObserver = null;
    if ('ResizeObserver' in window) {
      resizeObserver = new ResizeObserver(scheduleMeasure);
      resizeObserver.observe(container);
      resizeObserver.observe(segment);
    } else {
      window.addEventListener('resize', scheduleMeasure, { passive: true });
    }

    container._modelMarqueeCleanup = () => {
      cancelAnimationFrame(resizeFrame);
      if (resizeObserver) {
        resizeObserver.disconnect();
      } else {
        window.removeEventListener('resize', scheduleMeasure);
      }
    };
  }

  /**
   * 初始化
   */
  function initModelMarquee() {
    initMarqueeRow('model-marquee-row-1');
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initModelMarquee);
  } else {
    initModelMarquee();
  }

  window.initModelMarquee = initModelMarquee;
})();

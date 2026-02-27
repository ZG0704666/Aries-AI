/**
 * 模型提供商无限滚动展示模块
 * 使用官方品牌图标
 */

(function () {
  'use strict';

  const ICON_BASE = 'https://assets.qc-ai.cn/maashub/icons/models/';

  // 官方图标 - 使用可靠的 CDN
  const OFFICIAL_ICONS = {
    // Google Gemini - 使用 Gemini 专用 Logo
    Gemini: 'https://www.gstatic.com/lamda/images/gemini_sparkle_v002_d4735304ff6292a690345.svg',
    // Meta Llama - 使用 DuckDuckGo favicon 服务
    Llama: 'https://icons.duckduckgo.com/ip3/meta.com.ico',
    // 小米 MIMO - 使用高清 Logo
    MIMO: 'https://s02.mifile.cn/assets/static/image/logo-mi2.png',
    // OpenAI - 使用 DuckDuckGo favicon 服务
    OpenAI: 'https://icons.duckduckgo.com/ip3/openai.com.ico',
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
    { name: 'Claude', icon: 'https://icons.duckduckgo.com/ip3/anthropic.com.ico', type: 'external' },
    { name: 'Llama', icon: OFFICIAL_ICONS.Llama, type: 'official' },
    { name: 'Mistral', icon: 'https://icons.duckduckgo.com/ip3/mistral.ai.ico', type: 'external' },
    { name: 'MIMO', icon: OFFICIAL_ICONS.MIMO, type: 'official' },
  ];

  /**
   * 创建模型提供商卡片 HTML
   */
  function createProviderCard(provider) {
    let iconUrl;

    if (provider.type === 'official' || provider.type === 'external') {
      iconUrl = provider.icon;
    } else {
      iconUrl = ICON_BASE + provider.icon;
    }

    return `
      <div class="model-provider-item">
        <span class="model-provider-name">${provider.name}</span>
        <img class="model-provider-icon" src="${iconUrl}" alt="${provider.name}" loading="lazy" onerror="this.style.display='none'">
      </div>
    `;
  }

  /**
   * 初始化滚动
   */
  function initMarqueeRow(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const track = container.querySelector('.model-marquee-track');
    if (!track) return;

    const shuffled = [...MODEL_PROVIDERS].sort(() => Math.random() - 0.5);

    let baseContent = '';
    shuffled.forEach(provider => {
      baseContent += createProviderCard(provider);
    });

    track.innerHTML = baseContent.repeat(6);
    track.classList.add('scroll-left');

    preloadImages(track, () => {
      track.style.animationPlayState = 'running';
    });
  }

  /**
   * 预加载图片
   */
  function preloadImages(track, callback) {
    const images = track.querySelectorAll('img');
    let loaded = 0;
    const total = images.length;

    if (total === 0) {
      callback();
      return;
    }

    const checkLoaded = () => {
      loaded++;
      if (loaded >= total * 0.5) {
        callback();
      }
    };

    images.forEach(img => {
      if (img.complete) {
        checkLoaded();
      } else {
        img.onload = checkLoaded;
        img.onerror = checkLoaded;
      }
    });

    setTimeout(callback, 2000);
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

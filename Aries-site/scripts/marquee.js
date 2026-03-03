/**
 * 跑马灯效果模块
 * 负责应用列表的滚动展示 - Cloudflare优化版
 */

(function () {
  'use strict';

  const ARIES_DATA = window.ARIES_DATA || {
    apps: [
      '淘宝', '支付宝', '美团', '高德地图', '微信', 'QQ', '京东', '知乎', 'B站', '抖音', '小红书', '携程',
      '12306', '饿了么', '拼多多', '闲鱼', '快手', '网易云音乐', '微博', 'Keep', 'WPS', '大众点评', '滴滴出行', '百度地图',
      'QQ音乐', '腾讯视频', '爱奇艺', '优酷', '哔哩哔哩漫画', '淘宝特价版', '得物', '苏宁易购', '唯品会', '菜鸟',
      '豆瓣', '小宇宙', '喜马拉雅', '百度网盘', '夸克', 'UC浏览器', '百度', '今日头条', '腾讯新闻', '网易新闻',
      '微信读书', '飞书', '钉钉', '企业微信', '携程旅行', '去哪儿', '同程旅行', '滴滴',
    ],
  };

  // APP图标配置 - 使用Cloudflare友好的图标源
  const APP_ICONS = {
    '淘宝': { color: '#FF5000', domain: 'taobao.com' },
    '支付宝': { color: '#1677FF', domain: 'alipay.com' },
    '美团': { color: '#FFC300', domain: 'meituan.com' },
    '高德地图': { color: '#4285F4', domain: 'amap.com' },
    '微信': { color: '#07C160', domain: 'weixin.qq.com' },
    'QQ': { color: '#12B7F5', domain: 'im.qq.com' },
    '京东': { color: '#E4393C', domain: 'jd.com' },
    '知乎': { color: '#0084FF', domain: 'zhihu.com' },
    'B站': { color: '#FB7299', domain: 'bilibili.com' },
    '抖音': { color: '#000000', domain: 'douyin.com' },
    '小红书': { color: '#FF2442', domain: 'xiaohongshu.com' },
    '携程': { color: '#2577E3', domain: 'ctrip.com' },
    '12306': { color: '#0077C8', domain: '12306.cn' },
    '饿了么': { color: '#0085FF', domain: 'ele.me' },
    '拼多多': { color: '#E02E24', domain: 'pinduoduo.com' },
    '闲鱼': { color: '#FFDA44', domain: 'goofish.com' },
    '快手': { color: '#FF6600', domain: 'kuaishou.com' },
    '网易云音乐': { color: '#C20C0C', domain: 'music.163.com' },
    '微博': { color: '#E6162D', domain: 'weibo.com' },
    'Keep': { color: '#20C6B6', domain: 'gotokeep.com' },
    'WPS': { color: '#E60012', domain: 'wps.cn' },
    '大众点评': { color: '#FF9900', domain: 'dianping.com' },
    '滴滴出行': { color: '#FF7D00', domain: 'didiglobal.com' },
    '百度地图': { color: '#4E6EF2', domain: 'map.baidu.com' },
    'QQ音乐': { color: '#31C27C', domain: 'y.qq.com' },
    '腾讯视频': { color: '#00BE06', domain: 'v.qq.com' },
    '爱奇艺': { color: '#00BE06', domain: 'iqiyi.com' },
    '优酷': { color: '#1E90FF', domain: 'youku.com' },
    '哔哩哔哩漫画': { color: '#FB7299', domain: 'manga.bilibili.com' },
    '淘宝特价版': { color: '#FF5000', domain: 'taobao.com' },
    '得物': { color: '#46D7C8', domain: 'dewu.com' },
    '苏宁易购': { color: '#F8A51D', domain: 'suning.com' },
    '唯品会': { color: '#E8466C', domain: 'vip.com' },
    '菜鸟': { color: '#2EAB6F', domain: 'cainiao.com' },
    '豆瓣': { color: '#007722', domain: 'douban.com' },
    '小宇宙': { color: '#FFD200', domain: 'xiaoyuzhoufm.com' },
    '喜马拉雅': { color: '#E8380D', domain: 'ximalaya.com' },
    '百度网盘': { color: '#2932E1', domain: 'pan.baidu.com' },
    '夸克': { color: '#00B4FF', domain: 'quark.cn' },
    'UC浏览器': { color: '#FF6B00', domain: 'uc.cn' },
    '百度': { color: '#2932E1', domain: 'baidu.com' },
    '今日头条': { color: '#F04142', domain: 'toutiao.com' },
    '腾讯新闻': { color: '#00A3FF', domain: 'news.qq.com' },
    '网易新闻': { color: '#C20C0C', domain: 'news.163.com' },
    '微信读书': { color: '#1AAD19', domain: 'weread.qq.com' },
    '飞书': { color: '#3370FF', domain: 'feishu.cn' },
    '钉钉': { color: '#3370FF', domain: 'dingtalk.com' },
    '企业微信': { color: '#2BAD31', domain: 'work.weixin.qq.com' },
    '携程旅行': { color: '#2577E3', domain: 'ctrip.com' },
    '去哪儿': { color: '#00BCD4', domain: 'qunar.com' },
    '同程旅行': { color: '#00A4FF', domain: 'ly.com' },
    '滴滴': { color: '#FF7D00', domain: 'didiglobal.com' }
  };

  const APP_ICON_BASE = './assets/icons/apps/';

  // 本地图标源（离线可用）
  const ICON_SOURCES = [
    (domain) => `${APP_ICON_BASE}${domain}.ico`
  ];

  // 生成SVG图标作为备选
  function generateFallbackIcon(name, color) {
    const initials = name.substring(0, 1);
    const bgColor = color || '#666';

    return `data:image/svg+xml,${encodeURIComponent(`<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><defs><linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%"><stop offset="0%" style="stop-color:${bgColor}"/><stop offset="100%" style="stop-color:${adjustColor(bgColor, -30)}"/></linearGradient></defs><rect width="100" height="100" rx="22" fill="url(#g)"/><text x="50" y="68" font-family="system-ui,-apple-system,sans-serif" font-size="48" font-weight="600" fill="white" text-anchor="middle">${initials}</text></svg>`)}`;
  }

  function adjustColor(color, amount) {
    const num = parseInt(color.replace('#', ''), 16);
    const r = Math.max(0, Math.min(255, (num >> 16) + amount));
    const g = Math.max(0, Math.min(255, ((num >> 8) & 0x00FF) + amount));
    const b = Math.max(0, Math.min(255, (num & 0x0000FF) + amount));
    return '#' + ((r << 16) | (g << 8) | b).toString(16).padStart(6, '0');
  }

  function debounce(fn, delay) {
    let timer = null;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(this, args), delay);
    };
  }

  function initMarqueeHome() {
    const row1 = document.getElementById('marquee-row-1');
    const row2 = document.getElementById('marquee-row-2');
    if (!row1 || !row2) return;

    const appsRow1 = ARIES_DATA.apps.filter((_, i) => i % 2 === 0);
    const appsRow2 = ARIES_DATA.apps.filter((_, i) => i % 2 === 1);

    function buildBase(trackEl, items) {
      trackEl.innerHTML = '';
      for (const name of items) {
        const el = document.createElement('div');
        el.className = 'app-capsule';

        const config = APP_ICONS[name] || { color: '#666', domain: '' };
        const fallbackUrl = generateFallbackIcon(name, config.color);

        const iconWrapper = document.createElement('div');
        iconWrapper.className = 'app-icon-wrapper';
        iconWrapper.style.backgroundColor = config.color + '20';

        const img = document.createElement('img');
        img.alt = name;
        img.className = 'app-icon-img';
        img.loading = 'lazy';
        img.crossOrigin = 'anonymous';

        let sourceIndex = 0;

        function tryNextSource() {
          if (!config.domain || sourceIndex >= ICON_SOURCES.length) {
            img.src = fallbackUrl;
            return;
          }
          img.src = ICON_SOURCES[sourceIndex](config.domain);
          sourceIndex++;
        }

        img.onerror = function () {
          tryNextSource();
        };

        tryNextSource();

        iconWrapper.appendChild(img);

        const nameSpan = document.createElement('span');
        nameSpan.className = 'app-name';
        nameSpan.textContent = name;

        el.appendChild(iconWrapper);
        el.appendChild(nameSpan);
        trackEl.appendChild(el);
      }
    }

    function duplicateOnce(trackEl) {
      const copy = trackEl.innerHTML;
      trackEl.insertAdjacentHTML('beforeend', copy);
    }

    function initMarquee(wrapperId, items, direction) {
      const wrapper = document.getElementById(wrapperId);
      if (!wrapper) return;
      const track = wrapper.querySelector('.marquee-track');
      if (!track) return;

      function rebuild() {
        buildBase(track, items);
        let safety = 0;
        while (track.scrollWidth < window.innerWidth + 60 && safety < 12) {
          duplicateOnce(track);
          safety++;
        }
        duplicateOnce(track);

        track.classList.remove('marquee-left', 'marquee-right');
        track.classList.add(direction < 0 ? 'marquee-left' : 'marquee-right');
      }

      rebuild();
      window.addEventListener('resize', debounce(rebuild, 150));
    }

    initMarquee('marquee-row-1', appsRow1, -1);
    initMarquee('marquee-row-2', appsRow2, 1);
  }

  window.initMarqueeHome = initMarqueeHome;

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMarqueeHome);
  } else {
    initMarqueeHome();
  }
})();

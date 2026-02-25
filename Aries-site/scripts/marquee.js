/**
 * 跑马灯效果模块
 * 负责应用列表的滚动展示
 */

(function() {
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
        el.textContent = name;
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

  // 导出到全局
  window.initMarqueeHome = initMarqueeHome;

  // 自动初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initMarqueeHome);
  } else {
    initMarqueeHome();
  }
})();


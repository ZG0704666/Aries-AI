/**
 * 主入口文件
 * 负责协调各模块的初始化
 */

(function() {
  'use strict';

  function initHome() {
    if (window.initStars) window.initStars();
    if (window.initConstellation) window.initConstellation();
    if (window.initMarqueeHome) window.initMarqueeHome();
  }

  function initDocs() {
    if (window.initConstellation) window.initConstellation();
    if (window.initMeteorBackground) window.initMeteorBackground();
  }

  function boot() {
    const page = (document.body && document.body.dataset && document.body.dataset.page) || '';
    if (page === 'home') initHome();
    if (page === 'docs') initDocs();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();






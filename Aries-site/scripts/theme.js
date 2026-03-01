/**
 * 主题切换模块
 * 负责日间/夜间模式的切换和持久化
 */

(function() {
  'use strict';

  function applyThemeClass(theme) {
    const html = document.documentElement;
    html.classList.remove('dark', 'light');
    html.classList.add(theme);

    const label = document.getElementById('theme-label');
    if (label) label.textContent = theme === 'dark' ? '切换到日间' : '切换到夜间';
  }

  function getStoredTheme() {
    const saved = localStorage.getItem('theme');
    if (saved === 'dark' || saved === 'light') return saved;
    return 'dark';
  }

  function toggleTheme() {
    const current = getStoredTheme();
    const next = current === 'dark' ? 'light' : 'dark';
    localStorage.setItem('theme', next);
    applyThemeClass(next);
  }

  function initTheme() {
    applyThemeClass(getStoredTheme());
  }

  // 导出到全局
  window.toggleTheme = toggleTheme;
  window.initTheme = initTheme;

  // 自动初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTheme);
  } else {
    initTheme();
  }
})();






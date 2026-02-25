/**
 * 通用工具模块
 * 包含 QQ 链接、滚动显示、平滑滚动等功能
 */

(function() {
  'use strict';

  const ARIES_DATA = window.ARIES_DATA || {
    qqGroupId: '746439473',
    qqJoinUrl: 'https://qm.qq.com/q/ASVDJPrIxq',
  };

  // QQ 群链接
  function initQqLink() {
    const qqLink = document.getElementById('qq-link');
    if (!qqLink) return;

    qqLink.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(ARIES_DATA.qqGroupId);
      } catch (_) { }
      window.open(ARIES_DATA.qqJoinUrl, '_blank', 'noopener');
    });
  }

  // 滚动显示动画
  function initScrollReveal() {
    const elements = document.querySelectorAll('.reveal-on-scroll');
    if (!elements.length) return;

    const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (prefersReducedMotion) {
      elements.forEach(el => el.classList.add('revealed'));
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            entry.target.classList.add('revealed');
            observer.unobserve(entry.target);
          }
        });
      },
      {
        threshold: 0.1,
        rootMargin: '0px 0px -50px 0px',
      }
    );

    elements.forEach((el, index) => {
      el.style.transitionDelay = `${index * 0.1}s`;
      observer.observe(el);
    });
  }

  // 平滑滚动
  function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
      anchor.addEventListener('click', function (e) {
        const href = this.getAttribute('href');
        if (!href || href === '#') return;
        const target = document.querySelector(href);
        if (!target) return;
        e.preventDefault();
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    });
  }

  // 主题提示工具
  function initTooltipScroll() {
    const tooltip = document.getElementById('theme-tooltip');
    if (!tooltip) return;

    let hasScrolled = false;

    function onScroll() {
      if (hasScrolled) return;
      hasScrolled = true;
      tooltip.style.opacity = '0';
      tooltip.style.pointerEvents = 'none';
      window.removeEventListener('scroll', onScroll);
    }

    window.addEventListener('scroll', onScroll);
  }

  // 移动端菜单切换
  function toggleMobileMenu() {
    const menu = document.getElementById('mobile-menu');
    const backdrop = document.getElementById('mobile-menu-backdrop');
    if (!menu) return;

    if (menu.classList.contains('translate-x-full')) {
      menu.classList.remove('translate-x-full');
      if (backdrop) backdrop.classList.remove('opacity-0', 'pointer-events-none');
      document.body.style.overflow = 'hidden';
    } else {
      menu.classList.add('translate-x-full');
      if (backdrop) backdrop.classList.add('opacity-0', 'pointer-events-none');
      document.body.style.overflow = '';
    }
  }

  // 文档侧边栏切换
  function toggleDocSidebar() {
    const sidebar = document.getElementById('mobile-sidebar');
    const backdrop = document.getElementById('mobile-sidebar-backdrop');
    if (!sidebar) return;

    if (sidebar.classList.contains('translate-x-full')) {
      sidebar.classList.remove('translate-x-full');
      if (backdrop) backdrop.classList.remove('opacity-0', 'pointer-events-none');
      document.body.style.overflow = 'hidden';
    } else {
      sidebar.classList.add('translate-x-full');
      if (backdrop) backdrop.classList.add('opacity-0', 'pointer-events-none');
      document.body.style.overflow = '';
    }
  }

  // 导出到全局
  window.initQqLink = initQqLink;
  window.initScrollReveal = initScrollReveal;
  window.initSmoothScroll = initSmoothScroll;
  window.initTooltipScroll = initTooltipScroll;
  window.toggleMobileMenu = toggleMobileMenu;
  window.toggleDocSidebar = toggleDocSidebar;

  // 自动初始化
  function initCommon() {
    initQqLink();
    initScrollReveal();
    initSmoothScroll();
    initTooltipScroll();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initCommon);
  } else {
    initCommon();
  }
})();


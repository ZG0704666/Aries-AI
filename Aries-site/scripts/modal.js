/**
 * 下载弹窗模块
 * 负责下载弹窗的显示、隐藏和链接管理
 */

(function() {
  'use strict';

  const ARIES_DATA = window.ARIES_DATA || {
    githubReleasesPageUrl: 'https://github.com/ZG0704666/Aries-AI/releases',
    githubLatestApkUrl: 'https://github.com/ZG0704666/Aries-AI/releases/latest/download/app-release.apk',
    fixedApkUrl: 'https://github.com/ZG0704666/Aries-AI/releases/download/V1.5.0/app-release.apk',
  };

  function initDownloadModal() {
    const modal = document.getElementById('download-modal');
    if (!modal) return;

    const mirrorFast = document.getElementById('download-mirror-ghfast');
    const mirrorGitMirror = document.getElementById('download-mirror-gitmirror');
    const official = document.getElementById('download-official') || document.getElementById('download-github-link');
    const closeBtn = document.getElementById('download-modal-close');
    const backdrop = modal.querySelector('.backdrop') || modal.querySelector('.absolute.inset-0');

    const openers = [
      document.getElementById('btn-download-hero'),
      document.getElementById('btn-download-nav'),
      document.getElementById('btn-download-latest'),
    ].filter(Boolean);

    function openInNewTab(url) {
      window.open(url, '_blank', 'noopener');
    }

    function show() {
      modal.classList.remove('hidden');
      modal.classList.add('flex');
      modal.classList.add('show');
      modal.setAttribute('aria-hidden', 'false');
    }

    function hide() {
      modal.classList.add('hidden');
      modal.classList.remove('flex');
      modal.classList.remove('show');
      modal.setAttribute('aria-hidden', 'true');
    }

    function toGhFast(url) {
      return 'https://ghfast.top/' + url;
    }

    function toGitMirror(url) {
      return url.replace('https://github.com/', 'https://hub.gitmirror.com/');
    }

    function getResolvedAssetUrl() {
      return ARIES_DATA.githubLatestApkUrl || ARIES_DATA.fixedApkUrl;
    }

    function setHref(el, url) {
      if (!el) return;
      el.href = url || ARIES_DATA.githubReleasesPageUrl;
    }

    function refreshHrefs() {
      const u = getResolvedAssetUrl();
      setHref(official, u || ARIES_DATA.githubReleasesPageUrl);
      setHref(mirrorFast, u ? toGhFast(u) : '');
      setHref(mirrorGitMirror, u ? toGitMirror(u) : '');
    }

    function bindOpenLink(el, getUrl) {
      if (!el) return;
      el.addEventListener('click', (e) => {
        e.preventDefault();
        const url = getUrl();
        openInNewTab(url || ARIES_DATA.githubReleasesPageUrl);
      });
    }

    for (const btn of openers) {
      btn.addEventListener('click', (e) => {
        e.preventDefault();
        show();
      });
    }

    if (closeBtn) closeBtn.addEventListener('click', hide);
    if (backdrop) backdrop.addEventListener('click', hide);

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') hide();
    });

    refreshHrefs();

    bindOpenLink(mirrorFast, () => {
      const u = getResolvedAssetUrl();
      return u ? toGhFast(u) : '';
    });

    bindOpenLink(mirrorGitMirror, () => {
      const u = getResolvedAssetUrl();
      return u ? toGitMirror(u) : '';
    });

    bindOpenLink(official, () => getResolvedAssetUrl() || ARIES_DATA.githubReleasesPageUrl);
  }

  // 导出到全局
  window.initDownloadModal = initDownloadModal;

  // 自动初始化
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initDownloadModal);
  } else {
    initDownloadModal();
  }
})();





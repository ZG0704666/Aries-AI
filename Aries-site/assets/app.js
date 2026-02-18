(function () {
  'use strict';

  const ARIES_DATA = {
    qqGroupId: '746439473',
    qqJoinUrl: 'https://qm.qq.com/q/ASVDJPrIxq',
    githubReleasesPageUrl: 'https://github.com/ZG0704666/Aries-AI/releases',
    githubLatestReleaseApi: 'https://api.github.com/repos/ZG0704666/Aries-AI/releases/latest',
    fixedApkUrl: 'https://github.com/ZG0704666/Aries-AI/releases/download/V1.2.0/app-release.apk',
    apps: [
      '淘宝', '支付宝', '美团', '高德地图', '微信', 'QQ', '京东', '知乎', 'B站', '抖音', '小红书', '携程',
      '12306', '饿了么', '拼多多', '闲鱼', '快手', '网易云音乐', '微博', 'Keep', 'WPS', '大众点评', '滴滴出行', '百度地图',
      'QQ音乐', '腾讯视频', '爱奇艺', '优酷', '哔哩哔哩漫画', '淘宝特价版', '得物', '苏宁易购', '唯品会', '菜鸟',
      '豆瓣', '小宇宙', '喜马拉雅', '百度网盘', '夸克', 'UC浏览器', '百度', '今日头条', '腾讯新闻', '网易新闻',
      '微信读书', '飞书', '钉钉', '企业微信', '携程旅行', '去哪儿', '同程旅行', '滴滴',
    ],
  };

  window.ARIES_DATA = ARIES_DATA;

  function throttle(fn, delay) {
    let last = 0;
    return function (...args) {
      const now = performance.now();
      if (now - last >= delay) {
        last = now;
        fn.apply(this, args);
      }
    };
  }

  function debounce(fn, delay) {
    let timer = null;
    return function (...args) {
      clearTimeout(timer);
      timer = setTimeout(() => fn.apply(this, args), delay);
    };
  }

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

  window.toggleTheme = toggleTheme;

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

  function toggleDocSidebar() {
    const sidebar = document.getElementById('mobile-sidebar');
    const backdrop = document.getElementById('mobile-sidebar-backdrop');
    if (!sidebar) return;

    if (sidebar.classList.contains('-translate-x-full')) {
      sidebar.classList.remove('-translate-x-full');
      if (backdrop) backdrop.classList.remove('opacity-0', 'pointer-events-none');
      document.body.style.overflow = 'hidden';
    } else {
      sidebar.classList.add('-translate-x-full');
      if (backdrop) backdrop.classList.add('opacity-0', 'pointer-events-none');
      document.body.style.overflow = '';
    }
  }

  window.toggleMobileMenu = toggleMobileMenu;
  window.toggleDocSidebar = toggleDocSidebar;

  function initTheme() {
    applyThemeClass(getStoredTheme());
  }

  function initQqLink() {
    const qqLink = document.getElementById('qq-link');
    if (!qqLink) return;

    qqLink.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(ARIES_DATA.qqGroupId);
      } catch (_) {}
      window.open(ARIES_DATA.qqJoinUrl, '_blank', 'noopener');
    });
  }

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

    let resolvedAssetUrl = '';

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
      return resolvedAssetUrl || ARIES_DATA.fixedApkUrl;
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

    (async function resolveLatestReleaseApkUrl() {
      try {
        const res = await fetch(ARIES_DATA.githubLatestReleaseApi, {
          headers: { 'Accept': 'application/vnd.github+json' },
        });
        if (!res.ok) return;
        const data = await res.json();
        const assets = Array.isArray(data && data.assets) ? data.assets : [];
        const apk = assets.find(a => typeof a?.name === 'string' && a.name.toLowerCase().endsWith('.apk'))
          || assets.find(a => typeof a?.browser_download_url === 'string' && a.browser_download_url.toLowerCase().endsWith('.apk'));
        const url = apk && typeof apk.browser_download_url === 'string' ? apk.browser_download_url : '';
        resolvedAssetUrl = url || ARIES_DATA.fixedApkUrl;
      } catch (_) {
        resolvedAssetUrl = ARIES_DATA.fixedApkUrl;
      } finally {
        refreshHrefs();
      }
    })();

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

  function initStars() {
    const container = document.getElementById('stars-container');
    if (!container) return;

    const starCount = 80;
    container.innerHTML = '';

    for (let i = 0; i < starCount; i++) {
      const star = document.createElement('div');
      star.className = 'star';
      const size = Math.random() * 2.2 + 0.6;
      const opacity = Math.random() * 0.65 + 0.25;
      const duration = Math.random() * 4 + 2;
      const delay = Math.random() * 5;
      star.style.width = size + 'px';
      star.style.height = size + 'px';
      star.style.left = Math.random() * 100 + '%';
      star.style.top = Math.random() * 100 + '%';
      star.style.setProperty('--opacity', opacity.toString());
      star.style.setProperty('--duration', duration + 's');
      star.style.setProperty('--delay', delay + 's');
      container.appendChild(star);
    }
  }

  function initConstellation() {
    const canvas = document.getElementById('constellation-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    let w = 0;
    let h = 0;
    let dpr = Math.max(1, Math.min(2, window.devicePixelRatio || 1));

    const points = [];
    const pointCount = 40;
    const linkDist = 200;
    const repelRadius = 150;
    const repelStrength = 1.0;

    let mouseX = -9999;
    let mouseY = -9999;

    function resize() {
      dpr = Math.max(1, Math.min(2, window.devicePixelRatio || 1));
      w = Math.floor(window.innerWidth);
      h = Math.floor(window.innerHeight);
      canvas.width = Math.floor(w * dpr);
      canvas.height = Math.floor(h * dpr);
      canvas.style.width = w + 'px';
      canvas.style.height = h + 'px';
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    function isDark() {
      return document.documentElement.classList.contains('dark');
    }

    function resetPoints() {
      points.length = 0;
      for (let i = 0; i < pointCount; i++) {
        points.push({
          x: Math.random() * w,
          y: Math.random() * h,
          vx: (Math.random() - 0.5) * 0.3,
          vy: (Math.random() - 0.5) * 0.3,
          twinkle: Math.random() * Math.PI * 2,
        });
      }
    }

    function onMove(e) {
      mouseX = e.clientX;
      mouseY = e.clientY;
    }

    function onLeave() {
      mouseX = -9999;
      mouseY = -9999;
    }

    window.addEventListener('resize', debounce(() => {
      resize();
      resetPoints();
    }, 100));
    window.addEventListener('mousemove', throttle(onMove, 16));
    window.addEventListener('mouseleave', onLeave);

    resize();
    resetPoints();

    let last = performance.now();

    function step(now) {
      const dt = Math.min(0.033, (now - last) / 1000);
      last = now;

      ctx.clearRect(0, 0, w, h);

      if (!isDark()) {
        requestAnimationFrame(step);
        return;
      }

      for (const p of points) {
        p.x += p.vx;
        p.y += p.vy;
        p.twinkle += dt * 2;

        if (p.x < -20) p.x = w + 20;
        if (p.x > w + 20) p.x = -20;
        if (p.y < -20) p.y = h + 20;
        if (p.y > h + 20) p.y = -20;

        const dx = p.x - mouseX;
        const dy = p.y - mouseY;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist > 0.001 && dist < repelRadius) {
          const force = (1 - dist / repelRadius) * repelStrength;
          p.x += (dx / dist) * force * 24 * dt;
          p.y += (dy / dist) * force * 24 * dt;
        }
      }

      ctx.save();
      ctx.globalCompositeOperation = 'lighter';

      for (let i = 0; i < points.length; i++) {
        const a = points[i];
        for (let j = i + 1; j < points.length; j++) {
          const b = points[j];
          const dx = a.x - b.x;
          const dy = a.y - b.y;
          const d = Math.sqrt(dx * dx + dy * dy);
          if (d < linkDist) {
            const alpha = (1 - d / linkDist) * 0.28;
            ctx.strokeStyle = `rgba(180, 220, 255, ${alpha})`;
            ctx.lineWidth = 1.5;
            ctx.beginPath();
            ctx.moveTo(a.x, a.y);
            ctx.lineTo(b.x, b.y);
            ctx.stroke();
          }
        }
      }

      for (const p of points) {
        const pulse = 0.65 + (Math.sin(p.twinkle) * 0.25);
        ctx.fillStyle = `rgba(255, 255, 255, ${pulse})`;
        ctx.shadowColor = 'rgba(140, 200, 255, 0.75)';
        ctx.shadowBlur = 15;
        ctx.beginPath();
        ctx.arc(p.x, p.y, 2.2, 0, Math.PI * 2);
        ctx.fill();
        ctx.shadowBlur = 0;
      }

      ctx.restore();
      requestAnimationFrame(step);
    }

    requestAnimationFrame(step);
  }

  function initMeteorBackground() {
    const meteorCanvas = document.getElementById('meteor-canvas');
    if (!meteorCanvas) return;
    const mctx = meteorCanvas.getContext('2d');
    if (!mctx) return;

    let w = 0;
    let h = 0;
    let dpr = Math.max(1, Math.min(2, window.devicePixelRatio || 1));

    const meteors = [];
    const maxMeteors = 2;
    let nextMeteorAt = performance.now() + 1600;

    function isDark() {
      return document.documentElement.classList.contains('dark');
    }

    function rand(min, max) {
      return min + Math.random() * (max - min);
    }

    function resize() {
      dpr = Math.max(1, Math.min(2, window.devicePixelRatio || 1));
      w = Math.floor(window.innerWidth);
      h = Math.floor(window.innerHeight);
      meteorCanvas.width = Math.floor(w * dpr);
      meteorCanvas.height = Math.floor(h * dpr);
      meteorCanvas.style.width = w + 'px';
      meteorCanvas.style.height = h + 'px';
      mctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    function spawnMeteor() {
      const fromLeft = Math.random() < 0.5;
      const startX = fromLeft ? rand(-w * 0.2, w * 0.05) : rand(w * 0.95, w * 1.2);
      const startY = rand(h * 0.05, h * 0.55);
      const angle = fromLeft ? rand(0.15, 0.35) : Math.PI + rand(-0.35, -0.15);
      const speed = rand(900, 1350);
      const len = rand(1100, 1900);

      meteors.push({
        x: startX,
        y: startY,
        vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed,
        len,
        life: 0,
        maxLife: rand(0.9, 1.4),
      });
    }

    window.addEventListener('resize', resize);
    resize();

    let last = performance.now();
    function step(now) {
      const dt = Math.min(0.033, (now - last) / 1000);
      last = now;

      if (!isDark()) {
        mctx.clearRect(0, 0, w, h);
        requestAnimationFrame(step);
        return;
      }

      mctx.clearRect(0, 0, w, h);

      if (now > nextMeteorAt && meteors.length < maxMeteors) {
        spawnMeteor();
        nextMeteorAt = now + rand(3500, 9000);
      }

      for (let i = meteors.length - 1; i >= 0; i--) {
        const m = meteors[i];
        m.life += dt;
        m.x += m.vx * dt;
        m.y += m.vy * dt;
        const t = Math.min(1, m.life / m.maxLife);
        const a = (1 - t);

        const tailX = m.x - (m.vx / 1100) * m.len;
        const tailY = m.y - (m.vy / 1100) * m.len;
        const grad = mctx.createLinearGradient(m.x, m.y, tailX, tailY);
        grad.addColorStop(0.00, `rgba(255,255,255,${1.00 * a})`);
        grad.addColorStop(0.07, `rgba(120,235,255,${0.95 * a})`);
        grad.addColorStop(0.20, `rgba(255,210,140,${0.45 * a})`);
        grad.addColorStop(1.00, 'rgba(255,255,255,0)');

        mctx.save();
        mctx.globalCompositeOperation = 'lighter';
        mctx.strokeStyle = grad;
        mctx.lineWidth = 2.6;
        mctx.beginPath();
        mctx.moveTo(m.x, m.y);
        mctx.lineTo(tailX, tailY);
        mctx.stroke();

        mctx.fillStyle = `rgba(170,240,255,${1.00 * a})`;
        mctx.shadowColor = 'rgba(140,235,255,0.95)';
        mctx.shadowBlur = 28;
        mctx.beginPath();
        mctx.arc(m.x, m.y, 2.2, 0, Math.PI * 2);
        mctx.fill();
        mctx.restore();

        if (t >= 1 || m.x < -1000 || m.x > w + 1000 || m.y < -1000 || m.y > h + 1000) {
          meteors.splice(i, 1);
        }
      }

      requestAnimationFrame(step);
    }

    requestAnimationFrame(step);
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

  function initCommon() {
    initTheme();
    initQqLink();
    initDownloadModal();
    initScrollReveal();
    initSmoothScroll();
    initTooltipScroll();
  }

  function initHome() {
    initStars();
    initConstellation();
    initMarqueeHome();
  }

  function initDocs() {
    initConstellation();
    initMeteorBackground();
  }

  function boot() {
    initCommon();
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

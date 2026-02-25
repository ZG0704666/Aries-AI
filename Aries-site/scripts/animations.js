/**
 * 动画效果模块
 * 负责星空、星座连线、流星等视觉效果
 */

(function() {
  'use strict';

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

  // 星星背景
  function initStars() {
    const container = document.getElementById('stars-container');
    if (!container) return;

    const isMobile = window.innerWidth <= 640;
    const starCount = isMobile ? 20 : 80;
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

  // 星座连线动画
  function initConstellation() {
    const canvas = document.getElementById('constellation-canvas');
    if (!canvas) return;

    // 移动端性能优化：禁用星座动画
    if (window.innerWidth <= 640) {
      canvas.style.display = 'none';
      return;
    }

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

  // 流星背景
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

  // 导出到全局
  window.initStars = initStars;
  window.initConstellation = initConstellation;
  window.initMeteorBackground = initMeteorBackground;
})();


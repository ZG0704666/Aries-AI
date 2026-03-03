/**
 * APP图标管理模块
 * 提供真实、高质量的APP图标展示
 */

(function () {
  'use strict';

  // APP图标配置 - 使用官方品牌颜色和高清图标
  const APP_ICONS = {
    // 第一行应用
    '淘宝': { color: '#FF5000', icon: 'taobao', domain: 'taobao.com' },
    '支付宝': { color: '#1677FF', icon: 'alipay', domain: 'alipay.com' },
    '美团': { color: '#FFC300', icon: 'meituan', domain: 'meituan.com' },
    '高德地图': { color: '#4285F4', icon: 'amap', domain: 'amap.com' },
    '微信': { color: '#07C160', icon: 'wechat', domain: 'weixin.qq.com' },
    'QQ': { color: '#12B7F5', icon: 'qq', domain: 'im.qq.com' },
    '京东': { color: '#E4393C', icon: 'jd', domain: 'jd.com' },
    '知乎': { color: '#0084FF', icon: 'zhihu', domain: 'zhihu.com' },
    'B站': { color: '#FB7299', icon: 'bilibili', domain: 'bilibili.com' },
    '抖音': { color: '#000000', icon: 'douyin', domain: 'douyin.com' },
    '小红书': { color: '#FF2442', icon: 'xiaohongshu', domain: 'xiaohongshu.com' },
    '携程': { color: '#2577E3', icon: 'ctrip', domain: 'ctrip.com' },
    '12306': { color: '#0077C8', icon: '12306', domain: '12306.cn' },
    '饿了么': { color: '#0085FF', icon: 'eleme', domain: 'ele.me' },
    '拼多多': { color: '#E02E24', icon: 'pinduoduo', domain: 'pinduoduo.com' },
    '闲鱼': { color: '#FFDA44', icon: 'xianyu', domain: '2.taobao.com' },
    '快手': { color: '#FF6600', icon: 'kuaishou', domain: 'kuaishou.com' },
    '网易云音乐': { color: '#C20C0C', icon: 'netease-music', domain: 'music.163.com' },
    '微博': { color: '#E6162D', icon: 'weibo', domain: 'weibo.com' },
    'Keep': { color: '#20C6B6', icon: 'keep', domain: 'gotokeep.com' },
    'WPS': { color: '#E60012', icon: 'wps', domain: 'wps.cn' },
    '大众点评': { color: '#FF9900', icon: 'dianping', domain: 'dianping.com' },
    '滴滴出行': { color: '#FF7D00', icon: 'didi', domain: 'didiglobal.com' },
    '百度地图': { color: '#4E6EF2', icon: 'baidu-map', domain: 'map.baidu.com' },
    'QQ音乐': { color: '#31C27C', icon: 'qq-music', domain: 'y.qq.com' },
    '腾讯视频': { color: '#00BE06', icon: 'qq-video', domain: 'v.qq.com' },
    '爱奇艺': { color: '#00BE06', icon: 'iqiyi', domain: 'iqiyi.com' },
    '优酷': { color: '#1E90FF', icon: 'youku', domain: 'youku.com' },
    '哔哩哔哩漫画': { color: '#FB7299', icon: 'bilibili-manga', domain: 'manga.bilibili.com' },
    '淘宝特价版': { color: '#FF5000', icon: 'taobao-tejia', domain: 'taobao.com' },
    '得物': { color: '#46D7C8', icon: 'dewu', domain: 'dewu.com' },
    '苏宁易购': { color: '#F8A51D', icon: 'suning', domain: 'suning.com' },
    '唯品会': { color: '#E8466C', icon: 'vip', domain: 'vip.com' },
    '菜鸟': { color: '#2EAB6F', icon: 'cainiao', domain: 'cainiao.com' },
    '豆瓣': { color: '#007722', icon: 'douban', domain: 'douban.com' },
    '小宇宙': { color: '#FFD200', icon: 'xiaoyuzhou', domain: 'xiaoyuzhoufm.com' },
    '喜马拉雅': { color: '#E8380D', icon: 'ximalaya', domain: 'ximalaya.com' },
    '百度网盘': { color: '#2932E1', icon: 'baidu-pan', domain: 'pan.baidu.com' },
    '夸克': { color: '#00B4FF', icon: 'quark', domain: 'quark.cn' },
    'UC浏览器': { color: '#FF6B00', icon: 'uc', domain: 'uc.cn' },
    '百度': { color: '#2932E1', icon: 'baidu', domain: 'baidu.com' },
    '今日头条': { color: '#F04142', icon: 'toutiao', domain: 'toutiao.com' },
    '腾讯新闻': { color: '#00A3FF', icon: 'qq-news', domain: 'news.qq.com' },
    '网易新闻': { color: '#C20C0C', icon: 'netease-news', domain: 'news.163.com' },
    '微信读书': { color: '#1AAD19', icon: 'weread', domain: 'weread.qq.com' },
    '飞书': { color: '#3370FF', icon: 'feishu', domain: 'feishu.cn' },
    '钉钉': { color: '#3370FF', icon: 'dingtalk', domain: 'dingtalk.com' },
    '企业微信': { color: '#2BAD31', icon: 'wecom', domain: 'work.weixin.qq.com' },
    '携程旅行': { color: '#2577E3', icon: 'ctrip', domain: 'ctrip.com' },
    '去哪儿': { color: '#00BCD4', icon: 'qunar', domain: 'qunar.com' },
    '同程旅行': { color: '#00A4FF', icon: 'tongcheng', domain: 'ly.com' },
    '滴滴': { color: '#FF7D00', icon: 'didi', domain: 'didiglobal.com' }
  };

  // 生成SVG图标（作为备选方案）
  function generateAppIcon(name, config) {
    const initials = name.substring(0, 1);
    const color = config.color || '#666';
    
    return `data:image/svg+xml,${encodeURIComponent(`
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
        <defs>
          <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
            <stop offset="0%" style="stop-color:${color};stop-opacity:1" />
            <stop offset="100%" style="stop-color:${adjustColor(color, -20)};stop-opacity:1" />
          </linearGradient>
        </defs>
        <rect width="100" height="100" rx="22" fill="url(#grad)"/>
        <text x="50" y="65" font-family="-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif" 
              font-size="45" font-weight="bold" fill="white" text-anchor="middle">${initials}</text>
      </svg>
    `)}`;
  }

  // 调整颜色亮度
  function adjustColor(color, amount) {
    const num = parseInt(color.replace('#', ''), 16);
    const r = Math.max(0, Math.min(255, (num >> 16) + amount));
    const g = Math.max(0, Math.min(255, ((num >> 8) & 0x00FF) + amount));
    const b = Math.max(0, Math.min(255, (num & 0x0000FF) + amount));
    return '#' + ((r << 16) | (g << 8) | b).toString(16).padStart(6, '0');
  }

  const APP_ICON_BASE = './assets/icons/apps/';

  // 获取图标URL - 优先使用本地图标，缺失时再走生成 SVG
  function getIconUrl(name) {
    const config = APP_ICONS[name];
    if (!config) {
      return generateAppIcon(name, { color: '#666' });
    }
    
    const domain = config.domain;
    return `${APP_ICON_BASE}${domain}.ico`;
  }

  // 获取APP配置
  function getAppConfig(name) {
    return APP_ICONS[name] || { color: '#666', icon: 'default', domain: '' };
  }

  // 导出到全局
  window.APP_ICONS = {
    data: APP_ICONS,
    getIconUrl: getIconUrl,
    getAppConfig: getAppConfig,
    generateAppIcon: generateAppIcon
  };

})();

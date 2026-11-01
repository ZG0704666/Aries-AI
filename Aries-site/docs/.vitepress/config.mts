import { defineConfig } from 'vitepress';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import slugs from './slugs.json';
import categories from './categories.json';
import { rewriteDocLinks } from './links';

const srcDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

interface DocCategory {
  id: string;
  title: string;
  items: { file: string; title: string }[];
}

const docs = categories as DocCategory[];
const slugOf = slugs as Record<string, string>;

/** VitePress 的 rewrites 以带 `.md` 的源文件路径为键；用函数形式避免中文文件名里的括号被当成路径参数。 */
function rewritePage(page: string): string {
  const slug = slugOf[page.replace(/\.md$/, '')];
  return slug ? `${slug}.md` : page;
}

const sidebar = docs.map((category) => ({
  text: category.title,
  collapsed: category.id === 'legacy',
  items: category.items.map((item) => ({
    text: item.title,
    link: `/${slugOf[item.file]}`,
  })),
}));

/**
 * 中文分词：按 CJK 单字 + 双字组合生成 token，并保留英文/数字串。
 * MiniSearch 默认按空白切分，中文段落会变成单个超长 token，导致搜索命中率极低。
 */
function tokenize(text: string): string[] {
  const tokens: string[] = [];
  const segments = text.split(/[\s，。、；：！？“”‘’（）《》【】·—…,.:;!?"'()[\]{}<>|/\\]+/u);

  for (const segment of segments) {
    if (!segment) continue;

    const latin = segment.match(/[a-zA-Z0-9_+#.-]{2,}/g);
    if (latin) tokens.push(...latin.map((token) => token.toLowerCase()));

    const cjk = segment.match(/[\u3400-\u4dbf\u4e00-\u9fff]{1,}/g);
    if (!cjk) continue;

    for (const run of cjk) {
      for (let i = 0; i < run.length; i += 1) {
        tokens.push(run[i]);
        if (i + 1 < run.length) tokens.push(run.slice(i, i + 2));
      }
    }
  }

  return tokens;
}

export default defineConfig({
  lang: 'zh-CN',
  title: 'Aries AI',
  titleTemplate: ':title — Aries AI 文档',
  description:
    'Aries AI 技术文档：Android 视觉自动化引擎的架构、对话系统、自动化流程、虚拟屏隔离、多模型接入与开发规范。',
  cleanUrls: true,
  lastUpdated: true,
  appearance: 'dark',
  rewrites: rewritePage,

  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/favicon.png' }],
    ['link', { rel: 'apple-touch-icon', href: '/favicon.png' }],
    ['meta', { name: 'theme-color', content: '#05060a' }],
    ['meta', { name: 'color-scheme', content: 'dark light' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { property: 'og:site_name', content: 'Aries AI 文档' }],
    ['meta', { property: 'og:locale', content: 'zh_CN' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
  ],

  sitemap: {
    hostname: 'https://docs.aries-agent.com',
  },

  transformHead({ page, pageData }) {
    const relative = page
      .replace(/(^|\/)index\.(md|html)$/, '$1')
      .replace(/\.(md|html)$/, '');
    if (pageData.isNotFound || relative === '404') return [];

    const url = new URL(relative, 'https://docs.aries-agent.com/').href;
    const title = pageData.title ? `${pageData.title} — Aries AI 文档` : 'Aries AI 文档';
    const description =
      pageData.frontmatter.description ||
      `${pageData.title || 'Aries AI'} — Aries AI 技术文档，覆盖架构设计、对话系统、自动化引擎、虚拟屏隔离、多模型接入与开发规范。`;

    return [
      ['link', { rel: 'canonical', href: url }],
      ['meta', { property: 'og:url', content: url }],
      ['meta', { property: 'og:title', content: title }],
      ['meta', { property: 'og:description', content: description }],
      ['meta', { name: 'description', content: description }],
    ];
  },

  markdown: {
    lineNumbers: false,
    image: {
      lazyLoading: true,
    },
    config: (md) => {
      rewriteDocLinks(md, slugOf, srcDir);
    },
  },

  themeConfig: {
    logo: '/favicon.png',
    siteTitle: 'Aries AI 文档',

    nav: [
      { text: '指南', link: '/overview/introduction' },
      { text: '快速开始', link: '/quickstart/setup' },
      { text: '架构', link: '/architecture/overview' },
      { text: '自动化', link: '/automation/agent-loop' },
      { text: '下载', link: 'https://aries-agent.com/download' },
      { text: '官网', link: 'https://aries-agent.com/' },
    ],

    sidebar,

    outline: {
      level: [2, 3],
      label: '本页目录',
    },

    docFooter: {
      prev: '上一篇',
      next: '下一篇',
    },

    lastUpdated: {
      text: '最后更新',
      formatOptions: {
        dateStyle: 'medium',
        timeStyle: 'short',
      },
    },

    returnToTopLabel: '回到顶部',
    sidebarMenuLabel: '文档目录',
    darkModeSwitchLabel: '主题',
    lightModeSwitchTitle: '切换到浅色模式',
    darkModeSwitchTitle: '切换到深色模式',

    editLink: {
      pattern: 'https://github.com/ZG0704666/Aries-AI/edit/main/Aries-site/docs/:path',
      text: '在 GitHub 上编辑此页',
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/ZG0704666/Aries-AI' },
    ],

    footer: {
      message: '基于 AGPL-3.0 许可证开源 · 文档随代码同步更新',
      copyright: '© 2026 Aries AI Project',
    },

    search: {
      provider: 'local',
      options: {
        detailedView: true,
        miniSearch: {
          options: {
            tokenize,
            processTerm: (term: string) => term.toLowerCase(),
          },
          searchOptions: {
            fuzzy: 0.2,
            prefix: true,
            boost: { title: 6, text: 2, titles: 1 },
          },
        },
        locales: {
          root: {
            translations: {
              button: {
                buttonText: '搜索文档',
                buttonAriaLabel: '搜索文档',
              },
              modal: {
                displayDetails: '显示详情',
                resetButtonTitle: '清除查询',
                backButtonTitle: '返回',
                noResultsText: '没有找到相关结果',
                footer: {
                  selectText: '选择',
                  selectKeyAriaLabel: '回车',
                  navigateText: '切换',
                  navigateUpKeyAriaLabel: '上方向键',
                  navigateDownKeyAriaLabel: '下方向键',
                  closeText: '关闭',
                  closeKeyAriaLabel: 'Esc',
                },
              },
            },
          },
        },
      },
    },
  },
});

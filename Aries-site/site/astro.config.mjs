import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

export default defineConfig({
  site: 'https://aries-agent.com',
  integrations: [
    sitemap({
      filter: (page) => !page.includes('/404'),
    }),
  ],
  // 产物集中到 Aries-site/dist/，与文档站部署路径对称（Astro 7 起为顶层选项）
  outDir: '../dist/site',
  build: {
    inlineStylesheets: 'auto',
  },
});

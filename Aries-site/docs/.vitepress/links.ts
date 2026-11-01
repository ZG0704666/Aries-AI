import path from 'node:path';

/**
 * 把文档里的相对 `.md` 链接改写成新站的英文 slug 绝对路径。
 *
 * VitePress 的 rewrites 会改变页面输出路径，相对链接在改写后会按新路径解析，
 * 从而 404。这里在渲染前统一按源文件位置解析并映射到 slug。
 */
export function rewriteDocLinks(markdown, slugs: Record<string, string>, srcDir: string) {
  markdown.core.ruler.push('aries_rewrite_doc_links', (state: any) => {
    const realPath: string | undefined = state.env?.realPath;
    if (!realPath) return;

    const relative = path.relative(srcDir, realPath).split(path.sep).join('/');
    const fromDir = path.posix.dirname(relative);

    for (const token of state.tokens) {
      if (token.type !== 'inline') continue;

      for (const child of token.children ?? []) {
        if (child.type !== 'link_open') continue;

        const index = child.attrIndex('href');
        if (index < 0) continue;

        const href: string = child.attrs[index][1];
        if (!href || /^(?:[a-z][a-z\d+.-]*:|\/\/|#)/i.test(href)) continue;

        const hashIndex = href.indexOf('#');
        const target = hashIndex >= 0 ? href.slice(0, hashIndex) : href;
        const hash = hashIndex >= 0 ? href.slice(hashIndex) : '';

        if (!target.toLowerCase().endsWith('.md')) continue;

        let decoded = target;
        try {
          decoded = decodeURI(target);
        } catch {
          /* 保留原始值 */
        }

        const resolved = path.posix.normalize(path.posix.join(fromDir, decoded));
        const slug = slugs[resolved.replace(/\.md$/i, '')];
        if (!slug) continue;

        child.attrs[index][1] = `/${slug}${hash}`;
      }
    }
  });
}
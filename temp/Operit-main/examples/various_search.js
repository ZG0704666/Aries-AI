/* METADATA
{
  name: various_search
  description: 提供多平台搜索功能，支持从必应、百度、搜狗、夸克等平台获取搜索结果。
  enabledByDefault: true
  
  tools: [
    {
      name: search_bing
      description: 使用必应搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: includeLinks
          description: 是否在结果中包含可点击的链接列表，默认为false。如果为true，AI可以根据返回的链接序号进行深入访问。
          type: boolean
          required: false
        }
      ]
    },
    {
      name: search_baidu
      description: 使用百度搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        },
        {
          name: includeLinks
          description: 是否在结果中包含可点击的链接列表，默认为false。如果为true，AI可以根据返回的链接序号进行深入访问。
          type: boolean
          required: false
        }
      ]
    },
    {
      name: search_sogou
      description: 使用搜狗搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        },
        {
          name: includeLinks
          description: 是否在结果中包含可点击的链接列表，默认为false。如果为true，AI可以根据返回的链接序号进行深入访问。
          type: boolean
          required: false
        }
      ]
    },
    {
      name: search_quark
      description: 使用夸克搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        },
        {
          name: includeLinks
          description: 是否在结果中包含可点击的链接列表，默认为false。如果为true，AI可以根据返回的链接序号进行深入访问。
          type: boolean
          required: false
        }
      ]
    },
    {
      name: combined_search
      description: 在多个平台同时执行搜索。建议用户要求搜索的时候默认使用这个工具。
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: platforms
          description: 搜索平台列表字符串，可选值包括"bing","baidu","sogou","quark"，多个平台用逗号分隔，比如"bing,baidu,sogou,quark"
          type: string
          required: true
        },
        {
          name: includeLinks
          description: 是否在结果中包含可点击的链接列表，默认为false。聚合搜索时建议保持为false以节省输出，仅在需要深入访问时对单个搜索引擎使用。
          type: boolean
          required: false
        }
      ]
    }
  ]
}
*/
const various_search = (function () {
    async function performSearch(platform, url, query, page, includeLinks = false) {
        try {
            const response = await Tools.Net.visit(url);
            if (!response) {
                throw new Error(`无法获取 ${platform} 搜索结果`);
            }
            let parts = [];
            // visitKey
            if (response.visitKey !== undefined) {
                parts.push(String(response.visitKey));
            }
            // links: [index] text （不包含链接本身）
            if (includeLinks && response.links && Array.isArray(response.links) && response.links.length > 0) {
                const linksLines = response.links.map((link, index) => `[${index + 1}] ${link.text}`);
                parts.push(linksLines.join('\n'));
            }
            // content
            if (response.content !== undefined) {
                parts.push(String(response.content));
            }
            return {
                platform,
                content: parts.join('\n')
            };
        }
        catch (error) {
            return {
                platform,
                content: `${platform} 搜索失败: ${error.message}`
            };
        }
    }
    async function search_bing(query, includeLinks = false) {
        const encodedQuery = encodeURIComponent(query);
        const url = `https://cn.bing.com/search?q=${encodedQuery}&FORM=HDRSC1`;
        return performSearch('bing', url, query, 1, includeLinks);
    }
    async function search_baidu(query, pageStr, includeLinks = false) {
        let page = 1;
        if (pageStr) {
            page = parseInt(pageStr, 10);
        }
        const pn = (page - 1) * 10;
        const encodedQuery = encodeURIComponent(query);
        const url = `https://www.baidu.com/s?wd=${encodedQuery}&pn=${pn}`;
        return performSearch('baidu', url, query, page, includeLinks);
    }
    async function search_sogou(query, pageStr, includeLinks = false) {
        let page = 1;
        if (pageStr) {
            page = parseInt(pageStr, 10);
        }
        const encodedQuery = encodeURIComponent(query);
        const url = `https://www.sogou.com/web?query=${encodedQuery}&page=${page}`;
        return performSearch('sogou', url, query, page, includeLinks);
    }
    async function search_quark(query, pageStr, includeLinks = false) {
        let page = 1;
        if (pageStr) {
            page = parseInt(pageStr, 10);
        }
        const encodedQuery = encodeURIComponent(query);
        const url = `https://quark.sm.cn/s?q=${encodedQuery}&page=${page}`;
        return performSearch('quark', url, query, page, includeLinks);
    }
    const searchFunctions = {
        bing: search_bing,
        baidu: search_baidu,
        sogou: search_sogou,
        quark: search_quark
    };
    async function combined_search(query, platforms, includeLinks = false) {
        const platformKeysRaw = platforms.split(',');
        const platformKeys = [];
        for (const platform of platformKeysRaw) {
            const trimmedPlatform = platform.trim();
            if (trimmedPlatform) {
                platformKeys.push(trimmedPlatform);
            }
        }
        const searchPromises = [];
        for (const platform of platformKeys) {
            const searchFn = searchFunctions[platform];
            if (searchFn) {
                // 注意：这里我们假设组合搜索总是从第一页开始
                searchPromises.push(searchFn(query, '1', includeLinks));
            }
            else {
                searchPromises.push(Promise.resolve({ platform, success: false, message: `不支持的搜索平台: ${platform}` }));
            }
        }
        return Promise.all(searchPromises);
    }
    async function main() {
        const result = await combined_search('如何学习编程', 'bing,baidu,sogou,quark');
        console.log(JSON.stringify(result, null, 2));
    }
    function wrap(coreFunction) {
        return async (params) => {
            // wrap函数负责将JSON对象参数解构为独立参数
            // 并调用核心函数。
            // 它直接返回核心函数的JSON结果，不做任何修改。
            const args = Object.values(params);
            return coreFunction(...args);
        };
    }
    return {
        search_bing,
        search_baidu,
        search_sogou,
        search_quark,
        combined_search,
        wrap,
        main
    };
})();
exports.search_bing = various_search.wrap(various_search.search_bing);
exports.search_baidu = various_search.wrap(various_search.search_baidu);
exports.search_sogou = various_search.wrap(various_search.search_sogou);
exports.search_quark = various_search.wrap(various_search.search_quark);
exports.combined_search = various_search.wrap(various_search.combined_search);
exports.main = various_search.main;

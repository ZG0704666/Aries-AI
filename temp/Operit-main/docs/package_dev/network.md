# API 文档: `network.d.ts`

本文档详细介绍了 `network.d.ts` 文件中定义的 API，该 API 提供了在脚本中进行网络请求和数据交互的功能。

## 概述

所有网络相关的功能都封装在全局的 `Tools.Net` 命名空间下。这个模块提供了从简单的 GET/POST 请求到支持完整配置的高级 HTTP 客户端，以及文件上传和 Cookie 管理等功能。

---

## `Tools.Net` 命名空间详解

### 基础请求函数

-   `httpGet(url: string): Promise<HttpResponseData>`:
    发起一个简单的 HTTP GET 请求。适用于快速获取网页内容或调用 REST API。

-   `httpPost(url: string, data: string | object): Promise<HttpResponseData>`:
    发起一个 HTTP POST 请求。
    -   如果 `data` 是一个对象，它会被自动序列化为 JSON 字符串，并且 `Content-Type` 头会被设置为 `application/json`。
    -   如果 `data` 是一个字符串，它会按原样作为请求体发送。

-   `visit(urlOrParams: string | object): Promise<VisitWebResultData>`:
    访问一个网页并提取其内容。此函数支持两种调用方式：
    1.  **通过 URL 访问**:
        -   传入一个字符串 `url` 来访问一个新页面。
        -   `Tools.Net.visit("https://example.com")`
    2.  **通过链接编号继续访问**:
        -   传入一个对象，包含上一次访问返回的 `visit_key` 和要点击的链接编号 `link_number`。
        -   `Tools.Net.visit({ visit_key: "some-key-from-previous-result", link_number: 3 })`
    -   函数返回一个 `VisitWebResultData` 对象，其中包含页面标题、内容、提取到的链接列表以及用于后续交互的 `visit_key`。

### 高级请求函数

-   `http(options: object): Promise<HttpResponseData>`:
    一个功能全面的 HTTP 请求函数，允许你详细配置请求的各个方面。
    -   **`options`** 对象可以包含：
        -   `url: string`: (必须) 请求的目标 URL。
        -   `method?: 'GET' | 'POST' | ...`: HTTP 请求方法。
        -   `headers?: object`: 一个包含 HTTP 请求头的键值对对象。
        -   `data?: string | object`: 请求体。
        -   `timeout?: number`: 超时时间（毫秒）。
        -   `followRedirects?: boolean`: 是否自动跟随重定向。
        -   `responseType?: 'text' | 'json' | ...`: 期望的响应类型。

-   `uploadFile(options: object): Promise<HttpResponseData>`:
    使用 `multipart/form-data` 格式上传文件。
    -   **`options`** 对象可以包含：
        -   `url: string`: (必须) 上传的目标 URL。
        -   `filePath: string`: (必须) 要上传的本地文件的路径。
        -   `method?: 'POST' | 'PUT'`: HTTP 方法。
        -   `fileFieldName?: string`: 文件在表单中的字段名。
        -   `formFields?: object`: 其他需要一同提交的文本表单字段。
        -   `headers?: object`: 自定义的请求头。

### Cookie 管理

-   `cookies: CookieManager`:
    一个用于管理 HTTP Cookie 的对象。
    -   `cookies.get(domain: string): Promise<HttpResponseData>`: 获取指定域名的 Cookie。
    -   `cookies.set(domain: string, cookies: string | object): Promise<HttpResponseData>`: 为指定域名设置 Cookie。
    -   `cookies.clear(domain: string): Promise<HttpResponseData>`: 清除指定域名的 Cookie。

**示例: 发起 GET 和 POST 请求**
```typescript
async function networkRequests() {
    try {
        // GET 请求
        const getResponse = await Tools.Net.httpGet("https://api.github.com/users/openai");
        const userData = JSON.parse(getResponse.content);
        console.log(`OpenAI's public repos: ${userData.public_repos}`);

        // POST 请求
        const postData = {
            title: "foo",
            body: "bar",
            userId: 1
        };
        const postResponse = await Tools.Net.httpPost("https://jsonplaceholder.typicode.com/posts", postData);
        console.log(`POST a new post, server response: ${postResponse.statusCode}`);
        
        complete({ success: true, message: "网络请求示例完成。" });
    } catch (error) {
        complete({ success: false, message: `网络请求失败: ${error.message}` });
    }
}
```

**示例: 使用高级 `http` 函数**
```typescript
async function advancedHttp() {
    try {
        const response = await Tools.Net.http({
            url: "https://httpbin.org/headers",
            method: 'GET',
            headers: {
                "X-Custom-Header": "MyValue",
                "User-Agent": "MyScript/1.0"
            }
        });
        console.log(response.content);
        complete({ success: true, message: "高级 HTTP 请求完成。" });
    } catch (error) {
        complete({ success: false, message: `请求失败: ${error.message}` });
    }
}
``` 
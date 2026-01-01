# API 文档: `okhttp.d.ts`

本文档详细介绍了 `okhttp.d.ts` 文件中定义的 API。它为脚本环境提供了一个强大的、基于 Java [OkHttp3](https://square.github.io/okhttp/) 库的 HTTP 客户端。

## 概述

与 `Tools.Net.http` 相比，全局 `OkHttp` 对象提供了一个更高级、更面向对象、可配置性更强的网络请求接口。它允许你创建可复用的客户端实例，管理拦截器，并使用构建者模式（Builder Pattern）来构造复杂的请求。

-   **`OkHttp`**: 全局对象，用于创建 `OkHttpClient` 实例。
-   **`OkHttpClientBuilder`**: 用于配置和构建 `OkHttpClient`。
-   **`OkHttpClient`**: 用于执行 HTTP 请求的客户端实例。
-   **`RequestBuilder`**: 用于构造具体的 `HttpRequest` 对象。
-   **`HttpRequest`**: 代表一个即将被执行的请求。
-   **`Response`**: 代表从服务器返回的响应。

---

## `OkHttp` 对象详解

这是一个全局可用的对象，作为创建 HTTP 客户端的入口。

-   `newClient(): OkHttpClient`:
    创建一个具有默认配置的 `OkHttpClient` 实例。

-   `newBuilder(): OkHttpClientBuilder`:
    创建一个 `OkHttpClientBuilder` 实例，允许你自定义客户端配置。

---

## 构建和配置客户端 (`OkHttpClientBuilder`)

通过 `OkHttp.newBuilder()` 获取一个构建器实例，你可以链式调用以下方法进行配置：

-   `connectTimeout(timeout: number): OkHttpClientBuilder`: 设置连接超时时间（毫秒）。
-   `readTimeout(timeout: number): OkHttpClientBuilder`: 设置读取超时时间（毫秒）。
-   `writeTimeout(timeout: number): OkHttpClientBuilder`: 设置写入超时时间（毫秒）。
-   `followRedirects(follow: boolean): OkHttpClientBuilder`: 设置是否自动跟随重定向。
-   `retryOnConnectionFailure(retry: boolean): OkHttpClientBuilder`: 设置在连接失败时是否自动重试。
-   `addInterceptor(interceptor: function): OkHttpClientBuilder`: 添加一个请求拦截器。
-   `build(): OkHttpClient`: 完成配置并构建 `OkHttpClient` 实例。

---

## 执行请求 (`OkHttpClient`)

一旦你有了 `OkHttpClient` 实例，就可以用它来发送请求。

-   `newRequest(): RequestBuilder`:
    获取一个 `RequestBuilder` 实例，用于从零开始构建一个请求。

-   `execute(request: HttpRequest): Promise<Response>`:
    执行一个已经构建好的 `HttpRequest` 对象。

-   **便捷方法**:
    -   `get(url: string, headers?: object): Promise<Response>`
    -   `post(url: string, body: any, headers?: object): Promise<Response>`
    -   `put(url: string, body: any, headers?: object): Promise<Response>`
    -   `delete(url: string, headers?: object): Promise<Response>`

---

## 构建请求 (`RequestBuilder`)

通过 `client.newRequest()` 获取一个构建器实例，你可以链式调用以下方法来定义一个请求：

-   `url(url: string): RequestBuilder`: 设置请求 URL。
-   `method(method: string): RequestBuilder`: 设置 HTTP 方法 (GET, POST, etc.)。
-   `header(name: string, value: string): RequestBuilder`: 添加单个请求头。
-   `headers(headers: object): RequestBuilder`: 批量添加请求头。
-   `body(body: any, type?: 'text' | 'json' | 'form' | 'multipart'): RequestBuilder`: 设置请求体和类型。
-   `jsonBody(data: any): RequestBuilder`: `body(data, 'json')` 的快捷方式。
-   `formParam(name: string, value: string): RequestBuilder`: 添加一个表单参数。
-   `multipartParam(name: string, value: string, contentType?: string): RequestBuilder`: 添加一个 multipart 参数。
-   `build(): HttpRequest`: 完成并构建 `HttpRequest` 对象。

---

## 处理响应 (`Response`)

所有执行请求的方法最终都会返回一个 `Response` 对象的 `Promise`。

**属性:**

-   `statusCode: number`: HTTP 状态码 (e.g., 200, 404)。
-   `statusMessage: string`: HTTP 状态消息 (e.g., "OK", "Not Found")。
-   `headers: object`: 响应头。
-   `content: string`: 响应体（作为字符串）。
-   `contentType: string`: `Content-Type` 响应头的值。
-   `size: number`: 响应体的大小（字节）。

**方法:**

-   `json(): Promise<any>`: 将响应体解析为 JSON 对象。
-   `text(): Promise<string>`: 将响应体作为文本获取（与 `content` 属性类似）。
-   `bodyAsBase64(): Promise<string>`: 获取 Base64 编码的响应体。
-   `isSuccessful(): boolean`: 检查状态码是否在 200-299 范围内。

**示例: 发起一个带自定义头的 GET 请求**
```typescript
async function okHttpGetExample() {
    const client = OkHttp.newClient();
    try {
        const request = client.newRequest()
            .url("https://httpbin.org/get")
            .header("X-API-Key", "my-secret-key")
            .build();

        const response = await client.execute(request);

        if (response.isSuccessful()) {
            const data = response.json();
            console.log(`Response Headers: ${JSON.stringify(data.headers)}`);
            complete({ success: true, message: "OkHttp GET 请求成功。" });
        } else {
            complete({ success: false, message: `请求失败，状态码: ${response.statusCode}` });
        }
    } catch (error) {
        complete({ success: false, message: `请求出错: ${error.message}` });
    }
}
```

**示例: POST JSON 数据并自定义客户端**
```typescript
async function okHttpPostExample() {
    // 创建一个自定义配置的客户端
    const client = OkHttp.newBuilder()
        .connectTimeout(5000) // 5秒连接超时
        .readTimeout(10000)   // 10秒读取超时
        .build();

    try {
        const payload = { user: "test", score: 100 };
        const response = await client.post("https://httpbin.org/post", payload);
        const data = response.json();
        
        console.log(`服务器收到的 JSON: ${data.data}`);
        complete({ success: true, message: "OkHttp POST 请求成功。" });

    } catch (error) {
        complete({ success: false, message: `请求出错: ${error.message}` });
    }
}
``` 
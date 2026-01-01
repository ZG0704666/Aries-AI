/* METADATA
{
  "name": "nanobanana_draw",
  "description": "使用 Nano Banana API (基于Grsai的api服务/https://grsai.com/) 根据提示词画图，支持文生图和图生图（可传入参考图片URL或本地图片路径；本地图片会先上传到图床以获得公网URL），将图片保存到本地 /sdcard/Download/Operit/draws/ 目录，并返回 Markdown 图片提示。",
  "env": [
    "NANOBANANA_API_KEY",
    "BEEIMG_API_KEY"
  ],
  "tools": [
    {
      "name": "draw_image",
      "description": "根据提示词调用 Nano Banana API 生成图片（支持文生图和图生图），保存到本地并返回 Markdown 图片提示。",
      "parameters": [
        { "name": "prompt", "description": "绘图提示词（英文或中文皆可）", "type": "string", "required": true },
        { "name": "model", "description": "Nano Banana 模型名称，默认 nano-banana-pro", "type": "string", "required": false },
        { "name": "aspect_ratio", "description": "输出图像比例，如 '1:1', '16:9', 'auto' 等，可选", "type": "string", "required": false },
        { "name": "image_size", "description": "输出图像大小，仅 nano-banana-pro 支持，如 '1K', '2K', '4K'，可选", "type": "string", "required": false },
        { "name": "image_urls", "description": "参考图URL数组（图生图），支持格式：字符串数组['https://...'] 或 JSON字符串'[\"https://...\"]' 或逗号分隔'url1,url2'，可选", "type": "array", "required": false },
        { "name": "image_paths", "description": "参考图本地路径数组（图生图，会先上传图床再进行生成），支持格式：字符串数组['/sdcard/...'] 或 JSON字符串 或 逗号分隔，可选", "type": "array", "required": false },
        { "name": "file_name", "description": "自定义保存到本地的文件名（不含路径和扩展名）", "type": "string", "required": false },
        { "name": "poll_interval_ms", "description": "轮询间隔（毫秒），默认 5000", "type": "number", "required": false },
        { "name": "max_wait_time_ms", "description": "最长等待时间（毫秒）。默认 5 分钟；当 image_size=4K 时默认 15 分钟", "type": "number", "required": false }
      ]
    }
  ]
}
*/
const nanobananaDraw = (function () {
    const client = OkHttp.newClient();
    const BEEIMG_UPLOAD_ENDPOINT = "https://beeimg.com/api/upload/file/json/";
    // API配置
    const API_ENDPOINT = "https://grsai.dakka.com.cn/v1/draw/nano-banana";
    const RESULT_ENDPOINT = "https://grsai.dakka.com.cn/v1/draw/result";
    const DEFAULT_MODEL = "nano-banana-pro";
    // Android 实际路径为 /sdcard/Download，对应系统中文名"下载"
    const DOWNLOAD_ROOT = "/sdcard/Download";
    const OPERIT_DIR = `${DOWNLOAD_ROOT}/Operit`;
    const DRAWS_DIR = `${OPERIT_DIR}/draws`;
    // 轮询配置
    const POLL_INTERVAL = 5000; // 每5秒查询一次
    const MAX_WAIT_TIME = 300000; // 最多等待5分钟
    function isRecord(value) {
        return typeof value === "object" && value !== null;
    }
    function getErrorMessage(error) {
        if (error instanceof Error)
            return error.message;
        return String(error);
    }
    function getErrorStack(error) {
        if (error instanceof Error)
            return error.stack;
        return undefined;
    }
    function normalizePositiveInt(value, fallback) {
        if (value === undefined || value === null) {
            return fallback;
        }
        const n = typeof value === "number" ? value : parseInt(String(value), 10);
        if (!Number.isFinite(n) || n <= 0) {
            return fallback;
        }
        return Math.floor(n);
    }
    function getApiKey() {
        const apiKey = getEnv("NANOBANANA_API_KEY");
        if (!apiKey) {
            throw new Error("NANOBANANA_API_KEY 未配置，请在环境变量中设置 Nano Banana 的 API Key。");
        }
        return apiKey;
    }
    function getBeeimgApiKey() {
        return getEnv("BEEIMG_API_KEY") || "";
    }
    function guessMimeTypeFromPath(filePath) {
        const lower = filePath.toLowerCase();
        if (lower.endsWith(".png"))
            return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
            return "image/jpeg";
        if (lower.endsWith(".webp"))
            return "image/webp";
        if (lower.endsWith(".gif"))
            return "image/gif";
        return "application/octet-stream";
    }
    function safeJsonParseLoose(text) {
        const trimmed = (text || "").trim();
        if (!trimmed)
            return null;
        try {
            return JSON.parse(trimmed);
        }
        catch (e) {
            const start = trimmed.indexOf("{");
            const end = trimmed.lastIndexOf("}");
            if (start !== -1 && end !== -1 && end > start) {
                return JSON.parse(trimmed.substring(start, end + 1));
            }
            throw e;
        }
    }
    async function uploadImageToBeeimg(filePath) {
        const exists = await Tools.Files.exists(filePath);
        if (!exists.exists) {
            throw new Error(`参考图文件不存在: ${filePath}`);
        }
        const apiKey = getBeeimgApiKey();
        if (!apiKey) {
            throw new Error("使用 image_paths 需要配置 BEEIMG_API_KEY（用于把本地图片上传到图床以获得公网URL）。");
        }
        const resp = await Tools.Net.uploadFile({
            url: BEEIMG_UPLOAD_ENDPOINT,
            method: "POST",
            form_data: {
                apikey: apiKey
            },
            files: [
                {
                    field_name: "file",
                    file_path: filePath,
                    content_type: guessMimeTypeFromPath(filePath)
                }
            ]
        });
        if (resp.statusCode < 200 || resp.statusCode >= 300) {
            throw new Error(`BeeIMG 上传失败: HTTP ${resp.statusCode} - ${resp.content}`);
        }
        let parsed;
        try {
            parsed = safeJsonParseLoose(resp.content);
        }
        catch (e) {
            throw new Error(`BeeIMG 上传响应解析失败: ${getErrorMessage(e)}`);
        }
        const files = isRecord(parsed) && isRecord(parsed["files"]) ? parsed["files"] : null;
        const ok = !!files && (files["status"] === "Success" || files["code"] === "200" || files["code"] === 200);
        const url = files ? files["url"] : undefined;
        if (!ok || (typeof url !== "string" && typeof url !== "number") || String(url).trim().length === 0) {
            throw new Error(`BeeIMG 上传失败: ${resp.content}`);
        }
        return String(url);
    }
    function sanitizeFileName(name) {
        const safe = name.replace(/[\\/:*?"<>|]/g, "_").trim();
        if (!safe) {
            return `nano_draw_${Date.now()}`;
        }
        return safe.substring(0, 80);
    }
    function buildFileName(prompt, customName) {
        if (customName && customName.trim().length > 0) {
            return sanitizeFileName(customName);
        }
        const shortPrompt = prompt.length > 40 ? `${prompt.substring(0, 40)}...` : prompt;
        const base = sanitizeFileName(shortPrompt || "image");
        const timestamp = Date.now();
        return `${base}_${timestamp}`;
    }
    async function ensureDirectories() {
        const dirs = [DOWNLOAD_ROOT, OPERIT_DIR, DRAWS_DIR];
        for (const dir of dirs) {
            try {
                const result = await Tools.Files.mkdir(dir);
                if (!result.successful) {
                    console.warn(`创建目录失败(可能已存在): ${dir} -> ${result.details}`);
                }
            }
            catch (e) {
                console.warn(`创建目录异常: ${dir} -> ${getErrorMessage(e)}`);
            }
        }
    }
    async function callNanobananaApi(params) {
        var _a, _b;
        const apiKey = getApiKey();
        const model = (params.model && params.model.trim().length > 0)
            ? params.model.trim()
            : DEFAULT_MODEL;
        // 构建请求体 - 使用异步模式（webHook: "-1"）
        const body = {
            model: model,
            prompt: params.prompt,
            webHook: "-1", // 关键：立即返回任务ID
            shutProgress: false
        };
        // 添加可选参数
        if (params.aspect_ratio && params.aspect_ratio.trim().length > 0) {
            body.aspectRatio = params.aspect_ratio.trim();
        }
        if (params.image_size && params.image_size.trim().length > 0) {
            body.imageSize = params.image_size.trim();
        }
        // 图生图：添加参考图URL数组
        // 支持格式：['https://example.com/1.jpg', 'https://example.com/2.jpg']
        // 或 JSON字符串："[\"https://...\"]"
        // 或 逗号分隔："url1,url2"
        if (params.image_urls && Array.isArray(params.image_urls) && params.image_urls.length > 0) {
            body.urls = params.image_urls.filter(url => url && url.trim().length > 0);
        }
        const headers = {
            "accept": "application/json",
            "content-type": "application/json",
            "Authorization": `Bearer ${apiKey}`
        };
        const request = client
            .newRequest()
            .url(API_ENDPOINT)
            .method("POST")
            .headers(headers)
            .body(JSON.stringify(body), "json");
        console.log("步骤1/2: 提交绘图任务...");
        const response = await request.build().execute();
        if (!response.isSuccessful()) {
            throw new Error(`Nano Banana API 调用失败: ${response.statusCode} - ${response.content}`);
        }
        let parsed;
        try {
            parsed = JSON.parse(response.content);
        }
        catch (e) {
            throw new Error(`解析 Nano Banana 响应失败: ${getErrorMessage(e)}`);
        }
        if (!isRecord(parsed) || !isRecord(parsed["data"]) || typeof parsed["data"]["id"] !== "string") {
            throw new Error("API响应中未找到任务ID，请检查参数是否正确。响应: " + JSON.stringify(parsed));
        }
        const taskId = parsed["data"]["id"];
        console.log(`任务提交成功! ID: ${taskId}`);
        const pollIntervalMs = (_a = params.poll_interval_ms) !== null && _a !== void 0 ? _a : POLL_INTERVAL;
        const maxWaitTimeMs = (_b = params.max_wait_time_ms) !== null && _b !== void 0 ? _b : MAX_WAIT_TIME;
        console.log(`步骤2/2: 等待任务完成（轮询中，每${pollIntervalMs / 1000}秒查询一次，最长等待${Math.ceil(maxWaitTimeMs / 60000)}分钟）...`);
        return taskId;
    }
    async function pollForResult(taskId, options) {
        const apiKey = getApiKey();
        const startTime = Date.now();
        let attempts = 0;
        const pollIntervalMs = normalizePositiveInt(options === null || options === void 0 ? void 0 : options.poll_interval_ms, POLL_INTERVAL);
        const maxWaitTimeMs = normalizePositiveInt(options === null || options === void 0 ? void 0 : options.max_wait_time_ms, MAX_WAIT_TIME);
        // 构建结果查询请求
        const requestBody = JSON.stringify({ id: taskId });
        const headers = {
            "accept": "application/json",
            "content-type": "application/json",
            "Authorization": `Bearer ${apiKey}`
        };
        while (Date.now() - startTime < maxWaitTimeMs) {
            attempts++;
            console.log(`第${attempts}次查询任务状态...`);
            // 查询结果
            const request = client
                .newRequest()
                .url(RESULT_ENDPOINT)
                .method("POST")
                .headers(headers)
                .body(requestBody, "json");
            const response = await request.build().execute();
            if (!response.isSuccessful()) {
                throw new Error(`查询结果失败: ${response.statusCode} - ${response.content}`);
            }
            let parsed;
            try {
                parsed = JSON.parse(response.content);
            }
            catch (e) {
                throw new Error(`解析结果响应失败: ${getErrorMessage(e)}`);
            }
            if (!isRecord(parsed) || parsed["code"] !== 0 || !isRecord(parsed["data"])) {
                console.warn(`查询响应异常: ${JSON.stringify(parsed)}`);
                await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
                continue;
            }
            const data = parsed["data"];
            const progress = typeof data["progress"] === "number" ? data["progress"] : 0;
            const status = typeof data["status"] === "string" ? data["status"] : "unknown";
            console.log(`当前进度: ${progress}% | 状态: ${status}`);
            if (status === "succeeded") {
                console.log("✅ 任务完成!");
                const results = data["results"];
                const first = Array.isArray(results) && results.length > 0 ? results[0] : null;
                const url = isRecord(first) ? first["url"] : undefined;
                if ((typeof url !== "string" && typeof url !== "number") || String(url).trim().length === 0) {
                    throw new Error("任务完成但响应中未找到图片URL: " + JSON.stringify(data));
                }
                return String(url);
            }
            else if (status === "failed") {
                const reason = typeof data["failure_reason"] === "string" ? data["failure_reason"] : "未知原因";
                const error = typeof data["error"] === "string" ? data["error"] : "";
                throw new Error(`任务执行失败: ${reason} - ${error}`);
            }
            else if (status === "running" && progress > 0) {
                console.log(`生成中... 进度: ${progress}%`);
            }
            // 等待后再次查询
            await new Promise(resolve => setTimeout(resolve, pollIntervalMs));
        }
        throw new Error(`任务超时: 等待超过${Math.ceil(maxWaitTimeMs / 60000)}分钟仍未完成`);
    }
    function guessExtensionFromUrl(url) {
        const match = url.match(/\.(png|jpg|jpeg|webp|gif)(?:\?|#|$)/i);
        if (match && match[1]) {
            return match[1].toLowerCase();
        }
        return "png";
    }
    async function draw_image(params) {
        var _a;
        if (!params || !params.prompt || params.prompt.trim().length === 0) {
            throw new Error("参数 prompt 不能为空。");
        }
        const prompt = params.prompt.trim();
        const pollIntervalMs = normalizePositiveInt(params.poll_interval_ms, POLL_INTERVAL);
        const normalizedImageSize = params.image_size ? params.image_size.trim().toUpperCase() : "";
        const defaultMaxWaitTimeMs = normalizedImageSize === "4K" ? 900000 : MAX_WAIT_TIME;
        const maxWaitTimeMs = normalizePositiveInt(params.max_wait_time_ms, defaultMaxWaitTimeMs);
        // 添加辅助函数来解析URL数组
        function parseImageUrls(image_urls) {
            // 如果已经是数组，直接过滤空值返回
            if (Array.isArray(image_urls)) {
                return image_urls.filter(url => url && url.trim().length > 0);
            }
            // 如果是字符串，尝试解析
            if (typeof image_urls === "string") {
                // 方法一：尝试JSON解析
                try {
                    const parsed = JSON.parse(image_urls);
                    if (Array.isArray(parsed)) {
                        return parsed.filter((url) => typeof url === "string" && url.trim().length > 0);
                    }
                }
                catch (e) {
                    // 解析失败继续方法二
                }
                // 方法二：按逗号分割（支持 "url1,url2" 格式）
                const splitUrls = image_urls.split(",")
                    .map(url => url.trim())
                    .filter(url => url.length > 0);
                if (splitUrls.length > 0) {
                    return splitUrls;
                }
            }
            return [];
        }
        function parseImagePaths(image_paths) {
            if (Array.isArray(image_paths)) {
                return image_paths.filter(p => p && String(p).trim().length > 0).map(p => String(p).trim());
            }
            if (typeof image_paths === "string") {
                try {
                    const parsed = JSON.parse(image_paths);
                    if (Array.isArray(parsed)) {
                        return parsed.filter((p) => p && String(p).trim().length > 0).map((p) => String(p).trim());
                    }
                }
                catch (e) {
                    // ignore
                }
                const splitPaths = image_paths.split(",")
                    .map(p => p.trim())
                    .filter(p => p.length > 0);
                if (splitPaths.length > 0) {
                    return splitPaths;
                }
            }
            return [];
        }
        // 替换原有的验证逻辑
        let imageUrlsArray = [];
        if (params.image_urls) {
            imageUrlsArray = parseImageUrls(params.image_urls);
            if (imageUrlsArray.length === 0) {
                throw new Error("参数 image_urls 必须是有效的URL数组。");
            }
        }
        let imagePathsArray = [];
        if (params.image_paths) {
            imagePathsArray = parseImagePaths(params.image_paths);
            if (imagePathsArray.length === 0) {
                throw new Error("参数 image_paths 必须是有效的本地路径数组。");
            }
        }
        if (imagePathsArray.length > 0) {
            console.log(`检测到 ${imagePathsArray.length} 张本地参考图，开始上传以获得公网URL...`);
            for (const p of imagePathsArray) {
                const url = await uploadImageToBeeimg(p);
                imageUrlsArray.push(url);
            }
            console.log("本地参考图上传完成。");
        }
        await ensureDirectories();
        // 步骤1: 提交任务并获取任务ID
        const taskId = await callNanobananaApi({
            prompt,
            model: params.model,
            aspect_ratio: params.aspect_ratio,
            image_size: params.image_size,
            image_urls: imageUrlsArray,
            poll_interval_ms: pollIntervalMs,
            max_wait_time_ms: maxWaitTimeMs
        });
        // 步骤2: 轮询等待任务完成
        const imageUrl = await pollForResult(taskId, { poll_interval_ms: pollIntervalMs, max_wait_time_ms: maxWaitTimeMs });
        const ext = guessExtensionFromUrl(imageUrl);
        const baseName = buildFileName(prompt, (_a = params.file_name) !== null && _a !== void 0 ? _a : null);
        const filePath = `${DRAWS_DIR}/${baseName}.${ext}`;
        const downloadResult = await Tools.Files.download(imageUrl, filePath);
        if (!downloadResult.successful) {
            throw new Error(`下载图片失败: ${downloadResult.details}`);
        }
        const fileUri = `file://${filePath}`;
        const markdown = `![AI生成的图片](${fileUri})`;
        const hintLines = [];
        hintLines.push("图片已生成并保存在本地 /sdcard/Download/Operit/draws/ 目录。");
        hintLines.push(`本地路径: ${filePath}`);
        hintLines.push("");
        hintLines.push("在后续回答中，请直接输出下面这一行 Markdown 来展示这张图片：");
        hintLines.push("");
        hintLines.push(markdown);
        return {
            file_path: filePath,
            file_uri: fileUri,
            markdown,
            prompt,
            model: params.model || DEFAULT_MODEL,
            aspect_ratio: params.aspect_ratio,
            image_size: params.image_size,
            image_urls: params.image_urls,
            image_paths: params.image_paths,
            hint: hintLines.join("\n")
        };
    }
    async function draw_image_wrapper(params) {
        try {
            const result = await draw_image(params);
            complete({
                success: true,
                message: "图片生成成功，已保存到 /sdcard/Download/Operit/draws/，并返回 Markdown 图片提示。",
                data: result
            });
        }
        catch (error) {
            console.error("draw_image 执行失败:", error);
            complete({
                success: false,
                message: `图片生成失败: ${getErrorMessage(error)}`,
                error_stack: getErrorStack(error)
            });
        }
    }
    return {
        draw_image: draw_image_wrapper
    };
})();
exports.draw_image = nanobananaDraw.draw_image;

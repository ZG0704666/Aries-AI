/* METADATA
{
  "name": "ai_chat",
  "description": "调用AI模型API实现AI之间智能对话互动。",
  "env": ["AI_API_BASE_URL", "AI_API_KEY", "AI_MODEL_NAME"],
  "tools": [{
    "name": "chat_completion",
    "description": "发送消息给AI模型并获取回复。支持超时保护（默认30秒）。从根源上禁止分点列表输出。",
    "parameters": [
      {"name": "messages", "description": "消息数组或字符串", "type": "any", "required": true},
      {"name": "system_prompt", "description": "系统提示词（会自动追加禁止分点指令）", "type": "string", "required": false},
      {"name": "temperature", "description": "温度参数(0.0-2.0)", "type": "number", "required": false, "default": 0.7},
      {"name": "max_tokens", "description": "最大生成长度", "type": "number", "required": false},
      {"name": "timeout", "description": "超时时间（毫秒）", "type": "number", "required": false, "default": 30000}
    ]
  }]
 } */
/// <reference path="./types/index.d.ts" />
const aiModelInteraction = (function () {
    async function universalHttpRequest(url, method = 'POST', headers = {}, body = null, responseType = 'json', timeout = 30000) {
        const timeoutPromise = new Promise((_, reject) => setTimeout(() => reject(new Error(`请求超时：${timeout}ms`)), timeout));
        const requestPromise = (async () => {
            if (typeof OkHttp === 'undefined') {
                throw new Error('OkHttp 不可用');
            }
            const client = OkHttp.newBuilder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
            const requestBuilder = client.newRequest()
                .url(url)
                .method(method.toUpperCase());
            if (headers && Object.keys(headers).length > 0) {
                requestBuilder.headers(headers);
            }
            const upperMethod = method.toUpperCase();
            if (body !== null && body !== undefined && upperMethod !== 'GET' && upperMethod !== 'HEAD') {
                if (typeof body === 'string') {
                    requestBuilder.body(body, 'text');
                }
                else {
                    requestBuilder.body(body, 'json');
                }
            }
            const response = await requestBuilder.build().execute();
            const resultBody = responseType === 'json' ? response.json() : response.content;
            return {
                status: response.statusCode,
                statusText: response.statusMessage || '',
                headers: response.headers,
                body: resultBody,
            };
        })();
        return Promise.race([requestPromise, timeoutPromise]);
    }
    function cleanText(text) {
        if (!text || typeof text !== 'string')
            return String(text !== null && text !== void 0 ? text : '');
        let cleaned = text;
        // 处理转义与实体
        cleaned = cleaned.replace(/\|["\\]?n/g, '\n');
        cleaned = cleaned.replace(/\\\\([\\nrt"'&])/g, (m, c) => ({ n: '\n', r: '\r', t: '\t', '"': '"', "'": "'", "&": "&" }[c] || m));
        cleaned = cleaned.replace(/\\u([0-9A-Fa-f]{4})/g, (_, h) => String.fromCharCode(parseInt(h, 16)));
        // 一次性替换HTML实体
        const entities = { quot: '"', amp: '&', lt: '<', gt: '>', nbsp: ' ', '#39': "'", apos: "'" };
        cleaned = cleaned.replace(/&(\w+|#\d+);/g, (m, e) => entities[e] || m);
        // 清理代码块
        cleaned = cleaned.replace(/```[\w-]*\s*\n([\s\S]*?)```/g, '$1').replace(/```/g, '').replace(/`([^`]+)`/g, '$1');
        // 格式化空白与分点
        cleaned = cleaned.replace(/[ \t]{2,}/g, ' ');
        cleaned = cleaned.split('\n').map(l => l.replace(/^[\s\uFEFF\xA0\u3000\u200B-\u200D]+/g, '')).join('\n');
        cleaned = cleaned.replace(/\n{3,}/g, '\n\n').trim();
        cleaned = cleaned.replace(/^\s*[-•●]\s+|^\s*\d+\.\s+|^\s*\d+\)\s+|^\s*\([a-zA-Z]\)\s+/gm, '');
        return cleaned || text;
    }
    function getConfig(varName, defaultValue = null) {
        try {
            const value = getEnv(varName);
            if (!value || value === `YOUR_${varName}`) {
                if (defaultValue !== null)
                    return defaultValue;
                throw new Error(`${varName} 未配置`);
            }
            return value.trim();
        }
        catch (e) {
            if (defaultValue !== null)
                return defaultValue;
            throw new Error(`${varName} 未配置`);
        }
    }
    function getFullConfig() {
        try {
            return {
                apiBaseUrl: getConfig('AI_API_BASE_URL', ''),
                apiKey: getConfig('AI_API_KEY', ''),
                modelName: getConfig('AI_MODEL_NAME', ''),
                timeout: 30000
            };
        }
        catch (_a) {
            return { apiBaseUrl: '', apiKey: '', modelName: '', timeout: 30000 };
        }
    }
    function joinUrl(baseUrl, path) {
        const normalizedBase = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
        const normalizedPath = path.startsWith('/') ? path.slice(1) : path;
        return `${normalizedBase}${normalizedPath}`;
    }
    async function tryEndpoints(baseUrl, payload, config, timeout) {
        if (baseUrl.includes('chat/completions')) {
            return await universalHttpRequest(baseUrl, 'POST', {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${config.apiKey}`
            }, payload, 'json', timeout);
        }
        const endpoints = ['v1/chat/completions', 'chat/completions'];
        let lastError;
        for (const endpoint of endpoints) {
            try {
                const url = joinUrl(baseUrl, endpoint);
                const response = await universalHttpRequest(url, 'POST', {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${config.apiKey}`
                }, payload, 'json', timeout);
                if (response.status === 200)
                    return response;
                lastError = new Error(`端点 ${endpoint} 返回 ${response.status}`);
            }
            catch (error) {
                lastError = error;
            }
        }
        throw lastError || new Error('所有端点尝试失败');
    }
    async function chat_completion_logic(rawInput, timeout) {
        var _a, _b;
        if (rawInput === null || rawInput === undefined) {
            throw new Error('参数错误: 输入参数不能为空');
        }
        const internalParams = {
            messages: [],
            system_prompt: undefined,
            temperature: 0.7,
            max_tokens: undefined,
            functions: undefined,
        };
        // 处理输入参数
        if (typeof rawInput === 'string') {
            internalParams.messages = [{ role: 'user', content: rawInput }];
        }
        else if (typeof rawInput === 'object') {
            const hasMessageField = 'message' in rawInput;
            const hasMessagesField = 'messages' in rawInput;
            if (hasMessagesField) {
                const messagesValue = rawInput.messages;
                if (Array.isArray(messagesValue)) {
                    internalParams.messages = messagesValue;
                }
                else if (typeof messagesValue === 'string') {
                    internalParams.messages = [{ role: 'user', content: messagesValue }];
                }
                else {
                    throw new Error(`'messages' 必须是数组或字符串`);
                }
            }
            else if (hasMessageField) {
                const messageValue = rawInput.message;
                if (typeof messageValue !== 'string') {
                    throw new Error(`'message' 必须是字符串`);
                }
                internalParams.messages = [{ role: 'user', content: messageValue }];
            }
            else {
                throw new Error(`对象必须包含 'message' 或 'messages' 字段`);
            }
            internalParams.system_prompt = rawInput.system_prompt ? String(rawInput.system_prompt) : undefined;
            internalParams.temperature = rawInput.temperature !== undefined ? Number(rawInput.temperature) : 0.7;
            if (isNaN(internalParams.temperature))
                internalParams.temperature = 0.7;
            internalParams.max_tokens = rawInput.max_tokens !== undefined ? Number(rawInput.max_tokens) : undefined;
            if (internalParams.max_tokens !== undefined && isNaN(internalParams.max_tokens)) {
                internalParams.max_tokens = undefined;
            }
            internalParams.functions = Array.isArray(rawInput.functions) ? rawInput.functions : undefined;
        }
        else {
            throw new Error(`不支持的参数类型 '${typeof rawInput}'`);
        }
        // 验证messages
        if (!Array.isArray(internalParams.messages) || internalParams.messages.length === 0) {
            throw new Error('messages必须是有效数组且不为空');
        }
        internalParams.messages.forEach((msg, idx) => {
            if (!msg || typeof msg !== 'object' || !msg.role || typeof msg.content !== 'string') {
                throw new Error(`消息 #${idx} 格式无效`);
            }
        });
        // 获取配置
        const config = getFullConfig();
        if (!config.apiBaseUrl || !config.apiKey) {
            throw new Error('AI_API_BASE_URL 和 AI_API_KEY 必须配置');
        }
        // 构建消息数组
        const antiListInstruction = "【重要指令】你必须以连续段落的方式回答，严禁使用任何分点、列表、编号或项目符号格式。";
        const finalMessages = [
            {
                role: 'system',
                content: internalParams.system_prompt
                    ? internalParams.system_prompt + "\n\n" + antiListInstruction
                    : antiListInstruction
            },
            ...internalParams.messages
        ];
        // 构建请求
        const payload = {
            model: String(config.modelName || 'gpt-3.5-turbo'),
            messages: finalMessages,
            temperature: Number(internalParams.temperature)
        };
        if (internalParams.max_tokens !== undefined && !isNaN(internalParams.max_tokens)) {
            payload.max_tokens = Number(internalParams.max_tokens);
        }
        if (internalParams.functions)
            payload.functions = internalParams.functions;
        Object.keys(payload).forEach(key => payload[key] === undefined && delete payload[key]);
        // 发送请求
        let response;
        try {
            response = await tryEndpoints(config.apiBaseUrl, payload, config, timeout);
        }
        catch (_c) {
            response = await universalHttpRequest(config.apiBaseUrl, 'POST', {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${config.apiKey}`
            }, payload, 'json', timeout);
        }
        if (response.status !== 200) {
            let errorMsg = `API请求失败: ${response.status}`;
            try {
                const errorData = typeof response.body === 'string' ? JSON.parse(response.body) : response.body;
                errorMsg += ` - ${((_a = errorData.error) === null || _a === void 0 ? void 0 : _a.message) || JSON.stringify(errorData)}`;
            }
            catch (_d) {
                errorMsg += ` - ${response.statusText}`;
            }
            throw new Error(errorMsg);
        }
        const result = typeof response.body === 'string' ? JSON.parse(response.body) : response.body;
        if (!((_b = result.choices) === null || _b === void 0 ? void 0 : _b[0])) {
            throw new Error('API返回格式异常');
        }
        const choice = result.choices[0];
        const reply = {
            message: choice.message,
            finish_reason: choice.finish_reason,
            usage: result.usage || null,
            model: result.model || config.modelName,
            id: result.id,
            created: result.created
        };
        if (choice.message.function_call) {
            reply.is_function_call = true;
            reply.function_name = choice.message.function_call.name;
            reply.function_arguments = JSON.parse(choice.message.function_call.arguments || '{}');
        }
        return reply;
    }
    async function chat_completion_impl(params) {
        var _a;
        const normalizedParams = typeof params === 'object'
            ? params
            : { messages: String(params) };
        const timeout = normalizedParams.timeout || 30000;
        const result = await Promise.race([
            chat_completion_logic(normalizedParams, timeout),
            new Promise((_, reject) => setTimeout(() => reject(new Error(`操作超时：${timeout}ms`)), timeout))
        ]);
        const rawReply = (_a = result.message) === null || _a === void 0 ? void 0 : _a.content;
        const cleanedReply = cleanText(rawReply);
        return {
            success: true,
            message: "AI回复获取成功！",
            data: result,
            reply: cleanedReply,
            raw_reply: rawReply,
            usage: result.usage,
            anti_list_applied: true
        };
    }
    async function wrapToolExecution(func, params) {
        try {
            const result = await func(params || {});
            complete(result);
        }
        catch (error) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `AI对话失败: ${String(error && error.message ? error.message : error)}`,
                error_stack: error && error.stack
            });
        }
    }
    async function chat_completion(params) {
        return await wrapToolExecution(chat_completion_impl, params);
    }
    async function main(params) {
        var _a, _b;
        const config = getFullConfig();
        if (!config.apiBaseUrl || !config.apiKey) {
            return {
                success: false,
                message: 'AI_API_BASE_URL 和 AI_API_KEY 必须配置',
                config
            };
        }
        return await chat_completion_impl({
            messages: (params === null || params === void 0 ? void 0 : params.message) || 'Hello! Please respond in a continuous paragraph without any list or bullet points.',
            temperature: (_a = params === null || params === void 0 ? void 0 : params.temperature) !== null && _a !== void 0 ? _a : 0.2,
            timeout: (_b = params === null || params === void 0 ? void 0 : params.timeout) !== null && _b !== void 0 ? _b : 15000,
        });
    }
    return {
        chat_completion,
        single_message: chat_completion,
        test_connection: async (params) => wrapToolExecution(async () => {
            var _a;
            return await chat_completion_impl({
                messages: "Hello! Please respond in a continuous paragraph without any list or bullet points.",
                temperature: 0.1,
                timeout: (_a = params === null || params === void 0 ? void 0 : params.timeout) !== null && _a !== void 0 ? _a : 10000
            });
        }, {}),
        main: (params) => wrapToolExecution(main, params),
        getConfig: getFullConfig,
        _makeHttpRequest: universalHttpRequest,
        _cleanText: cleanText
    };
})();
// 导出工具函数（CommonJS）
exports.chat_completion = aiModelInteraction.chat_completion;
exports.single_message = aiModelInteraction.single_message;
exports.test_connection = aiModelInteraction.test_connection;
exports.getConfig = aiModelInteraction.getConfig;
exports.main = aiModelInteraction.main;

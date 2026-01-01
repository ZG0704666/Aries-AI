package com.ai.assistance.operit.core.tools.javascript

/** JavaScript Tools 对象定义 提供了一组便捷的工具调用方法，用于在JavaScript脚本中调用Android工具 */
fun getJsToolsDefinition(): String {
    return """
        // 工具调用的便捷方法
        var Tools = {
            // 文件系统操作
            Files: {
                list: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("list_files", params);
                },
                read: (pathOrOptions) => {
                    let params;
                    if (typeof pathOrOptions === 'string') {
                        // Simple form: read(path)
                        params = { path: pathOrOptions };
                    } else if (pathOrOptions && typeof pathOrOptions === 'object') {
                        // Options form: read({ path, environment?, intent? })
                        params = { ...pathOrOptions };
                    } else {
                        params = {};
                    }
                    return toolCall("read_file_full", params);
                },
                readBinary: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("read_file_binary", params);
                },
                readPart: (path, startLine, endLine, environment) => {
                    const params = { path };
                    if (startLine !== undefined) params.start_line = String(startLine);
                    if (endLine !== undefined) params.end_line = String(endLine);
                    if (environment) params.environment = environment;
                    return toolCall("read_file_part", params);
                },
                write: (path, content, append, environment) => {
                    const params = { path, content };
                    if (append !== undefined) params.append = append ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("write_file", params);
                },
                writeBinary: (path, base64Content, environment) => {
                    const params = { path, base64Content };
                    if (environment) params.environment = environment;
                    return toolCall("write_file_binary", params);
                },
                deleteFile: (path, recursive, environment) => {
                    const params = { path };
                    if (recursive !== undefined) params.recursive = recursive ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("delete_file", params);
                },
                exists: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("file_exists", params);
                },
                move: (source, destination, environment) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    return toolCall("move_file", params);
                },
                copy: (source, destination, recursive, sourceEnvironment, destEnvironment) => {
                    const params = { source, destination };
                    if (recursive !== undefined) params.recursive = recursive ? "true" : "false";
                    if (sourceEnvironment) params.source_environment = sourceEnvironment;
                    if (destEnvironment) params.dest_environment = destEnvironment;
                    return toolCall("copy_file", params);
                },
                mkdir: (path, create_parents, environment) => {
                    const params = { path };
                    if (create_parents !== undefined) params.create_parents = create_parents ? "true" : "false";
                    if (environment) params.environment = environment;
                    return toolCall("make_directory", params);
                },
                find: (path, pattern, options = {}, environment) => {
                    const params = { path, pattern, ...options };
                    if (environment) params.environment = environment;
                    return toolCall("find_files", params);
                },
                grep: (path, pattern, options = {}) => {
                    const params = { path, pattern, ...options };
                    // environment can be included in options
                    return toolCall("grep_code", params);
                },
                grepContext: (path, intent, options = {}) => {
                    const params = { path, intent, ...options };
                    // environment and file_pattern can be included in options
                    return toolCall("grep_context", params);
                },
                info: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("file_info", params);
                },
                // 智能应用文件绑定
                apply: (path, content, environment) => {
                    const params = { path, content };
                    if (environment) params.environment = environment;
                    return toolCall("apply_file", params);
                },
                zip: (source, destination, environment) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    return toolCall("zip_files", params);
                },
                unzip: (source, destination, environment) => {
                    const params = { source, destination };
                    if (environment) params.environment = environment;
                    return toolCall("unzip_files", params);
                },
                open: (path, environment) => {
                    const params = { path };
                    if (environment) params.environment = environment;
                    return toolCall("open_file", params);
                },
                share: (path, title, environment) => {
                    const params = { path };
                    if (title) params.title = title;
                    if (environment) params.environment = environment;
                    return toolCall("share_file", params);
                },
                download: (url, destination, environment) => {
                    const params = { url, destination };
                    if (environment) params.environment = environment;
                    return toolCall("download_file", params);
                },
            },
            // 网络操作
            Net: {
                httpGet: (url) => toolCall("http_request", { url, method: "GET" }),
                httpPost: (url, body) => {
                    const params = { url, method: "POST", body };
                    if (typeof body === 'object') {
                        params.body = JSON.stringify(body);
                    }
                    return toolCall("http_request", params);
                },
                visit: (params) => {
                    if (typeof params === 'string') {
                        // 向后兼容，如果只传入一个字符串，则假定为URL
                        return toolCall("visit_web", { url: params });
                    }
                    // 否则，假定为参数对象
                    return toolCall("visit_web", params);
                },
                // 新增增强版HTTP请求
                http: (options) => {
                    const params = { ...options };
                    if (params.body !== undefined && typeof params.body === 'object') {
                        params.body = JSON.stringify(params.body);
                    }
                    if (params.headers !== undefined && typeof params.headers === 'object') {
                        params.headers = JSON.stringify(params.headers);
                    }
                    return toolCall("http_request", params);
                },
                // 新增文件上传
                uploadFile: (options) => {
                    const params = {
                        ...options,
                        files: JSON.stringify(options.files || []),
                        form_data: JSON.stringify(options.form_data || {})
                    };
                    if (options.headers !== undefined && typeof options.headers === 'object') {
                        params.headers = JSON.stringify(options.headers);
                    }
                    return toolCall("multipart_request", params);
                },
                // 新增Cookie管理
                cookies: {
                    get: (domain) => toolCall("manage_cookies", { action: "get", domain }),
                    set: (domain, cookies) => toolCall("manage_cookies", { action: "set", domain, cookies }),
                    clear: (domain) => toolCall("manage_cookies", { action: "clear", domain })
                }
            },
            // 系统操作
            System: {
                sleep: (milliseconds) => toolCall("sleep", { duration_ms: parseInt(milliseconds) }),
                getSetting: (setting, namespace) => toolCall("get_system_setting", { setting, namespace }),
                setSetting: (setting, value, namespace) => toolCall("modify_system_setting", { setting, value, namespace }),
                getDeviceInfo: () => toolCall("device_info"),
                // 使用工具包
                usePackage: (packageName) => toolCall("use_package", { package_name: packageName }),
                // 安装应用
                installApp: (path) => toolCall("install_app", { path }),
                // 卸载应用
                uninstallApp: (packageName) => toolCall("uninstall_app", { package_name: packageName }),
                startApp: (packageName, activity) => {
                    const params = { package_name: packageName };
                    if (activity) params.activity = activity;
                    return toolCall("start_app", params);
                },
                stopApp: (packageName) => toolCall("stop_app", { package_name: packageName }),
                listApps: (includeSystem) => toolCall("list_installed_apps", { include_system: !!includeSystem }),
                // 获取设备通知
                getNotifications: (limit = 10, includeOngoing = false) => 
                    toolCall("get_notifications", { limit: parseInt(limit), include_ongoing: !!includeOngoing }),
                // 获取设备位置
                getLocation: (highAccuracy = false, timeout = 10) => 
                    toolCall("get_device_location", { high_accuracy: !!highAccuracy, timeout: parseInt(timeout) }),
                shell: (command) => toolCall("execute_shell", { command }),
                // 执行终端命令 - 一次性收集输出
                terminal: {
                    create: (sessionName) => toolCall("create_terminal_session", { session_name: sessionName }),
                    exec: (sessionId, command) => {
                        const params = { session_id: sessionId, command };
                        return toolCall("execute_in_terminal_session", params);
                    },
                    close: (sessionId) => toolCall("close_terminal_session", { session_id: sessionId })
                },
                // 执行Intent
                intent: (options = {}) => {
                    return toolCall("execute_intent", options);
                }
            },
            // Tasker event
            Tasker: {
                triggerEvent: (params) => {
                    return toolCall("trigger_tasker_event", params || {});
                }
            },
            // UI操作
            UI: {
                getPageInfo: () => toolCall("get_page_info"),
                tap: (x, y) => toolCall("tap", { x, y }),
                longPress: (x, y) => toolCall("long_press", { x, y }),
                // 增强的clickElement方法，支持多种参数类型
                clickElement: function(param1, param2, param3) {
                    // 根据参数类型和数量判断调用方式
                    if (typeof param1 === 'object') {
                        // 如果第一个参数是对象，直接传递参数对象
                        return toolCall("click_element", param1);
                    } else if (arguments.length === 1) {
                        // 单参数，假定为resourceId
                        if (param1.startsWith('[') && param1.includes('][')) {
                            // 参数看起来像bounds格式 [x,y][x,y]
                            return toolCall("click_element", { bounds: param1 });
                        }
                        return toolCall("click_element", { resourceId: param1 });
                    } else if (arguments.length === 2) {
                        // 两个参数，假定为(resourceId, index)或(className, index)
                        if (param1 === 'resourceId') {
                            return toolCall("click_element", { resourceId: param2 });
                        } else if (param1 === 'className') {
                            return toolCall("click_element", { className: param2 });
                        } else if (param1 === 'bounds') {
                            return toolCall("click_element", { bounds: param2 });
                        } else {
                            return toolCall("click_element", { resourceId: param1, index: param2 });
                        }
                    } else if (arguments.length === 3) {
                        // 三个参数，假定为(type, value, index)
                        if (param1 === 'resourceId') {
                            return toolCall("click_element", { resourceId: param2, index: param3 });
                        } else if (param1 === 'className') {
                            return toolCall("click_element", { className: param2, index: param3 });
                        } else {
                            return toolCall("click_element", { resourceId: param1, className: param2, index: param3 });
                        }
                    }
                    // 默认情况
                    return toolCall("click_element", { resourceId: param1 });
                },

                setText: (text, resourceId) => {
                    const params = { text };
                    if (resourceId) params.resourceId = resourceId;
                    return toolCall("set_input_text", params);
                },
                swipe: (startX, startY, endX, endY, duration) => {
                    const params = { 
                        start_x: startX, 
                        start_y: startY, 
                        end_x: endX, 
                        end_y: endY 
                    };
                    if (duration) params.duration = duration;
                    return toolCall("swipe", params);
                },
                pressKey: (keyCode) => toolCall("press_key", { key_code: keyCode }),
                /**
                 * Run the built-in UI automation subagent.
                 * @param intent High-level task description for the subagent.
                 * @param maxSteps Optional maximum number of steps (default 20).
                 * @param agentId Optional agent id to reuse the same virtual screen session.
                 */
                runSubAgent: (intent, maxSteps, agentId) => {
                    const params = { intent: String(intent || "") };
                    if (maxSteps !== undefined) params.max_steps = String(maxSteps);
                    if (agentId !== undefined && agentId !== null && String(agentId).length > 0) params.agent_id = String(agentId);
                    return toolCall("run_ui_subagent", params);
                },
            },
            // 记忆管理
            Memory: {
                // 查询记忆库
                query: (query, folderPath, threshold, limit) => {
                    const params = { query };
                    if (folderPath) params.folder_path = folderPath;
                    if (threshold !== undefined) params.threshold = threshold;
                    if (limit !== undefined) params.limit = limit;
                    return toolCall("query_memory", params);
                },
                // 通过标题获取记忆
                getByTitle: (title, chunkIndex, chunkRange, query) => {
                    const params = { title };
                    if (chunkIndex !== undefined) params.chunk_index = chunkIndex;
                    if (chunkRange) params.chunk_range = chunkRange;
                    if (query) params.query = query;
                    return toolCall("get_memory_by_title", params);
                },
                // 创建记忆
                create: (title, content, contentType, source, folderPath) => {
                    const params = { title, content };
                    if (contentType) params.content_type = contentType;
                    if (source) params.source = source;
                    if (folderPath) params.folder_path = folderPath;
                    return toolCall("create_memory", params);
                },
                // 更新记忆
                update: (oldTitle, updates = {}) => {
                    const params = { old_title: oldTitle };
                    if (updates.newTitle) params.new_title = updates.newTitle;
                    if (updates.content) params.content = updates.content;
                    if (updates.contentType) params.content_type = updates.contentType;
                    if (updates.source) params.source = updates.source;
                    if (updates.credibility !== undefined) params.credibility = updates.credibility;
                    if (updates.importance !== undefined) params.importance = updates.importance;
                    if (updates.folderPath) params.folder_path = updates.folderPath;
                    if (updates.tags) params.tags = updates.tags;
                    return toolCall("update_memory", params);
                },
                // 删除记忆
                deleteMemory: (title) => toolCall("delete_memory", { title }),
                // 链接记忆
                link: (sourceTitle, targetTitle, linkType, weight, description) => {
                    const params = { source_title: sourceTitle, target_title: targetTitle };
                    if (linkType) params.link_type = linkType;
                    if (weight !== undefined) params.weight = weight;
                    if (description) params.description = description;
                    return toolCall("link_memories", params);
                }
            },
            // 计算功能
            calc: (expression) => toolCall("calculate", { expression }),
            
            // FFmpeg工具
            FFmpeg: {
                // 执行自定义FFmpeg命令
                execute: (command) => toolCall("ffmpeg_execute", { command }),
                
                // 获取FFmpeg系统信息
                info: () => toolCall("ffmpeg_info"),
                
                // 转换视频文件
                convert: (inputPath, outputPath, options = {}) => {
                    const params = {
                        input_path: inputPath,
                        output_path: outputPath,
                        ...options
                    };
                    return toolCall("ffmpeg_convert", params);
                }
            },
            
            // 工作流工具
            Workflow: {
                // 获取所有工作流
                getAll: () => {
                    return toolCall("get_all_workflows", {});
                },
                // 创建新工作流
                create: (name, description = "", nodes = null, connections = null, enabled = true) => {
                    const params = { name, description, enabled: enabled.toString() };
                    if (nodes) params.nodes = typeof nodes === 'string' ? nodes : JSON.stringify(nodes);
                    if (connections) params.connections = typeof connections === 'string' ? connections : JSON.stringify(connections);
                    return toolCall("create_workflow", params);
                },
                // 获取工作流详情
                get: (workflowId) => {
                    const params = { workflow_id: workflowId };
                    return toolCall("get_workflow", params);
                },
                // 更新工作流
                update: (workflowId, updates = {}) => {
                    const params = { workflow_id: workflowId };
                    if (updates.name) params.name = updates.name;
                    if (updates.description !== undefined) params.description = updates.description;
                    if (updates.nodes) params.nodes = typeof updates.nodes === 'string' ? updates.nodes : JSON.stringify(updates.nodes);
                    if (updates.connections) params.connections = typeof updates.connections === 'string' ? updates.connections : JSON.stringify(updates.connections);
                    if (updates.enabled !== undefined) params.enabled = updates.enabled.toString();
                    return toolCall("update_workflow", params);
                },
                // 删除工作流
                delete: (workflowId) => {
                    const params = { workflow_id: workflowId };
                    return toolCall("delete_workflow", params);
                },
                // 触发工作流执行
                trigger: (workflowId) => {
                    const params = { workflow_id: workflowId };
                    return toolCall("trigger_workflow", params);
                }
            },
            // 对话管理工具
            Chat: {
                // 启动对话服务
                startService: () => toolCall("start_chat_service", {}),
                // 创建新对话
                createNew: () => toolCall("create_new_chat", {}),
                // 列出所有对话
                listAll: () => toolCall("list_chats", {}),
                // 切换对话
                switchTo: (chatId) => toolCall("switch_chat", { chat_id: chatId }),
                // 发送消息给AI
                sendMessage: (message, chatId) => {
                    const params = { message };
                    if (chatId) params.chat_id = chatId;
                    return toolCall("send_message_to_ai", params);
                }
            }
        };
    """.trimIndent()
}

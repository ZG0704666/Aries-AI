package com.ai.assistance.operit.core.config

import com.ai.assistance.operit.data.model.SystemToolPromptCategory
import com.ai.assistance.operit.data.model.ToolPrompt
import com.ai.assistance.operit.data.model.ToolParameterSchema

/**
 * 系统工具提示词管理器
 * 包含所有工具的结构化定义
 */
object SystemToolPrompts {
    
    // ==================== 基础工具 ====================
    val basicTools = SystemToolPromptCategory(
        categoryName = "Available tools",
        tools = listOf(
            ToolPrompt(
                name = "sleep",
                description = "Demonstration tool that pauses briefly.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "duration_ms", type = "integer", description = "milliseconds, default 1000, max 10000", required = false, default = "1000")
                )
            ),
            ToolPrompt(
                name = "use_package",
                description = "Activate a package for use in the current session.",
                parametersStructured = listOf(
                    ToolParameterSchema(
                        name = "package_name",
                        type = "string",
                        description = "name of the package to activate",
                        required = true
                    )
                )
            )
        )
    )
    
    val basicToolsCn = SystemToolPromptCategory(
        categoryName = "可用工具",
        tools = listOf(
            ToolPrompt(
                name = "sleep",
                description = "演示工具，短暂暂停。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "duration_ms", type = "integer", description = "毫秒，默认1000，最大10000", required = false, default = "1000")
                )
            ),
            ToolPrompt(
                name = "use_package",
                description = "在当前会话中激活包。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "package_name", type = "string", description = "要激活的包名", required = true)
                )
            )
        )
    )
    
    // ==================== 文件系统工具 ====================
    val fileSystemTools = SystemToolPromptCategory(
        categoryName = "File System Tools",
        categoryHeader = """**IMPORTANT: All file tools support an optional 'environment' parameter:**
- environment (optional): Specifies the execution environment. Values: "android" (default, Android file system) or "linux" (local Ubuntu 24 terminal environment via proot). 
  - When "linux" is specified, paths use Linux format (e.g., "/home/user/file.txt", "/etc/hosts") and operate in the local Ubuntu 24 environment.

**SSH Remote File System:**""",
        tools = listOf(
            ToolPrompt(
                name = "ssh_login",
                description = "Login to a remote SSH server. After logging in, all file tools with environment=\"linux\" will use this SSH connection instead of the local Ubuntu 24 terminal.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "host", type = "string", description = "SSH server address", required = true),
                    ToolParameterSchema(name = "port", type = "integer", description = "optional", required = false, default = "22"),
                    ToolParameterSchema(name = "username", type = "string", description = "required", required = true),
                    ToolParameterSchema(name = "password", type = "string", description = "required", required = true),
                    ToolParameterSchema(name = "enable_reverse_mount", type = "boolean", description = "optional, enables reverse mounting of local storage to remote server", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "ssh_exit",
                description = "Logout from the SSH connection. After logout, file tools will resume using the local Ubuntu 24 terminal.",
                parametersStructured = listOf()
            ),
            ToolPrompt(
                name = "list_files",
                description = "List files in a directory.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "e.g. \"/sdcard/Download\"", required = true)
                )
            ),
            ToolPrompt(
                name = "read_file",
                description = "Read the content of a file. For image files (jpg, jpeg, png, gif, bmp), it automatically extracts text using OCR.",
                parametersStructured = listOf(
                    ToolParameterSchema(
                        name = "path",
                        type = "string",
                        description = "file path",
                        required = true
                    ),
                    ToolParameterSchema(
                        name = "environment",
                        type = "string",
                        description = "optional, execution environment: \"android\" (default) or \"linux\"",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "intent",
                        type = "string",
                        description = "optional, your question about the media/file (used for backend recognition)",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "direct_image",
                        type = "boolean",
                        description = "optional, when true: return an <link type=\"image\"> tag for models that support vision",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "direct_audio",
                        type = "boolean",
                        description = "optional, when true: return an <link type=\"audio\"> tag for models that support audio",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "direct_video",
                        type = "boolean",
                        description = "optional, when true: return an <link type=\"video\"> tag for models that support video",
                        required = false
                    )
                )
            ),
            ToolPrompt(
                name = "read_file_part",
                description = "Read file content by line range.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "file path", required = true),
                    ToolParameterSchema(name = "start_line", type = "integer", description = "starting line number, 1-indexed", required = false, default = "1"),
                    ToolParameterSchema(name = "end_line", type = "integer", description = "ending line number, 1-indexed, inclusive, optional", required = false, default = "start_line + 99")
                )
            ),
            ToolPrompt(
                name = "apply_file",
                description = "Applies edits to a file by finding and replacing content blocks, or directly overwrites the entire file.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "file path", required = true),
                    ToolParameterSchema(name = "content", type = "string", description = "the string containing all your edit blocks, or the full file content for overwrite mode", required = true)
                ),
                details = """
  - **How it works**: This tool has two modes:
    1. **Edit Mode**: Locates code based on the content inside the `[OLD]` block (not by line numbers) and replaces it with the content from the `[NEW]` block.
    2. **Overwrite Mode**: If no edit blocks (REPLACE/DELETE) are present, the entire content parameter will overwrite the file. This is useful for creating new files or completely rewriting existing ones.
  - **CRITICAL RULES**:
    1.  **Use Semantic Blocks**: `REPLACE` requires both `[OLD]` and `[NEW]` blocks. `DELETE` only requires an `[OLD]` block.
    2.  **Correct Syntax**: All tags (e.g., `[START-REPLACE]`, `[OLD]`) must be on their own lines.
    3.  **Overwrite**: To overwrite a file, simply provide the content without any edit blocks.

  - **Operations & Examples**:
    - **Replace**: `[START-REPLACE]`
      [OLD]
      ...content to be replaced...
      [/OLD]
      [NEW]
      ...new content...
      [/NEW]
      [END-REPLACE]
    - **Delete**: `[START-DELETE]`
      [OLD]
      ...content to be deleted...
      [/OLD]
      [END-DELETE]
    - **Overwrite**: Simply provide the full file content without any blocks (will replace entire file)"""
            ),
            ToolPrompt(
                name = "delete_file",
                description = "Delete a file or directory.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "target path", required = true),
                    ToolParameterSchema(name = "recursive", type = "boolean", description = "boolean", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "file_exists",
                description = "Check if a file or directory exists.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "target path", required = true)
                )
            ),
            ToolPrompt(
                name = "move_file",
                description = "Move or rename a file or directory.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source", type = "string", description = "source path", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "destination path", required = true)
                )
            ),
            ToolPrompt(
                name = "copy_file",
                description = "Copy a file or directory. Supports cross-environment copying between Android and Linux.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source", type = "string", description = "source path", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "destination path", required = true),
                    ToolParameterSchema(name = "recursive", type = "boolean", description = "boolean", required = false, default = "false"),
                    ToolParameterSchema(name = "source_environment", type = "string", description = "optional, \"android\" or \"linux\"", required = false, default = "\"android\""),
                    ToolParameterSchema(name = "dest_environment", type = "string", description = "optional, \"android\" or \"linux\". For cross-environment copy (e.g., Android → Linux or Linux → Android), specify both source_environment and dest_environment", required = false, default = "\"android\"")
                )
            ),
            ToolPrompt(
                name = "make_directory",
                description = "Create a directory.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "directory path", required = true),
                    ToolParameterSchema(name = "create_parents", type = "boolean", description = "boolean", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "find_files",
                description = "Search for files matching a pattern.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "search path, for Android use /sdcard/..., for Linux use /home/... or /etc/...", required = true),
                    ToolParameterSchema(name = "pattern", type = "string", description = "search pattern, e.g. \"*.jpg\"", required = true),
                    ToolParameterSchema(name = "max_depth", type = "integer", description = "optional, controls depth of subdirectory search, -1=unlimited", required = false),
                    ToolParameterSchema(name = "use_path_pattern", type = "boolean", description = "boolean", required = false, default = "false"),
                    ToolParameterSchema(name = "case_insensitive", type = "boolean", description = "boolean", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "grep_code",
                description = "Search code content matching a regex pattern in files. Returns matches with surrounding context lines.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "search path", required = true),
                    ToolParameterSchema(name = "pattern", type = "string", description = "regex pattern", required = true),
                    ToolParameterSchema(name = "file_pattern", type = "string", description = "file filter", required = false, default = "\"*\""),
                    ToolParameterSchema(name = "case_insensitive", type = "boolean", description = "boolean", required = false, default = "false"),
                    ToolParameterSchema(name = "context_lines", type = "integer", description = "lines of context before/after match", required = false, default = "3"),
                    ToolParameterSchema(name = "max_results", type = "integer", description = "max matches", required = false, default = "100")
                )
            ),
            ToolPrompt(
                name = "grep_context",
                description = "Search for relevant content based on intent/context understanding. Supports two modes: 1) Directory mode: when path is a directory, finds most relevant files. 2) File mode: when path is a file, finds most relevant code segments within that file. Uses semantic relevance scoring.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "directory or file path", required = true),
                    ToolParameterSchema(name = "intent", type = "string", description = "intent or context description string", required = true),
                    ToolParameterSchema(name = "file_pattern", type = "string", description = "file filter for directory mode", required = false, default = "\"*\""),
                    ToolParameterSchema(name = "max_results", type = "integer", description = "maximum items to return", required = false, default = "10")
                )
            ),
            ToolPrompt(
                name = "file_info",
                description = "Get detailed information about a file or directory including type, size, permissions, owner, group, and last modified time.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "target path", required = true)
                )
            ),
            ToolPrompt(
                name = "zip_files",
                description = "Compress files or directories.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source", type = "string", description = "path to compress", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "output zip file", required = true)
                )
            ),
            ToolPrompt(
                name = "unzip_files",
                description = "Extract a zip file.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source", type = "string", description = "zip file path", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "extract path", required = true)
                )
            ),
            ToolPrompt(
                name = "open_file",
                description = "Open a file using the system's default application.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "file path", required = true)
                )
            ),
            ToolPrompt(
                name = "share_file",
                description = "Share a file with other applications.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "file path", required = true),
                    ToolParameterSchema(name = "title", type = "string", description = "optional share title", required = false, default = "\"Share File\"")
                )
            ),
            ToolPrompt(
                name = "download_file",
                description = "Download a file from the internet.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "file URL", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "save path", required = true)
                )
            )
        )
    )
    
    val fileSystemToolsCn = SystemToolPromptCategory(
        categoryName = "文件系统工具",
        categoryHeader = """**重要：所有文件工具都支持可选的'environment'参数：**
- environment（可选）：指定执行环境。取值："android"（默认，Android文件系统）或"linux"（本地Ubuntu 24终端环境，通过proot实现）。
  - 当指定"linux"时，路径使用Linux格式（如"/home/user/file.txt"、"/etc/hosts"），在本地Ubuntu 24环境中操作。

**SSH远程文件系统：**""",
        tools = listOf(
            ToolPrompt(
                name = "ssh_login",
                description = "登录远程SSH服务器。登录后，所有environment=\"linux\"的文件工具都将使用此SSH连接，而不是本地Ubuntu 24终端。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "host", type = "string", description = "SSH服务器地址", required = true),
                    ToolParameterSchema(name = "port", type = "integer", description = "可选", required = false, default = "22"),
                    ToolParameterSchema(name = "username", type = "string", description = "必填", required = true),
                    ToolParameterSchema(name = "password", type = "string", description = "必填", required = true),
                    ToolParameterSchema(name = "enable_reverse_mount", type = "boolean", description = "可选，布尔值，启用后将本地存储反向挂载到远程服务器", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "ssh_exit",
                description = "退出SSH连接。退出后，文件工具将恢复使用本地Ubuntu 24终端。",
                parametersStructured = listOf()
            ),
            ToolPrompt(
                name = "list_files",
                description = "列出目录中的文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "例如\"/sdcard/Download\"", required = true)
                )
            ),
            ToolPrompt(
                name = "read_file",
                description = "读取文件内容。对于图片文件(jpg, jpeg, png, gif, bmp)，自动使用OCR提取文本。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "文件路径", required = true),
                    ToolParameterSchema(
                        name = "environment",
                        type = "string",
                        description = "可选，执行环境：\"android\"（默认）或\"linux\"",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "intent",
                        type = "string",
                        description = "可选，用户对媒体/文件的问题（用于后端识别模型）",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "direct_image",
                        type = "boolean",
                        description = "可选，为true时：返回<link type=\"image\">标签供支持识图的模型直接查看",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "direct_audio",
                        type = "boolean",
                        description = "可选，为true时：返回<link type=\"audio\">标签供支持音频的模型直接处理",
                        required = false
                    ),
                    ToolParameterSchema(
                        name = "direct_video",
                        type = "boolean",
                        description = "可选，为true时：返回<link type=\"video\">标签供支持视频的模型直接处理",
                        required = false
                    )
                )
            ),
            ToolPrompt(
                name = "read_file_part",
                description = "按行号范围读取文件内容。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "文件路径", required = true),
                    ToolParameterSchema(name = "start_line", type = "integer", description = "起始行号，从1开始", required = false, default = "1"),
                    ToolParameterSchema(name = "end_line", type = "integer", description = "结束行号，从1开始，包括该行，可选", required = false, default = "start_line + 99")
                )
            ),
            ToolPrompt(
                name = "apply_file",
                description = "通过查找并替换内容块来编辑文件，或直接覆盖整个文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "文件路径", required = true),
                    ToolParameterSchema(name = "content", type = "string", description = "包含所有编辑块的字符串，或用于覆盖模式的完整文件内容", required = true)
                ),
                details = """
  - **工作原理**: 此工具有两种模式：
    1. **编辑模式**: 根据 `[OLD]` 块中的内容（而不是行号）来定位代码，然后用 `[NEW]` 块中的内容替换它。
    2. **覆盖模式**: 如果没有编辑块（REPLACE/DELETE），整个 content 参数的内容将覆盖文件。这对于创建新文件或完全重写现有文件很有用。
  - **关键规则**:
    1.  **使用语义块**: `REPLACE` 操作需要同时包含 `[OLD]` 和 `[NEW]` 块。`DELETE` 操作只需要 `[OLD]` 块。
    2.  **正确的语法**: 所有标签（例如 `[START-REPLACE]`, `[OLD]`）都必须独占一行。
    3.  **覆盖**: 要覆盖文件，只需提供内容而不使用任何编辑块。

  - **操作示例**:
    - **替换**: `[START-REPLACE]`
      [OLD]
      ...要被替换的内容...
      [/OLD]
      [NEW]
      ...新的内容...
      [/NEW]
      [END-REPLACE]
    - **删除**: `[START-DELETE]`
      [OLD]
      ...要被删除的内容...
      [/OLD]
      [END-DELETE]
    - **覆盖**: 直接提供完整的文件内容而不使用任何块（将替换整个文件）"""
            ),
            ToolPrompt(
                name = "delete_file",
                description = "删除文件或目录。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "目标路径", required = true),
                    ToolParameterSchema(name = "recursive", type = "boolean", description = "布尔值", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "file_exists",
                description = "检查文件或目录是否存在。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "目标路径", required = true)
                )
            ),
            ToolPrompt(
                name = "move_file",
                description = "移动或重命名文件或目录。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source", type = "string", description = "源路径", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "目标路径", required = true)
                )
            ),
            ToolPrompt(
                name = "copy_file",
                description = "复制文件或目录。支持Android和Linux之间的跨环境复制。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source", type = "string", description = "源路径", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "目标路径", required = true),
                    ToolParameterSchema(name = "recursive", type = "boolean", description = "布尔值", required = false, default = "false"),
                    ToolParameterSchema(name = "source_environment", type = "string", description = "可选，\"android\"或\"linux\"", required = false, default = "\"android\""),
                    ToolParameterSchema(name = "dest_environment", type = "string", description = "可选，\"android\"或\"linux\"。跨环境复制（如Android → Linux或Linux → Android）时，需指定source_environment和dest_environment", required = false, default = "\"android\"")
                )
            ),
            ToolPrompt(
                name = "make_directory",
                description = "创建目录。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "目录路径", required = true),
                    ToolParameterSchema(name = "create_parents", type = "boolean", description = "布尔值", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "find_files",
                description = "搜索匹配模式的文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "搜索路径，Android用/sdcard/...，Linux用/home/...或/etc/...", required = true),
                    ToolParameterSchema(name = "pattern", type = "string", description = "搜索模式，例如\"*.jpg\"", required = true),
                    ToolParameterSchema(name = "max_depth", type = "integer", description = "可选，控制子目录搜索深度，-1=无限", required = false),
                    ToolParameterSchema(name = "use_path_pattern", type = "boolean", description = "布尔值", required = false, default = "false"),
                    ToolParameterSchema(name = "case_insensitive", type = "boolean", description = "布尔值", required = false, default = "false")
                )
            ),
            ToolPrompt(
                name = "grep_code",
                description = "在文件中搜索匹配正则表达式的代码内容，返回带上下文的匹配结果。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "搜索路径", required = true),
                    ToolParameterSchema(name = "pattern", type = "string", description = "正则表达式模式", required = true),
                    ToolParameterSchema(name = "file_pattern", type = "string", description = "文件过滤", required = false, default = "\"*\""),
                    ToolParameterSchema(name = "case_insensitive", type = "boolean", description = "布尔值", required = false, default = "false"),
                    ToolParameterSchema(name = "context_lines", type = "integer", description = "匹配行前后的上下文行数", required = false, default = "3"),
                    ToolParameterSchema(name = "max_results", type = "integer", description = "最大匹配数", required = false, default = "100")
                )
            ),
            ToolPrompt(
                name = "grep_context",
                description = "基于意图/上下文理解搜索相关内容。支持两种模式：1) 目录模式：当path是目录时，找出最相关的文件。2) 文件模式：当path是文件时，找出该文件内最相关的代码段。使用语义相关性评分。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "目录或文件路径", required = true),
                    ToolParameterSchema(name = "intent", type = "string", description = "意图或上下文描述字符串", required = true),
                    ToolParameterSchema(name = "file_pattern", type = "string", description = "目录模式下的文件过滤", required = false, default = "\"*\""),
                    ToolParameterSchema(name = "max_results", type = "integer", description = "返回的最大项数", required = false, default = "10")
                )
            ),
            ToolPrompt(
                name = "file_info",
                description = "获取文件或目录的详细信息，包括类型、大小、权限、所有者、组和最后修改时间。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "目标路径", required = true)
                )
            ),
            ToolPrompt(
                name = "zip_files",
                description = "压缩文件或目录。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source", type = "string", description = "要压缩的路径", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "输出zip文件", required = true)
                )
            ),
            ToolPrompt(
                name = "unzip_files",
                description = "解压zip文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source", type = "string", description = "zip文件路径", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "解压路径", required = true)
                )
            ),
            ToolPrompt(
                name = "open_file",
                description = "使用系统默认应用程序打开文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "文件路径", required = true)
                )
            ),
            ToolPrompt(
                name = "share_file",
                description = "与其他应用程序共享文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "path", type = "string", description = "文件路径", required = true),
                    ToolParameterSchema(name = "title", type = "string", description = "可选的共享标题", required = false, default = "\"Share File\"")
                )
            ),
            ToolPrompt(
                name = "download_file",
                description = "从互联网下载文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "文件URL", required = true),
                    ToolParameterSchema(name = "destination", type = "string", description = "保存路径", required = true)
                )
            )
        )
    )
    
    // ==================== HTTP工具 ====================
    val httpTools = SystemToolPromptCategory(
        categoryName = "HTTP Tools",
        tools = listOf(
            ToolPrompt(
                name = "http_request",
                description = "Send HTTP request.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "url", required = true),
                    ToolParameterSchema(name = "method", type = "string", description = "GET/POST/PUT/DELETE", required = true),
                    ToolParameterSchema(name = "headers", type = "string", description = "headers", required = false),
                    ToolParameterSchema(name = "body", type = "string", description = "body", required = false),
                    ToolParameterSchema(name = "body_type", type = "string", description = "json/form/text/xml", required = false)
                )
            ),
            ToolPrompt(
                name = "multipart_request",
                description = "Upload files.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "url", required = true),
                    ToolParameterSchema(name = "method", type = "string", description = "POST/PUT", required = true),
                    ToolParameterSchema(name = "headers", type = "string", description = "headers", required = false),
                    ToolParameterSchema(name = "form_data", type = "string", description = "form_data", required = false),
                    ToolParameterSchema(name = "files", type = "string", description = "JSON array string. Each item is an object: {\"field_name\": string, \"file_path\": string, \"content_type\"?: string, \"file_name\"?: string}", required = false)
                )
            ),
            ToolPrompt(
                name = "manage_cookies",
                description = "Manage cookies.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "action", type = "string", description = "get/set/clear", required = true),
                    ToolParameterSchema(name = "domain", type = "string", description = "domain", required = false),
                    ToolParameterSchema(name = "cookies", type = "string", description = "cookies", required = false)
                )
            ),
            ToolPrompt(
                name = "visit_web",
                description = "Visit a webpage and extract its content. This tool can be used in two ways: 1. Provide a `url` to visit a new page. 2. Provide a `visit_key` from a previous search result and a `link_number` to visit a specific link from that search. This is the preferred way to follow up on a search.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "optional, webpage URL", required = false),
                    ToolParameterSchema(name = "visit_key", type = "string", description = "optional, string, the key from a previous search", required = false),
                    ToolParameterSchema(name = "link_number", type = "integer", description = "optional, int, the number of the link to visit from the search results", required = false)
                )
            )
        )
    )
    
    val httpToolsCn = SystemToolPromptCategory(
        categoryName = "HTTP工具",
        tools = listOf(
            ToolPrompt(
                name = "http_request",
                description = "发送HTTP请求。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "url", required = true),
                    ToolParameterSchema(name = "method", type = "string", description = "GET/POST/PUT/DELETE", required = true),
                    ToolParameterSchema(name = "headers", type = "string", description = "headers", required = false),
                    ToolParameterSchema(name = "body", type = "string", description = "body", required = false),
                    ToolParameterSchema(name = "body_type", type = "string", description = "json/form/text/xml", required = false)
                )
            ),
            ToolPrompt(
                name = "multipart_request",
                description = "上传文件。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "url", required = true),
                    ToolParameterSchema(name = "method", type = "string", description = "POST/PUT", required = true),
                    ToolParameterSchema(name = "headers", type = "string", description = "headers", required = false),
                    ToolParameterSchema(name = "form_data", type = "string", description = "form_data", required = false),
                    ToolParameterSchema(name = "files", type = "string", description = "JSON数组字符串。每个元素是对象: {\"field_name\": 字符串, \"file_path\": 字符串, 可选 \"content_type\": 字符串, 可选 \"file_name\": 字符串}", required = false)
                )
            ),
            ToolPrompt(
                name = "manage_cookies",
                description = "管理cookies。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "action", type = "string", description = "get/set/clear", required = true),
                    ToolParameterSchema(name = "domain", type = "string", description = "domain", required = false),
                    ToolParameterSchema(name = "cookies", type = "string", description = "cookies", required = false)
                )
            ),
            ToolPrompt(
                name = "visit_web",
                description = "访问网页并提取内容。此工具有两种用法：1. 提供 `url` 访问新页面。2. 提供先前搜索结果中的 `visit_key` 和 `link_number` 来访问该搜索中的特定链接。这是跟进搜索的首选方式。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "url", type = "string", description = "可选, 网页URL", required = false),
                    ToolParameterSchema(name = "visit_key", type = "string", description = "可选, 字符串, 上一次搜索返回的密钥", required = false),
                    ToolParameterSchema(name = "link_number", type = "integer", description = "可选, 整数, 要访问的搜索结果链接的编号", required = false)
                )
            )
        )
    )
    
    // ==================== 记忆库工具 ====================
    val memoryTools = SystemToolPromptCategory(
        categoryName = "Memory and Memory Library Tools",
        tools = listOf(
            ToolPrompt(
                name = "query_memory",
                description = "Searches the memory library for relevant memories using hybrid search (keyword matching + semantic understanding). Use this when you need to recall past knowledge, look up specific information, or require context. Keywords can be separated by '|' or spaces - each keyword will be independently matched semantically and the results will be combined with weighted scoring. You can use \"*\" as the query to return all memories (optionally filtered by folder_path). When the user attaches a memory folder, a `<memory_context>` will be provided in the prompt. You MUST use the `folder_path` parameter to restrict the search to that folder. **IMPORTANT**: For document nodes (uploaded files), this tool uses vector search to return ONLY the most relevant chunks matching your query, NOT the entire document. Results show \"Document: [name], Chunk X/Y: [content]\" format. To read the complete document or specific parts, use `get_memory_by_title` instead. **NOTE**: When limit > 20, results will only show titles and truncated content to save tokens.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "query", type = "string", description = "string, the keyword or question to search for, or \"*\" to return all memories", required = true),
                    ToolParameterSchema(name = "folder_path", type = "string", description = "optional, string, the specific folder path to search within", required = false),
                    ToolParameterSchema(name = "threshold", type = "number", description = "optional, float 0.0-1.0, semantic similarity threshold, lower values return more results", required = false, default = "0.25"),
                    ToolParameterSchema(name = "limit", type = "integer", description = "optional, int >= 1, maximum number of results to return. When > 20, only titles and truncated content are returned", required = false, default = "5")
                )
            ),
            ToolPrompt(
                name = "get_memory_by_title",
                description = "Retrieves a memory by exact title. For regular memories, returns full content. For document nodes (uploaded files), you can: 1) Read entire document (no parameters), 2) Read specific chunk(s) via `chunk_index` (e.g., \"3\") or `chunk_range` (e.g., \"3-7\"), 3) Search within document via `query`. Use this when query_memory returns partial results and you need more complete content.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "title", type = "string", description = "required, string, the exact title of the memory", required = true),
                    ToolParameterSchema(name = "chunk_index", type = "integer", description = "optional, int, read a specific chunk by its number, e.g., 3 for the 3rd chunk", required = false),
                    ToolParameterSchema(name = "chunk_range", type = "string", description = "optional, string, read a range of chunks in \"start-end\" format, e.g., \"3-7\" for chunks 3 through 7", required = false),
                    ToolParameterSchema(name = "query", type = "string", description = "optional, string, search for matching chunks within the document using keywords or semantic search", required = false)
                )
            ),
            ToolPrompt(
                name = "create_memory",
                description = "Creates a new memory node in the library. Use this when you want to save important information for future reference.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "title", type = "string", description = "required, string", required = true),
                    ToolParameterSchema(name = "content", type = "string", description = "required, string", required = true),
                    ToolParameterSchema(name = "content_type", type = "string", description = "optional", required = false, default = "\"text/plain\""),
                    ToolParameterSchema(name = "source", type = "string", description = "optional", required = false, default = "\"ai_created\""),
                    ToolParameterSchema(name = "folder_path", type = "string", description = "optional", required = false, default = "\"\"")
                )
            ),
            ToolPrompt(
                name = "update_memory",
                description = "Updates an existing memory node by title. Use this to modify an existing memory's content or metadata.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "old_title", type = "string", description = "required, string to identify the memory", required = true),
                    ToolParameterSchema(name = "new_title", type = "string", description = "optional, string, new title if renaming", required = false),
                    ToolParameterSchema(name = "content", type = "string", description = "optional, string", required = false),
                    ToolParameterSchema(name = "content_type", type = "string", description = "optional, string", required = false),
                    ToolParameterSchema(name = "source", type = "string", description = "optional, string", required = false),
                    ToolParameterSchema(name = "credibility", type = "number", description = "optional, float 0-1", required = false),
                    ToolParameterSchema(name = "importance", type = "number", description = "optional, float 0-1", required = false),
                    ToolParameterSchema(name = "folder_path", type = "string", description = "optional, string", required = false),
                    ToolParameterSchema(name = "tags", type = "string", description = "optional, comma-separated string", required = false)
                )
            ),
            ToolPrompt(
                name = "delete_memory",
                description = "Deletes a memory node from the library by title. Use with caution as this operation is irreversible.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "title", type = "string", description = "required, string to identify the memory", required = true)
                )
            ),
            ToolPrompt(
                name = "link_memories",
                description = "Creates a semantic link between two memories in the library. Use this to establish relationships between related concepts, facts, or pieces of information. This helps build a knowledge graph structure for better memory retrieval and understanding.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source_title", type = "string", description = "required, string, the title of the source memory", required = true),
                    ToolParameterSchema(name = "target_title", type = "string", description = "required, string, the title of the target memory", required = true),
                    ToolParameterSchema(name = "link_type", type = "string", description = "optional, string, the type of relationship such as \"related\", \"causes\", \"explains\", \"part_of\", \"contradicts\", etc.", required = false, default = "\"related\""),
                    ToolParameterSchema(name = "weight", type = "number", description = "optional, float 0.0-1.0, the strength of the link with 1.0 being strongest", required = false, default = "0.7"),
                    ToolParameterSchema(name = "description", type = "string", description = "optional, string, additional context about the relationship", required = false, default = "\"\"")
                )
            ),
            ToolPrompt(
                name = "update_user_preferences",
                description = "Updates user preference information directly. Use this when you learn new information about the user that should be remembered (e.g., their birthday, gender, personality traits, identity, occupation, or preferred AI interaction style). This allows immediate updates without waiting for the automatic system.",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "birth_date", type = "integer", description = "optional, Unix timestamp in milliseconds", required = false),
                    ToolParameterSchema(name = "gender", type = "string", description = "optional, string", required = false),
                    ToolParameterSchema(name = "personality", type = "string", description = "optional, string describing personality traits", required = false),
                    ToolParameterSchema(name = "identity", type = "string", description = "optional, string describing identity/role", required = false),
                    ToolParameterSchema(name = "occupation", type = "string", description = "optional, string", required = false),
                    ToolParameterSchema(name = "ai_style", type = "string", description = "optional, string describing preferred AI interaction style. At least one parameter must be provided", required = false)
                )
            )
        ),
        categoryFooter = "\nNote: The memory library and user personality profile are automatically updated by a separate system after you output the task completion marker. However, if you need to manage memories immediately or update user preferences, use the appropriate tools directly."
    )
    
    val memoryToolsCn = SystemToolPromptCategory(
        categoryName = "记忆与记忆库工具",
        tools = listOf(
            ToolPrompt(
                name = "query_memory",
                description = "使用混合搜索（关键词匹配 + 语义理解）从记忆库中搜索相关记忆。当需要回忆过去的知识、查找特定信息或需要上下文时使用。关键词可以使用\"|\"或空格分隔 - 每个关键词都会独立进行语义匹配，结果将通过加权评分合并。可以使用 \"*\" 作为查询来返回所有记忆（可通过 folder_path 过滤）。当用户附加记忆文件夹时，提示中会提供`<memory_context>`。你必须使用 `folder_path` 参数将搜索限制在该文件夹内。**重要**：对于文档节点（上传的文件），此工具使用向量搜索只返回与查询最相关的分块，而不是整个文档。结果显示\"Document: [文档名], Chunk X/Y: [内容]\"格式。如需阅读完整文档或特定部分，请改用 `get_memory_by_title` 工具。**注意**：当 limit > 20 时，结果将只显示标题和截断内容以节省令牌。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "query", type = "string", description = "string, 搜索的关键词或问题, 或使用 \"*\" 返回所有记忆", required = true),
                    ToolParameterSchema(name = "folder_path", type = "string", description = "可选, string, 要搜索的特定文件夹路径", required = false),
                    ToolParameterSchema(name = "threshold", type = "number", description = "可选, float 0.0-1.0, 语义相似度阈值, 较低的值返回更多结果", required = false, default = "0.25"),
                    ToolParameterSchema(name = "limit", type = "integer", description = "可选, int >= 1, 返回结果的最大数量. 当 > 20 时，只返回标题和截断内容", required = false, default = "5")
                )
            ),
            ToolPrompt(
                name = "get_memory_by_title",
                description = "通过精确标题检索记忆。对于普通记忆，返回完整内容。对于文档节点（上传的文件），可以：1) 读取整个文档（不提供参数），2) 通过 `chunk_index`（如\"3\"）或 `chunk_range`（如\"3-7\"）读取特定分块，3) 通过 `query` 在文档内搜索。当 query_memory 返回部分结果而你需要更完整内容时使用。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "title", type = "string", description = "必需, 字符串, 记忆的精确标题", required = true),
                    ToolParameterSchema(name = "chunk_index", type = "integer", description = "可选, 整数, 读取特定编号的分块, 例如3表示第3块", required = false),
                    ToolParameterSchema(name = "chunk_range", type = "string", description = "可选, 字符串, 读取分块范围，格式为\"起始-结束\"，例如\"3-7\"表示第3到第7块", required = false),
                    ToolParameterSchema(name = "query", type = "string", description = "可选, 字符串, 使用关键词或语义搜索在文档内查找匹配的分块", required = false)
                )
            ),
            ToolPrompt(
                name = "create_memory",
                description = "在记忆库中创建新的记忆节点。当你想保存重要信息供将来参考时使用。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "title", type = "string", description = "必需, 字符串", required = true),
                    ToolParameterSchema(name = "content", type = "string", description = "必需, 字符串", required = true),
                    ToolParameterSchema(name = "content_type", type = "string", description = "可选", required = false, default = "\"text/plain\""),
                    ToolParameterSchema(name = "source", type = "string", description = "可选", required = false, default = "\"ai_created\""),
                    ToolParameterSchema(name = "folder_path", type = "string", description = "可选", required = false, default = "\"\"")
                )
            ),
            ToolPrompt(
                name = "update_memory",
                description = "通过标题更新现有的记忆节点。用于修改现有记忆的内容或元数据。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "old_title", type = "string", description = "必需, 字符串，用于识别记忆", required = true),
                    ToolParameterSchema(name = "new_title", type = "string", description = "可选, 字符串, 重命名时的新标题", required = false),
                    ToolParameterSchema(name = "content", type = "string", description = "可选, 字符串", required = false),
                    ToolParameterSchema(name = "content_type", type = "string", description = "可选, 字符串", required = false),
                    ToolParameterSchema(name = "source", type = "string", description = "可选, 字符串", required = false),
                    ToolParameterSchema(name = "credibility", type = "number", description = "可选, 浮点数 0-1", required = false),
                    ToolParameterSchema(name = "importance", type = "number", description = "可选, 浮点数 0-1", required = false),
                    ToolParameterSchema(name = "folder_path", type = "string", description = "可选, 字符串", required = false),
                    ToolParameterSchema(name = "tags", type = "string", description = "可选, 逗号分隔的字符串", required = false)
                )
            ),
            ToolPrompt(
                name = "delete_memory",
                description = "通过标题从记忆库中删除记忆节点。谨慎使用，此操作不可逆。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "title", type = "string", description = "必需, 字符串，用于识别记忆", required = true)
                )
            ),
            ToolPrompt(
                name = "link_memories",
                description = "在记忆库中的两个记忆之间创建语义链接。用于建立相关概念、事实或信息片段之间的关系。这有助于构建知识图谱结构，以便更好地检索和理解记忆。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "source_title", type = "string", description = "必需, 字符串, 源记忆的标题", required = true),
                    ToolParameterSchema(name = "target_title", type = "string", description = "必需, 字符串, 目标记忆的标题", required = true),
                    ToolParameterSchema(name = "link_type", type = "string", description = "可选, 字符串, 关系类型，如\"related\"（相关）、\"causes\"（导致）、\"explains\"（解释）、\"part_of\"（部分）、\"contradicts\"（矛盾）等", required = false, default = "\"related\""),
                    ToolParameterSchema(name = "weight", type = "number", description = "可选, 浮点数 0.0-1.0, 链接强度，1.0表示最强", required = false, default = "0.7"),
                    ToolParameterSchema(name = "description", type = "string", description = "可选, 字符串, 关于关系的额外上下文", required = false, default = "\"\"")
                )
            ),
            ToolPrompt(
                name = "update_user_preferences",
                description = "直接更新用户偏好信息。当你了解到用户的新信息时使用（例如生日、性别、性格特征、身份、职业或首选AI交互风格）。这允许立即更新而无需等待自动系统。",
                parametersStructured = listOf(
                    ToolParameterSchema(name = "birth_date", type = "integer", description = "可选, Unix时间戳，毫秒", required = false),
                    ToolParameterSchema(name = "gender", type = "string", description = "可选, 字符串", required = false),
                    ToolParameterSchema(name = "personality", type = "string", description = "可选, 描述性格特征的字符串", required = false),
                    ToolParameterSchema(name = "identity", type = "string", description = "可选, 描述身份/角色的字符串", required = false),
                    ToolParameterSchema(name = "occupation", type = "string", description = "可选, 字符串", required = false),
                    ToolParameterSchema(name = "ai_style", type = "string", description = "可选, 描述首选AI交互风格的字符串. 必须提供至少一个参数", required = false)
                )
            )
        ),
        categoryFooter = "\n注意：记忆库和用户性格档案会在你输出任务完成标志后由独立的系统自动更新。但是，如果需要立即管理记忆或更新用户偏好，请直接使用相应的工具。"
    )
    
    /**
     * 获取所有英文工具分类
     * @param hasBackendImageRecognition 是否配置了后端识图服务（IMAGE_RECOGNITION功能）
     * @param chatModelHasDirectImage 当前聊天模型是否自带识图能力（可直接看图片）
     */
    fun getAllCategoriesEn(
        hasBackendImageRecognition: Boolean = false,
        chatModelHasDirectImage: Boolean = false,
        hasBackendAudioRecognition: Boolean = false,
        hasBackendVideoRecognition: Boolean = false,
        chatModelHasDirectAudio: Boolean = false,
        chatModelHasDirectVideo: Boolean = false
    ): List<SystemToolPromptCategory> {
        val shouldExposeIntent =
            (hasBackendImageRecognition && !chatModelHasDirectImage) ||
                (hasBackendAudioRecognition && !chatModelHasDirectAudio) ||
                (hasBackendVideoRecognition && !chatModelHasDirectVideo)

        val adjustedFileSystemTools = fileSystemTools.copy(
            tools = fileSystemTools.tools.map { tool ->
                if (tool.name != "read_file") return@map tool

                val filteredParams = (tool.parametersStructured ?: emptyList()).filter { param ->
                    when (param.name) {
                        "direct_image" -> false
                        "direct_audio" -> false
                        "direct_video" -> false
                        "intent" -> shouldExposeIntent
                        else -> true
                    }
                }

                val adjustedDescription =
                    if (shouldExposeIntent) {
                        "Read the content of a file. For media files, you can also provide an 'intent' parameter to use a backend recognition model for analysis."
                    } else {
                        tool.description
                    }

                tool.copy(
                    description = adjustedDescription,
                    parametersStructured = filteredParams
                )
            }
        )

        return listOf(
            basicTools,
            adjustedFileSystemTools,
            httpTools,
            memoryTools
        )
    }
    
    /**
     * 获取所有中文工具分类
     * @param hasBackendImageRecognition 是否配置了后端识图服务（IMAGE_RECOGNITION功能）
     * @param chatModelHasDirectImage 当前聊天模型是否自带识图能力（可直接看图片）
     */
    fun getAllCategoriesCn(
        hasBackendImageRecognition: Boolean = false,
        chatModelHasDirectImage: Boolean = false,
        hasBackendAudioRecognition: Boolean = false,
        hasBackendVideoRecognition: Boolean = false,
        chatModelHasDirectAudio: Boolean = false,
        chatModelHasDirectVideo: Boolean = false
    ): List<SystemToolPromptCategory> {
        val shouldExposeIntent =
            (hasBackendImageRecognition && !chatModelHasDirectImage) ||
                (hasBackendAudioRecognition && !chatModelHasDirectAudio) ||
                (hasBackendVideoRecognition && !chatModelHasDirectVideo)

        val adjustedFileSystemTools = fileSystemToolsCn.copy(
            tools = fileSystemToolsCn.tools.map { tool ->
                if (tool.name != "read_file") return@map tool

                val filteredParams = (tool.parametersStructured ?: emptyList()).filter { param ->
                    when (param.name) {
                        "direct_image" -> false
                        "direct_audio" -> false
                        "direct_video" -> false
                        "intent" -> shouldExposeIntent
                        else -> true
                    }
                }

                val adjustedDescription =
                    if (shouldExposeIntent) {
                        "读取文件内容。对于媒体文件，你也可以提供 intent 参数，使用后端识别模型进行分析。"
                    } else {
                        tool.description
                    }

                tool.copy(
                    description = adjustedDescription,
                    parametersStructured = filteredParams
                )
            }
        )

        return listOf(
            basicToolsCn,
            adjustedFileSystemTools,
            httpToolsCn,
            memoryToolsCn
        )
    }
    
    /**
     * 生成完整的工具提示词文本（英文）
     */
    fun generateToolsPromptEn(
        hasBackendImageRecognition: Boolean = false,
        includeMemoryTools: Boolean = true,
        chatModelHasDirectImage: Boolean = false,
        hasBackendAudioRecognition: Boolean = false,
        hasBackendVideoRecognition: Boolean = false,
        chatModelHasDirectAudio: Boolean = false,
        chatModelHasDirectVideo: Boolean = false
    ): String {
        val categories = if (includeMemoryTools) {
            getAllCategoriesEn(
                hasBackendImageRecognition = hasBackendImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasBackendAudioRecognition = hasBackendAudioRecognition,
                hasBackendVideoRecognition = hasBackendVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo
            )
        } else {
            getAllCategoriesEn(
                hasBackendImageRecognition = hasBackendImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasBackendAudioRecognition = hasBackendAudioRecognition,
                hasBackendVideoRecognition = hasBackendVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo
            )
                .filter { it.categoryName != "Memory and Memory Library Tools" }
        }

        return categories.joinToString("\n\n") { it.toString() }
    }
    
    /**
     * 生成完整的工具提示词文本（中文）
     */
    fun generateToolsPromptCn(
        hasBackendImageRecognition: Boolean = false,
        includeMemoryTools: Boolean = true,
        chatModelHasDirectImage: Boolean = false,
        hasBackendAudioRecognition: Boolean = false,
        hasBackendVideoRecognition: Boolean = false,
        chatModelHasDirectAudio: Boolean = false,
        chatModelHasDirectVideo: Boolean = false
    ): String {
        val categories = if (includeMemoryTools) {
            getAllCategoriesCn(
                hasBackendImageRecognition = hasBackendImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasBackendAudioRecognition = hasBackendAudioRecognition,
                hasBackendVideoRecognition = hasBackendVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo
            )
        } else {
            getAllCategoriesCn(
                hasBackendImageRecognition = hasBackendImageRecognition,
                chatModelHasDirectImage = chatModelHasDirectImage,
                hasBackendAudioRecognition = hasBackendAudioRecognition,
                hasBackendVideoRecognition = hasBackendVideoRecognition,
                chatModelHasDirectAudio = chatModelHasDirectAudio,
                chatModelHasDirectVideo = chatModelHasDirectVideo
            )
                .filter { it.categoryName != "记忆与记忆库工具" }
        }

        return categories.joinToString("\n\n") { it.toString() }
    }
}

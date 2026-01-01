package com.ai.assistance.operit.ui.features.chat.webview

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.IOException

fun createAndGetDefaultWorkspace(context: Context, chatId: String): File {
    return createAndGetDefaultWorkspace(context, chatId, null)
}

fun createAndGetDefaultWorkspace(context: Context, chatId: String, projectType: String?): File {
    // 创建内部存储工作区
    val workspacePath = getWorkspacePath(context, chatId)
    ensureWorkspaceDirExists(workspacePath)

    val webContentDir = File(workspacePath)

    // 根据项目类型复制模板文件并创建配置
    when (projectType) {
        "node" -> {
            copyTemplateFiles(context, webContentDir, "node")
            createProjectConfigIfNeeded(webContentDir, ProjectType.NODE)
        }
        "typescript" -> {
            copyTemplateFiles(context, webContentDir, "typescript")
            createProjectConfigIfNeeded(webContentDir, ProjectType.TYPESCRIPT)
        }
        "python" -> {
            copyTemplateFiles(context, webContentDir, "python")
            createProjectConfigIfNeeded(webContentDir, ProjectType.PYTHON)
        }
        "java" -> {
            copyTemplateFiles(context, webContentDir, "java")
            createProjectConfigIfNeeded(webContentDir, ProjectType.JAVA)
        }
        "go" -> {
            copyTemplateFiles(context, webContentDir, "go")
            createProjectConfigIfNeeded(webContentDir, ProjectType.GO)
        }
        "office" -> {
            copyTemplateFiles(context, webContentDir, "office")
            createProjectConfigIfNeeded(webContentDir, ProjectType.OFFICE)
        }
        "blank" -> {
            createProjectConfigIfNeeded(webContentDir, ProjectType.BLANK)
        }
        else -> {
            copyTemplateFiles(context, webContentDir, "web")
            createProjectConfigIfNeeded(webContentDir, ProjectType.WEB)
        }
    }

    return webContentDir
}

/**
 * 获取工作区路径（新位置：内部存储）
 * 路径: /data/data/com.ai.assistance.operit/files/workspace/{chatId}
 */
fun getWorkspacePath(context: Context, chatId: String): String {
    return File(context.filesDir, "workspace/$chatId").absolutePath
}

/**
 * 获取旧的工作区路径（外部存储）
 * 路径: /sdcard/Download/Operit/workspace/{chatId}
 */
fun getLegacyWorkspacePath(chatId: String): String {
    val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    return "$downloadDir/Operit/workspace/$chatId"
}

fun ensureWorkspaceDirExists(path: String): File {
    val workspaceDir = File(path)
    if (!workspaceDir.exists()) {
        workspaceDir.mkdirs()
    }
    return workspaceDir
}

private enum class ProjectType {
    WEB, NODE, TYPESCRIPT, PYTHON, JAVA, GO, OFFICE, BLANK
}

private const val DEFAULT_BLANK_PROJECT_CONFIG_JSON = """
{
    "projectType": "blank",
    "title": "空白工作区",
    "description": "这是一个空白工作区，只包含基础目录结构。你可以编辑 .operit/config.json 来配置项目类型、服务器和命令，例如：server.enabled、preview.type、commands 等。",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false,
        "previewButtonLabel": ""
    },
    "commands": [],
    "export": {
        "enabled": false
    }
}
"""

private const val DEFAULT_WEB_PROJECT_CONFIG_JSON = """
{
    "projectType": "web",
    "title": "Web 项目",
    "description": "HTML/CSS/JavaScript 网页开发，本地服务器已启用",
    "server": {
        "enabled": true,
        "port": 8093,
        "autoStart": true
    },
    "preview": {
        "type": "browser",
        "url": "http://localhost:8093"
    },
    "commands": [],
    "export": {
        "enabled": true
    }
}
"""

private const val DEFAULT_NODE_PROJECT_CONFIG_JSON = """
{
    "projectType": "node",
    "title": "Node.js 项目",
    "description": "使用 npm 管理依赖，适用于后端开发和构建工具",
    "server": {
        "enabled": false,
        "port": 3000,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "http://localhost:3000",
        "showPreviewButton": true,
        "previewButtonLabel": "浏览器预览"
    },
    "commands": [
        {
            "id": "npm_init",
            "label": "npm init -y",
            "command": "npm init -y",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "npm_install",
            "label": "npm install",
            "command": "npm install",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "npm_start",
            "label": "npm start",
            "command": "npm start",
            "workingDir": ".",
            "shell": true,
            "usesDedicatedSession": true,
            "sessionTitle": "npm start"
        },
        {
            "id": "npm_test",
            "label": "npm test",
            "command": "npm test",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
"""

private const val DEFAULT_TYPESCRIPT_PROJECT_CONFIG_JSON = """
{
    "projectType": "typescript",
    "title": "TypeScript 项目",
    "description": "使用 pnpm 和 TypeScript，提供类型安全和实时编译",
    "server": {
        "enabled": false,
        "port": 3000,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false
    },
    "commands": [
        {
            "id": "pnpm_install",
            "label": "pnpm install",
            "command": "pnpm install",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "pnpm_build",
            "label": "pnpm build",
            "command": "pnpm build",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "tsc_watch",
            "label": "tsc watch",
            "command": "pnpm exec tsc --watch",
            "workingDir": ".",
            "shell": true,
            "usesDedicatedSession": true,
            "sessionTitle": "TypeScript Watch"
        },
        {
            "id": "pnpm_start",
            "label": "pnpm start",
            "command": "pnpm start",
            "workingDir": ".",
            "shell": true,
            "usesDedicatedSession": true,
            "sessionTitle": "pnpm start"
        },
        {
            "id": "pnpm_list",
            "label": "pnpm list",
            "command": "pnpm list",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
"""

private const val DEFAULT_PYTHON_PROJECT_CONFIG_JSON = """
{
    "projectType": "python",
    "title": "Python 项目",
    "description": "支持虚拟环境和 pip 包管理，适用于数据分析和开发",
    "server": {
        "enabled": false,
        "port": 8000,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false
    },
    "commands": [
        {
            "id": "venv_create",
            "label": "创建虚拟环境",
            "command": "python -m venv venv",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "venv_activate",
            "label": "激活虚拟环境",
            "command": "source venv/bin/activate || venv\\Scripts\\activate",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "pip_install",
            "label": "安装依赖",
            "command": "pip install -r requirements.txt",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "pip_list",
            "label": "查看已安装包",
            "command": "pip list",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "python_run",
            "label": "运行 main.py",
            "command": "python main.py",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
"""

private const val DEFAULT_JAVA_PROJECT_CONFIG_JSON = """
{
    "projectType": "java",
    "title": "Java 项目",
    "description": "标准 Gradle 项目结构，支持构建、测试和打包",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false
    },
    "commands": [
        {
            "id": "gradle_init",
            "label": "初始化 Gradle Wrapper",
            "command": "gradle wrapper --gradle-version 8.5",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_build",
            "label": "构建项目",
            "command": "./gradlew build || gradle build",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_run",
            "label": "运行程序",
            "command": "./gradlew run || gradle run",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_test",
            "label": "运行测试",
            "command": "./gradlew test || gradle test",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_jar",
            "label": "打包 JAR",
            "command": "./gradlew jar || gradle jar",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_clean",
            "label": "清理构建",
            "command": "./gradlew clean || gradle clean",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "gradle_tasks",
            "label": "查看所有任务",
            "command": "./gradlew tasks || gradle tasks",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
"""

private const val DEFAULT_GO_PROJECT_CONFIG_JSON = """
{
    "projectType": "go",
    "title": "Go 项目",
    "description": "高性能并发编程，使用 Go Modules 管理依赖",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false
    },
    "commands": [
        {
            "id": "go_mod_init",
            "label": "go mod init",
            "command": "go mod init myapp",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "go_mod_tidy",
            "label": "go mod tidy",
            "command": "go mod tidy",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "go_run",
            "label": "go run main.go",
            "command": "go run main.go",
            "workingDir": ".",
            "shell": true
        },
        {
            "id": "go_build",
            "label": "go build",
            "command": "go build",
            "workingDir": ".",
            "shell": true
        }
    ],
    "export": {
        "enabled": false
    }
}
"""

private const val DEFAULT_OFFICE_PROJECT_CONFIG_JSON = """
{
    "projectType": "office",
    "title": "办公文档",
    "description": "用于文档编辑、文件处理和通用办公任务",
    "server": {
        "enabled": false,
        "port": 8080,
        "autoStart": false
    },
    "preview": {
        "type": "terminal",
        "url": "",
        "showPreviewButton": false,
        "previewButtonLabel": ""
    },
    "commands": [],
    "export": {
        "enabled": false
    }
}
"""

/**
 * 从 assets 复制项目模板文件到工作区
 */
private fun copyTemplateFiles(context: Context, workspaceDir: File, templateName: String) {
    val assetManager = context.assets
    val templatePath = "templates/$templateName"
    
    try {
        val files = assetManager.list(templatePath) ?: return
        
        for (filename in files) {
            val sourcePath = "$templatePath/$filename"
            // 特殊处理：gitignore (无点) -> .gitignore (有点)
            // 因为 Android 构建工具会排除 assets 中的 .gitignore 文件
            val destFileName = if (filename == "gitignore") ".gitignore" else filename
            val destFile = File(workspaceDir, destFileName)
            
            // 检查是否是目录
            val isDirectory = try {
                assetManager.list(sourcePath)?.isNotEmpty() == true
            } catch (e: IOException) {
                false
            }
            
            if (isDirectory) {
                // 递归复制子目录
                destFile.mkdirs()
                copyTemplateFilesRecursive(assetManager, sourcePath, destFile)
            } else {
                // 复制文件
                assetManager.open(sourcePath).use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

/**
 * 递归复制模板文件
 */
private fun copyTemplateFilesRecursive(assetManager: android.content.res.AssetManager, sourcePath: String, destDir: File) {
    try {
        val files = assetManager.list(sourcePath) ?: return
        
        for (filename in files) {
            val currentSourcePath = "$sourcePath/$filename"
            val destFile = File(destDir, filename)
            
            val isDirectory = try {
                assetManager.list(currentSourcePath)?.isNotEmpty() == true
            } catch (e: IOException) {
                false
            }
            
            if (isDirectory) {
                destFile.mkdirs()
                copyTemplateFilesRecursive(assetManager, currentSourcePath, destFile)
            } else {
                assetManager.open(currentSourcePath).use { inputStream ->
                    destFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            }
        }
    } catch (e: IOException) {
        e.printStackTrace()
    }
}

private fun createProjectConfigIfNeeded(workspaceDir: File, projectType: ProjectType) {
    // 创建 .operit 目录和 config.json
    val operitDir = File(workspaceDir, ".operit")
    if (!operitDir.exists()) {
        operitDir.mkdirs()
    }

    val configFile = File(operitDir, "config.json")
    if (!configFile.exists()) {
        val configContent = when (projectType) {
            ProjectType.WEB -> DEFAULT_WEB_PROJECT_CONFIG_JSON
            ProjectType.NODE -> DEFAULT_NODE_PROJECT_CONFIG_JSON
            ProjectType.TYPESCRIPT -> DEFAULT_TYPESCRIPT_PROJECT_CONFIG_JSON
            ProjectType.PYTHON -> DEFAULT_PYTHON_PROJECT_CONFIG_JSON
            ProjectType.JAVA -> DEFAULT_JAVA_PROJECT_CONFIG_JSON
            ProjectType.GO -> DEFAULT_GO_PROJECT_CONFIG_JSON
            ProjectType.OFFICE -> DEFAULT_OFFICE_PROJECT_CONFIG_JSON
            ProjectType.BLANK -> DEFAULT_BLANK_PROJECT_CONFIG_JSON
        }

        try {
            configFile.writeText(configContent.trimIndent())
        } catch (_: IOException) {
            // Ignore write errors for now
        }
    }
}
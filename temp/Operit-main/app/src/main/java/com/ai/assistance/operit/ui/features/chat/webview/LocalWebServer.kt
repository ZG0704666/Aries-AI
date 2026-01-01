package com.ai.assistance.operit.ui.features.chat.webview

import android.content.Context
import android.os.Environment
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.DirectoryListingData
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolParameter
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FileApiEntry(val name: String, val isDirectory: Boolean)

/** LocalWebServer - 基于NanoHTTPD的本地Web服务器 用于显示工作空间目录中的文件 */
class LocalWebServer
private constructor(
    private val context: Context,
    private val port: Int,
    private var rootPath: String,
    private val type: ServerType
) : NanoHTTPD(port) {

    enum class ServerType {
        WORKSPACE,
        COMPUTER
    }

    companion object {
        private const val TAG = "LocalWebServer"

        // Port constants
        const val WORKSPACE_PORT = 8093
        const val COMPUTER_PORT = 8094

        @Volatile
        private var instances = mutableMapOf<ServerType, LocalWebServer>()

        @Synchronized
        fun getInstance(context: Context, type: ServerType): LocalWebServer {
            return instances.getOrPut(type) {
                val server: LocalWebServer = when (type) {
                    ServerType.WORKSPACE -> {
                        val workspaceRoot = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "Operit/workspace"
                        )
                        LocalWebServer(
                            context.applicationContext,
                            WORKSPACE_PORT,
                            workspaceRoot.absolutePath,
                            ServerType.WORKSPACE
                        )
                    }

                    ServerType.COMPUTER -> {
                        val computerRoot = getComputerRootPath()
                        // Asset copying logic is now in start() to ensure overwrite on each launch
                        LocalWebServer(
                            context.applicationContext,
                            COMPUTER_PORT,
                            computerRoot.absolutePath,
                            ServerType.COMPUTER
                        )
                    }
                }
                server
            }
        }

        private fun getComputerRootPath(): File {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return File(downloadDir, "Operit/computer")
        }

        private fun copyAssetsToDirectory(context: Context, assetDir: String, destDir: File) {
            // Always overwrite. First, delete existing files if the directory exists.
            if (destDir.exists()) {
                destDir.deleteRecursively()
                AppLogger.d("LocalWebServer", "Cleared existing computer directory for refresh: ${destDir.absolutePath}")
            }

            if (!destDir.mkdirs()) {
                AppLogger.e("LocalWebServer", "Failed to create destination directory: ${destDir.absolutePath}")
                return
            }

            val assetManager = context.assets
            try {
                val assets = assetManager.list(assetDir)
                if (assets == null || assets.isEmpty()) {
                    AppLogger.w("LocalWebServer", "No assets found in directory: $assetDir")
                    return
                }
                for (asset in assets) {
                    val sourcePath = "$assetDir/$asset"
                    val destPath = File(destDir, asset)
                    // Check if it's a directory by trying to list its contents
                    val isDir = try {
                        assetManager.list(sourcePath)?.isNotEmpty() == true
                    } catch (e: IOException) {
                        false
                    }

                    if (isDir) {
                        // It's a directory, recurse
                        copyAssetsToDirectory(context, sourcePath, destPath)
                    } else {
                        // It's a file, copy it
                        assetManager.open(sourcePath).use { inputStream ->
                            FileOutputStream(destPath).use { outputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                AppLogger.e("LocalWebServer", "Failed to copy assets from '$assetDir'", e)
            }
        }
    }

    private val isServerRunning = AtomicBoolean(false)

    @Throws(IOException::class)
    override fun start() {
        // 检查服务器是否已经在运行，避免重复启动
        if (isServerRunning.get()) {
            AppLogger.d(TAG, "服务器已在端口 $port 上运行，跳过启动")
            return
        }
        
        if (type == ServerType.COMPUTER) {
            val computerRoot = getComputerRootPath()
            AppLogger.d(TAG, "确保AI电脑资源已是最新，路径: ${computerRoot.absolutePath}")
            copyAssetsToDirectory(context, "computer_desktop", computerRoot)
        }
        super.start(SOCKET_READ_TIMEOUT, false)
        AppLogger.d(TAG, "本地Web服务器已在端口 $port 上启动, 根目录: $rootPath")
        isServerRunning.set(true)
    }

    override fun stop() {
        super.stop()
        isServerRunning.set(false)
        AppLogger.d(TAG, "Local server stopped at port: $port")
    }

    fun updateChatWorkspace(newWorkspacePath: String) {
        // This is now specific to the workspace server.
        // A better approach would be to create a new instance if the path changes fundamentally,
        // but for now, we'll just update the path for the WORKSPACE instance.
        this.rootPath = newWorkspacePath
        ensureWorkspaceDirExists(newWorkspacePath)
        AppLogger.d(TAG, "Workspace path updated to: $rootPath")
    }

    fun isRunning(): Boolean {
        return isServerRunning.get()
    }

    override fun serve(session: IHTTPSession): Response {
        AppLogger.d(TAG, "Request received: ${session.uri} at port $port")

        // API route for file listing
        if (session.uri.startsWith("/api/")) {
            return handleApiRequest(session)
        }

        // Serve static files from rootPath
        val uri = if (session.uri == "/") "/index.html" else session.uri
        val file = File(rootPath, uri)

        if (!file.exists() || !isInRoot(file)) {
            AppLogger.w(TAG, "File not found or access denied: ${file.absolutePath}")
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                MIME_PLAINTEXT,
                "File not found"
            ).addCorsHeaders()
        }

        val mimeType = getCustomMimeType(uri)
        return try {
            val fstream = FileInputStream(file)
            // Read the file into a byte array to serve it directly.
            // This avoids the GZIP streaming issue with WebView that causes "Broken pipe".
            val bytes = fstream.readBytes()
            fstream.close()

            if (mimeType == "text/html") {
                val htmlContent = String(bytes, Charsets.UTF_8)
                val injectedHtml = injectErudaIntoHtml(htmlContent)
                return newFixedLengthResponse(Response.Status.OK, mimeType, injectedHtml).addCorsHeaders()
            }

            // Serve the file from a byte array input stream. This is the robust way to avoid GZIP issues.
            val inputStream = ByteArrayInputStream(bytes)
            val response = newFixedLengthResponse(Response.Status.OK, mimeType, inputStream, bytes.size.toLong())
            response.addCorsHeaders()
            response
        } catch (ioe: IOException) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Could not read file."
            ).addCorsHeaders()
        }
    }

    private fun injectErudaIntoHtml(htmlContent: String): String {
        val erudaScript =
                """
        <script>
        (function() {
            if (window.erudaInjected) { return; }
            window.erudaInjected = true;
            localStorage.removeItem('eruda-entry-btn');
            var script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/eruda';
            document.body.appendChild(script);
            script.onload = function() {
                if (!window.eruda) return;
                eruda.init();
                var entryBtn = eruda.get('entry');
                if (entryBtn) {
                    entryBtn.position({
                        x: 10,
                        y: window.innerHeight - 70
                    });
                }
            }
        })();
        </script>
        """
        return htmlContent.replace("</body>", "$erudaScript</body>", ignoreCase = true)
    }

    private fun isInRoot(file: File): Boolean {
        return try {
            val rootDir = File(rootPath)
            file.canonicalPath.startsWith(rootDir.canonicalPath)
        } catch (e: IOException) {
            AppLogger.e(TAG, "Error checking file path: ${e.message}")
            false
        }
    }

    private fun handleApiRequest(session: IHTTPSession): Response {
        val uri = session.uri
        return when {
            uri.startsWith("/api/files") -> {
                val path = session.parameters["path"]?.get(0) ?: ""
                listDirectory(path)
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "API endpoint not found").addCorsHeaders()
            }
        }
    }

    private fun listDirectory(relativePath: String): Response {
        try {
            val toolHandler = AIToolHandler.getInstance(context)
            
            // Security check: ensure the path is within our root directory
            val requestedDir = File(rootPath, relativePath).canonicalFile
            if (!requestedDir.path.startsWith(File(rootPath).canonicalPath)) {
                 return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Access denied").addCorsHeaders()
            }

            val tool = AITool(
                name = "list_files",
                parameters = listOf(ToolParameter("path", requestedDir.absolutePath))
            )

            val result = toolHandler.executeTool(tool)

            if (result.success && result.result is DirectoryListingData) {
                // The result from list_files is already a JSON string of a list of file info.
                val directoryListing = result.result as DirectoryListingData
                val apiEntries = directoryListing.entries.map { FileApiEntry(it.name, it.isDirectory) }
                val jsonResult = Json.encodeToString(apiEntries)
                return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResult).addCorsHeaders()
            } else {
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, result.error ?: "Failed to list files").addCorsHeaders()
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Error listing directory", e)
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Error: ${e.message}").addCorsHeaders()
        }
    }

    private fun Response.addCorsHeaders(): Response {
        this.addHeader("Access-Control-Allow-Origin", "*")
        this.addHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD")
        this.addHeader("Access-Control-Allow-Headers", "X-Requested-With, Content-Type, Authorization, Origin, Accept")
        this.addHeader("Access-Control-Max-Age", "3600")
        this.addHeader("Access-Control-Allow-Credentials", "true")
        return this
    }
    
    /**
     * 确保工作区目录存在
     */
    private fun ensureWorkspaceDirExists(path: String) {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
            AppLogger.d(TAG, "创建工作区目录: $path")
        }
    }
    
    /**
     * 根据文件路径获取MIME类型
     */
    private fun getCustomMimeType(uri: String): String {
        val extension = uri.substringAfterLast('.', "")
        return when (extension.lowercase()) {
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "json" -> "application/json"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            "txt" -> "text/plain"
            "xml" -> "application/xml"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}

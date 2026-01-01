# API 文档: `files.d.ts`

本文档详细介绍了 `files.d.ts` 文件中定义的 API，该 API 提供了在脚本中进行文件和目录操作的全面功能。

## 概述

所有文件系统相关的功能都封装在全局的 `Tools.Files` 命名空间下。这个模块涵盖了从基本的读写、移动、删除，到更高级的压缩、下载等操作。

### Environment 参数

**重要**：所有文件操作函数都支持可选的 `environment` 参数，用于指定执行环境：

- `"android"` (默认): Android文件系统环境，路径使用Android格式（如 `/sdcard/Download`）
- `"linux"`: Ubuntu终端环境，路径使用Linux格式（如 `/home/user/file.txt`, `/etc/hosts`）

当使用 `"linux"` 环境时，系统会自动将Linux路径映射到Android文件系统中的实际位置：
- Ubuntu根目录位于：`/data/data/<应用包名>/files/usr/var/lib/proot-distro/installed-rootfs/ubuntu`
- 例如：`/home/user/test.txt` 会被映射到 `{filesDir}/usr/var/lib/proot-distro/installed-rootfs/ubuntu/home/user/test.txt`

**使用示例**：
```typescript
// 在Android环境读取文件
await Tools.Files.read("/sdcard/test.txt", "android");

// 在Linux环境读取文件
await Tools.Files.read("/home/user/config.txt", "linux");
```

---

## `Tools.Files` 命名空间详解

### 基本文件操作

-   `read(path: string): Promise<FileContentData>`: 读取一个文本文件的内容。对于大文件，可能只返回部分内容；若要读取完整内容，请使用 `readFull`。
-   `readFull(path: string): Promise<FileContentData>`: 强制读取并返回一个文本文件的全部内容。
-   `readPart(path: string, partIndex: number): Promise<FilePartContentData>`: 分块读取大文件，返回指定索引的部分。
-   `write(path: string, content: string): Promise<FileOperationData>`: 将文本内容写入一个文件。如果文件已存在，其内容将被覆盖。
-   `writeBinary(path: string, base64Content: string): Promise<FileOperationData>`: 将 Base64 编码的字符串解码为二进制数据并写入文件.
-   `readBinary(path: string, environment?: FileEnvironment): Promise<BinaryFileContentData>`: 读取二进制文件的内容，并返回一个包含 `path`、`contentBase64`（Base64 编码内容）和 `size`（字节大小）的结构化结果。
-   `list(path: string): Promise<DirectoryListingData>`: 列出指定目录下的所有文件和子目录。
-   `exists(path: string): Promise<FileExistsData>`: 检查指定路径的文件或目录是否存在。
-   `info(path: string): Promise<FileInfoData>`: 获取文件或目录的详细信息，如大小、修改日期、权限等。

### 文件和目录管理

-   `copy(source: string, destination: string): Promise<FileOperationData>`: 复制文件或目录。
-   `move(source: string, destination: string): Promise<FileOperationData>`: 移动或重命名文件或目录。
-   `deleteFile(path: string): Promise<FileOperationData>`: 删除一个文件或（递归删除）一个目录。
-   `mkdir(path: string): Promise<FileOperationData>`: 创建一个新目录。

### 高级文件操作

-   `find(path: string, pattern: string): Promise<FindFilesResultData>`: 在指定目录下根据模式（pattern）查找文件。
-   `grep(path: string, pattern: string, options?: object): Promise<GrepResultData>`: 在文件中搜索匹配正则表达式的代码内容。返回带行号和上下文的匹配结果。
    -   **`options`** 对象可以包含 `file_pattern`（文件过滤，如 `"*.kt"`）, `case_insensitive`（忽略大小写）, `context_lines`（匹配行前后的上下文行数，默认3）, `max_results`（最大匹配数，默认100）等参数。
-   `zip(source: string, destination: string): Promise<FileOperationData>`: 将指定的文件或目录压缩成一个 zip 文件。
-   `unzip(source: string, destination: string): Promise<FileOperationData>`: 将一个 zip 压缩包解压到指定目录。
-   `download(url: string, destination: string): Promise<FileOperationData>`: 从给定的 URL 下载文件并保存到本地。
-   `apply(path: string, content: string): Promise<FileApplyResultData>`: **（AI特定功能）** 将 AI 生成的内容智能地应用（合并/修改）到现有文件中。

### 文件交互

-   `open(path: string): Promise<FileOperationData>`: 请求系统使用默认的应用打开指定文件。
-   `share(path: string): Promise<FileOperationData>`: 调用系统的分享菜单来分享指定文件。

**示例: 读写文件**
```typescript
async function readAndWriteExample() {
    const filePath = "/sdcard/Documents/my_note.txt";
    try {
        // 写入文件
        await Tools.Files.write(filePath, "这是第一行。\n这是第二行。");

        // 读取文件
        const fileContent = await Tools.Files.read(filePath);
        console.log(fileContent.content);

        // 删除文件
        await Tools.Files.deleteFile(filePath);

        complete({ success: true, message: "文件读写和删除操作完成。" });
    } catch (error) {
        complete({ success: false, message: `文件操作失败: ${error.message}` });
    }
}
```

**示例: 下载并解压文件**
```typescript
async function downloadAndUnzip() {
    const url = "https://example.com/archive.zip";
    const zipPath = "/sdcard/Download/archive.zip";
    const extractPath = "/sdcard/Download/my_files/";

    try {
        await Tools.Files.download(url, zipPath);
        await Tools.Files.unzip(zipPath, extractPath);
        complete({ success: true, message: "文件已下载并解压。" });
    } catch (error) {
        complete({ success: false, message: `处理失败: ${error.message}` });
    }
}
```

**示例: 搜索代码**
```typescript
async function searchCodeExample() {
    const projectPath = "/sdcard/MyProject";
    
    try {
        // 在所有 Kotlin 文件中搜索包含 "ViewModel" 的代码
        const result = await Tools.Files.grep(projectPath, "ViewModel", {
            file_pattern: "*.kt",
            case_insensitive: false,
            max_results: 50
        });
        
        console.log(`找到 ${result.totalMatches} 个匹配项，分布在 ${result.matches.length} 个文件中`);
        
        // 处理搜索结果
        result.matches.forEach(fileMatch => {
            console.log(`文件: ${fileMatch.filePath}`);
            fileMatch.lineMatches.forEach(line => {
                console.log(`  ${line.lineNumber}: ${line.lineContent}`);
            });
        });
        
        complete({ success: true, result });
    } catch (error) {
        complete({ success: false, message: `搜索失败: ${error.message}` });
    }
}
``` 
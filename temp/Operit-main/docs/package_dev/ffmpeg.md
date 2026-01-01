# API 文档: `ffmpeg.d.ts`

本文档详细介绍了 `ffmpeg.d.ts` 文件中定义的 API，该 API 提供了在脚本中与 [FFmpeg](https://ffmpeg.org/) 多媒体处理框架交互的能力。

## 概述

所有 FFmpeg 相关功能都封装在全局的 `Tools.FFmpeg` 命名空间下。这个接口允许你执行原始的 FFmpeg 命令，也提供了一些常见操作（如视频转换）的简化函数。

-   **`Tools.FFmpeg.execute`**: 执行任意复杂的 FFmpeg 命令字符串。
-   **`Tools.FFmpeg.convert`**: 一个高级函数，用于简化常见的视频格式转换任务。
-   **`Tools.FFmpeg.info`**: 获取 FFmpeg 构建和配置信息。

此外，该文件还定义了一系列类型别名（如 `FFmpegVideoCodec`, `FFmpegResolution`），以提供更好的代码提示和类型安全。

---

## `Tools.FFmpeg` 命名空间详解

### `execute(command: string): Promise<FFmpegResultData>`

执行一个原始的 FFmpeg 命令字符串。这是最灵活但也最低级的方法，允许你使用 FFmpeg 的全部功能。

-   **`command`**: 要执行的完整 FFmpeg 命令字符串，**不包括**开头的 `ffmpeg` 部分。
    -   **正确**: `-i input.mp4 -vcodec libx264 output.mp4`
    -   **错误**: `ffmpeg -i input.mp4 -vcodec libx264 output.mp4`
-   **返回值**: 一个 `Promise`，成功时解析为 `FFmpegResultData` 对象，其中包含了执行的详细信息，如返回码、输出日志等。

**示例:**

```typescript
// 从视频中提取一帧为图片
const command = "-i /sdcard/Movies/input.mp4 -ss 00:00:05 -vframes 1 /sdcard/Pictures/output.jpg";
try {
    const result = await Tools.FFmpeg.execute(command);
    if (result.returnCode === 0) {
        complete({ success: true, message: "成功提取帧！" });
    } else {
        console.error(result.output);
        complete({ success: false, message: "提取失败。" });
    }
} catch (error) {
    complete({ success: false, message: `执行 FFmpeg 命令出错: ${error.message}` });
}
```

### `convert(inputPath: string, outputPath: string, options?: object): Promise<FFmpegResultData>`

一个用于简化视频转换任务的高级函数。你只需提供输入/输出路径和一些常见的转换选项，它会自动为你构建并执行相应的 FFmpeg 命令。

-   **`inputPath`**: 源视频文件的完整路径。
-   **`outputPath`**: 转换后输出文件的完整路径。
-   **`options`**: 一个可选的对象，包含以下转换参数：
    -   `video_codec?: FFmpegVideoCodec`: 视频编解码器，例如 `'libx264'`。
    -   `audio_codec?: FFmpegAudioCodec`: 音频编解码器，例如 `'aac'`。
    -   `resolution?: FFmpegResolution`: 输出分辨率，例如 `'1280x720'`。
    -   `bitrate?: FFmpegBitrate`: 视频比特率，例如 `'2000k'`。
-   **返回值**: 与 `execute` 方法相同，返回一个包含执行结果的 `Promise`。

**示例:**

```typescript
// 将视频转换为 720p，使用 H.264 编码
const input = "/sdcard/DCIM/camera/VID_20230101.mp4";
const output = "/sdcard/Movies/converted_video.mp4";

try {
    const result = await Tools.FFmpeg.convert(input, output, {
        resolution: '1280x720',
        video_codec: 'libx264',
        bitrate: '1500k'
    });
    if (result.returnCode === 0) {
        complete({ success: true, message: "视频转换成功！" });
    } else {
        complete({ success: false, message: `转换失败: ${result.output}` });
    }
} catch (error) {
    complete({ success: false, message: `转换时出错: ${error.message}` });
}
```

### `info(): Promise<FFmpegResultData>`

获取关于当前 FFmpeg 版本、构建配置和支持的库等信息。

-   **返回值**: 返回一个 `Promise`，解析为包含 FFmpeg 信息的 `FFmpegResultData` 对象。

---

## 类型别名

为了方便和类型安全，`ffmpeg.d.ts` 定义了以下类型：

-   **`FFmpegVideoCodec`**: 支持的视频编解码器字符串字面量类型。
    -   例如: `'libx264'`, `'libx265'`, `'mpeg4'`

-   **`FFmpegAudioCodec`**: 支持的音频编解码器字符串字面量类型。
    -   例如: `'aac'`, `'mp3'`, `'opus'`

-   **`FFmpegResolution`**: 常见的视频分辨率或自定义分辨率模板字符串。
    -   例如: `'1920x1080'`, `'1280x720'`, `` `${number}x${number}` ``

-   **`FFmpegBitrate`**: 常见的比特率或自定义比特率模板字符串。
    -   例如: `'1000k'`, `'2M'` 
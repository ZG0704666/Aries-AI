/* METADATA
{
    "name": "ffmpeg",
    "description": "提供FFmpeg工具，用于处理多媒体内容。",
    "enabledByDefault": true,
    "tools": [
        {
            "name": "ffmpeg_execute",
            "description": "执行自定义FFmpeg命令。",
            "parameters": [
                { "name": "command", "description": "要执行的FFmpeg命令", "type": "string", "required": true }
            ]
        },
        {
            "name": "ffmpeg_info",
            "description": "获取FFmpeg系统信息，包括版本、构建配置和支持的编解码器。",
            "parameters": []
        },
        {
            "name": "ffmpeg_convert",
            "description": "使用简化参数转换视频文件。",
            "parameters": [
                { "name": "input_path", "description": "源视频文件路径", "type": "string", "required": true },
                { "name": "output_path", "description": "目标视频文件路径", "type": "string", "required": true },
                { "name": "video_codec", "description": "可选，要使用的视频编解码器。支持的值: 'libx264', 'libx265', 'libvpx', 'libaom', 'mpeg4', 'mjpeg', 'prores', 'h264', 'hevc', 'vp8', 'vp9', 'av1'。", "type": "string", "required": false },
                { "name": "audio_codec", "description": "可选，要使用的音频编解码器。支持的值: 'aac', 'mp3', 'opus', 'vorbis', 'flac', 'pcm', 'wav', 'ac3', 'eac3'。", "type": "string", "required": false },
                { "name": "resolution", "description": "可选，输出分辨率，例如 '1280x720'。", "type": "string", "required": false },
                { "name": "bitrate", "description": "可选，视频比特率，例如 '1000k'。", "type": "string", "required": false }
            ]
        }
    ]
}
*/

const FFmpegTools = (function () {

    type FFmpegVideoCodec = 'libx264' | 'libx265' | 'libvpx' | 'libaom' | 'mpeg4' | 'mjpeg' | 'prores' | 'h264' | 'hevc' | 'vp8' | 'vp9' | 'av1';
    type FFmpegAudioCodec = 'aac' | 'mp3' | 'opus' | 'vorbis' | 'flac' | 'pcm' | 'wav' | 'ac3' | 'eac3';
    type FFmpegResolution = '1280x720' | '1920x1080' | '3840x2160' | '7680x4320' | `${number}x${number}`;
    type FFmpegBitrate = '500k' | '1000k' | '2000k' | '4000k' | '8000k' | `${number}k` | `${number}M`;

    interface ToolResponse {
        success: boolean;
        message: string;
        data?: any;
    }

    async function ffmpeg_execute(params: { command: string }): Promise<ToolResponse> {
        const result = await Tools.FFmpeg.execute(params.command);
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg command executed successfully." : `FFmpeg command failed with return code ${result.returnCode}.`,
            data: result.output
        };
    }

    async function ffmpeg_info(): Promise<ToolResponse> {
        const result = await Tools.FFmpeg.info();
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg info retrieved successfully." : "Failed to retrieve FFmpeg info.",
            data: result.output
        };
    }

    async function ffmpeg_convert(params: {
        input_path: string,
        output_path: string,
        video_codec?: FFmpegVideoCodec,
        audio_codec?: FFmpegAudioCodec,
        resolution?: FFmpegResolution,
        bitrate?: FFmpegBitrate
    }): Promise<ToolResponse> {
        const result = await Tools.FFmpeg.convert(params.input_path, params.output_path, {
            video_codec: params.video_codec,
            audio_codec: params.audio_codec,
            resolution: params.resolution,
            bitrate: params.bitrate,
        });
        return {
            success: result.returnCode === 0,
            message: result.returnCode === 0 ? "FFmpeg conversion completed successfully." : `FFmpeg conversion failed with return code ${result.returnCode}.`,
            data: result.output
        };
    }

    async function wrapToolExecution<P>(func: (params: P) => Promise<ToolResponse>, params: P) {
        try {
            const result = await func(params);
            complete(result);
        } catch (error: any) {
            console.error(`Tool ${func.name} failed unexpectedly`, error);
            complete({
                success: false,
                message: `工具执行时发生意外错误: ${error.message}`,
            });
        }
    }

    async function main() {
        console.log("--- FFmpeg Tools Test ---");

        console.log("\n[1/3] Testing ffmpeg_info...");
        const infoResult = await ffmpeg_info();
        console.log(JSON.stringify(infoResult, null, 2));

        console.log("\n[2/3] Testing ffmpeg_execute (example: getting help for a decoder)...");
        const executeResult = await ffmpeg_execute({ command: '-h decoder=h264' });
        console.log(JSON.stringify(executeResult, null, 2));

        console.log("\n[3/3] Testing ffmpeg_convert (example)...");
        console.log("Skipping ffmpeg_convert test as it requires input files.");

        complete({ success: true, message: "Test finished." });
    }

    return {
        ffmpeg_execute: (params: { command: string }) => wrapToolExecution(ffmpeg_execute, params),
        ffmpeg_info: (params: {}) => wrapToolExecution(ffmpeg_info, params),
        ffmpeg_convert: (params: {
            input_path: string,
            output_path: string,
            video_codec?: FFmpegVideoCodec,
            audio_codec?: FFmpegAudioCodec,
            resolution?: FFmpegResolution,
            bitrate?: FFmpegBitrate
        }) => wrapToolExecution(ffmpeg_convert, params),
        main,
    };
})();

exports.ffmpeg_execute = FFmpegTools.ffmpeg_execute;
exports.ffmpeg_info = FFmpegTools.ffmpeg_info;
exports.ffmpeg_convert = FFmpegTools.ffmpeg_convert;
exports.main = FFmpegTools.main; 
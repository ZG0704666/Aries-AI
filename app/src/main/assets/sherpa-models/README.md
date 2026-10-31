# sherpa-models

本目录用于存放离线语音识别相关模型文件。

- 当前引擎：**sherpa-onnx**（X-ASR-zh-en 流式 zipformer transducer：中英双语 + 自动标点）。
- 由于模型文件体积较大，本目录在仓库中默认被 `.gitignore` 忽略；仓库只保留本 `README.md`。

## 当前模型（X-ASR-zh-en punct int8，2026-06-05）

| 项 | 值 |
|---|---|
| 模型 | `sherpa-onnx-x-asr-480ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05` |
| 架构/运行时 | sherpa-onnx 流式 zipformer transducer（480ms 延迟块），**自带中英文标点输出** |
| 语言 | 中英双语（混说） |
| 上游权重 | HuggingFace `GilgameshWind/X-ASR-zh-en`（模型卡标注 Apache-2.0；**训练语料未披露，商用前需向作者确认**） |
| ONNX 转换发布 | k2-fsa/sherpa-onnx release `asr-models` tag |
| 包 sha256 | `fa5f63d618e5a01526e275a358bb7772e403f84808a4769fba52cffd8160bf74`（官方 release digest，2026-10-31 核验） |
| kotlin-api / so 版本 | sherpa-onnx **v2.0.1**（与模型成对升级） |

需要的目录结构（4 个文件）：

```
app/src/main/assets/sherpa-models/
  sherpa-onnx-x-asr-480ms-streaming-zipformer-transducer-zh-en-punct-int8-2026-06-05/
    encoder.int8.onnx   (~148M)
    decoder.onnx        (~11M)
    joiner.int8.onnx    (~2.4M)
    tokens.txt          (~57K)
```

> 代码读取入口：`SherpaSpeechRecognizer.createRecognizer()`（`modelDirName` 与 `requiredFiles` 常量需与上表同步）。

## 缓存完整性（R-04）

运行时会把 assets 模型先拷入 `filesDir/<模型目录名>.staging` 暂存目录：
拷贝完成 → 校验必需文件 → 计算每个文件长度与 SHA-256 并写入
`.aries-model-manifest` 清单（fsync）→ 原子 rename 为正式缓存目录。

启动期校验中，清单缺失、格式不符或任何必需文件长度不一致都会触发整份缓存重建；
进程被杀/拷贝中断不会留下“长度大于 0 的残缺模型”被加载。

## 旧引擎模型（sherpa-ncnn，已从 assets 移除）

- `sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13`（7 文件 ≈134.6MiB）已于 2026-10-31 从本目录移除，APK 相应瘦身。
- 需要回退 ncnn 引擎时：切回 `test-capsule` 分支后，从 sherpa-ncnn 官方 releases 重新下载同名 `tar.bz2`（≈123.6MiB），按嵌套或平铺结构放回本目录即可（代码入口在回退分支的 `createRecognizer()`）。

## 获取模型文件

从 sherpa-onnx 官方 release（`asr-models` tag）下载上表同名模型包，解压后按上述结构放置（只保留 4 个必需文件，`test_wavs/`、`bpe.model`、`test_onnx.py` 不需要）。

so 与 kotlin-api 获取：`sherpa-onnx-v2.0.1-android.tar.bz2`（sha256 `39003753ec293425ed56723ea9b972b98841a490af3311670c3060548d654330`）解包 `jniLibs/<abi>/lib{onnxruntime,sherpa-onnx-jni}.so` 至 `app/src/main/jniLibs/<abi>/`；kotlin-api 取自同 tag 源码 `sherpa-onnx/kotlin-api/`（当前仅收录 OnlineRecognizer 相关 5 文件）。

# sherpa-models

本目录用于存放离线语音识别相关模型文件。

- 本项目采用 **Sherpa-ncnn**（流式 zipformer 中英双语模型）。
- 由于模型文件体积较大，本目录在仓库中默认被 `.gitignore` 忽略；仓库只保留本 `README.md`。

## 模型来源与版本（R-01 溯源）

| 项 | 值 |
|---|---|
| 模型 | `sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13` |
| 架构/运行时 | sherpa-ncnn 流式 zipformer transducer（ncnn） |
| 语言 | 中英双语 |
| 官方文档 | https://k2-fsa.github.io/sherpa/ncnn/pretrained_models/zipformer-transucer-models.html |
| 官方仓库 | https://github.com/k2-fsa/sherpa-ncnn（releases 内有同名 `tar.bz2` 发行包） |
| 上游许可 | 随 sherpa-ncnn 模型页提供的许可条款，保留原始来源记录 |

替换/升级模型时必须同步更新 `SherpaSpeechRecognizer.createRecognizer()` 中的
`modelDirName` 与 7 个必需文件名，并重新记录来源与许可。

## 需要的目录结构（仅 7 个文件，除 README 不带任何其余文件）

将模型文件放到如下路径：

```
app/src/main/assets/sherpa-models/
  sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/
    encoder_jit_trace-pnnx.ncnn.param
    encoder_jit_trace-pnnx.ncnn.bin
    decoder_jit_trace-pnnx.ncnn.param
    decoder_jit_trace-pnnx.ncnn.bin
    joiner_jit_trace-pnnx.ncnn.param
    joiner_jit_trace-pnnx.ncnn.bin
    tokens.txt
```

> 代码读取入口：`SherpaSpeechRecognizer.kt`（嵌套布局 / legacy 嵌套布局 / 平铺布局三个候选会按序尝试）。

## 缓存完整性（R-04）

运行时会把 assets 模型先拷入 `filesDir/<模型目录名>.staging` 暂存目录：
拷贝完成 → 校验 7 个必需文件 → 计算每个文件长度与 SHA-256 并写入
`.aries-model-manifest` 清单（fsync）→ 原子 rename 为正式缓存目录。

启动期校验中，清单缺失、格式不符或任何必需文件长度不一致都会触发整份缓存重建；
进程被杀/拷贝中断不会留下“长度大于 0 的残缺模型”被加载。

## 获取模型文件

请从 **sherpa-ncnn 官方 releases** 下载与上表同名的模型包，解压后按上述结构放置。

下载后将文件放入上述目录，再重新构建运行即可启用离线语音识别。

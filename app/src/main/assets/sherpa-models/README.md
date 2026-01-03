# sherpa-models

本目录用于存放离线语音识别相关模型文件。

- 本项目采用 **Sherpa-ncnn**（流式 zipformer 中英双语模型）。
- 由于模型文件体积较大，本目录在仓库中默认被 `.gitignore` 忽略；仓库只保留本 `README.md`。

## 需要的目录结构（示例）

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

> 代码读取入口：`SherpaSpeechRecognizer.kt` 中的 `assetModelDir = "sherpa-models/<modelDirName>"`。

## 获取模型文件

请从 **sherpa-ncnn 官方仓库**或课程/项目共享位置下载对应模型（目录名必须与上方一致）。

- 官方项目： https://github.com/k2-fsa/sherpa-ncnn

下载后将文件放入上述目录，再重新构建运行即可启用离线语音识别。

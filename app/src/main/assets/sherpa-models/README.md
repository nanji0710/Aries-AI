# sherpa-models

> **重要（2026-10 起）**：离线语音模型**不再打进 APK**。它占 135 MB，此前让安装包从 112 MB
> 涨到 245 MB。现在改为 App 首次使用时下载到应用私有目录 `filesDir`，本目录只作为
> 「本地开发/离线构建」的备选投放点保留（放进来的文件会被 `.gitignore` 忽略，也不会进 APK）。

本目录用于存放离线语音识别相关模型文件（可选，仅本地开发用）。

- 本项目采用 **Sherpa-ncnn**（流式 zipformer 中英双语模型）。
- 由于模型文件体积较大，本目录在仓库中默认被 `.gitignore` 忽略；仓库只保留本 `README.md`。
- 运行时下载入口：设置页「模型与 API → 离线语音模型 → 下载离线语音模型」，
  实现见 `app/src/main/java/com/ai/phoneagent/speech/SherpaModelRepository.kt`（下载到 `filesDir`，
  已下载则跳过；`SherpaSpeechRecognizer.ensureModelDirReady()` 会直接复用该目录，不再从 assets 拷贝）。

## 需要的目录结构（仅本地开发需要）

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
- 模型发布页： https://github.com/k2-fsa/sherpa-ncnn/releases/tag/models
- 本项目使用的模型包（129,578,378 字节）：
  https://github.com/k2-fsa/sherpa-ncnn/releases/download/models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13.tar.bz2

一键下载、解压并放置（PowerShell，仓库根目录执行）：

```powershell
curl.exe -L --fail -o model.tar.bz2 `
  https://github.com/k2-fsa/sherpa-ncnn/releases/download/models/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13.tar.bz2
tar.exe -xjf model.tar.bz2
New-Item -ItemType Directory -Force `
  app\src\main\assets\sherpa-models\sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13 | Out-Null
Copy-Item sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13\* `
  app\src\main\assets\sherpa-models\sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13\
```

放置完成后重新构建（若尚未拉取 native submodule，还需 `git submodule update --init --recursive`，并删除 `app\.cxx` 清理 CMake 配置缓存，详见 `Aries-site/docs/开发规范与工具/构建脚本与工具链.md`），即可启用离线语音识别。**注意：把模型放进 assets 会让 APK 增大约 124 MB，正式分发不要这样做。**

> 模型加载路径：运行时下载（或首次从 assets 拷贝）落到应用私有目录
> `filesDir/sherpa-ncnn-streaming-zipformer-bilingual-zh-en-2023-02-13/`（约 135 MB）。
> 下载与拷贝都在 `Dispatchers.IO` 上执行并带完整性校验与进程级互斥，不会阻塞主线程。

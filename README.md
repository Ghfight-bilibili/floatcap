# 浮字 · FloatCap

**把平板正在播放的声音，实时变成屏幕上的字幕。** 端侧识别，不上传音频，不需要 root。

Android 10+ 上利用系统的 `AudioPlaybackCapture` 内录其他 App 的播放声音，交给端侧
[sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 做实时语音识别，再用悬浮窗显示字幕。
适合看网课、追剧、看直播、开会时听不清的时候。

## 功能

- **内录系统声音**：不用外放、不用麦克风，戴耳机也能出字幕
- **双引擎可切换**
  - **SenseVoice**：中文最准、自带标点，停顿后约 1 秒整句上屏
  - **Paraformer（流式）**：逐字上屏，延迟最低，适合直播和长课
- **完全离线**：模型下载一次后不再联网，音频不出设备
- **悬浮字幕条**：可拖动、可调宽度/字号/圆角/不透明度/行数/字色/字重/字间距/对齐/阴影
- **四种主题预设**：玻璃黑、纯黑、暖白、霓虹
- **三种动效**：逐字浮现、整句淡入、立即显示；满行时整条上滚
- **字幕记录**：自动累计本次每句，一键导出 TXT 到 `Download/DSH-Go/`
- **识别调参**：每行字数、断句静音、端点静音、线程数（1 核省电）
- **轻量**：APK 约 12 MB，模型按需下载，不随包分发

## 系统要求

- Android 10（API 29）及以上
- arm64-v8a
- 首次使用需下载约 230 MB 模型（只下一次）
- 需要授予：麦克风、通知、**显示在其他应用上层**；每次开始需同意一次「录制或投放屏幕」

## 安装

1. 到 [Releases](../../releases) 下载 `FloatCap-*.apk`
2. 安装后打开，按引导授权
3. 选引擎 → 下载模型 → 开始字幕 → 选「整个屏幕」
4. 切到视频 App，字幕会浮在画面上

> 首次安装需要在系统设置里允许「安装未知来源应用」。

## 从源码构建

需要 JDK 17、curl、unzip、python3。Android SDK、Kotlin 编译器、sherpa-onnx 运行库由脚本下载。

```bash
git clone <this repo>
cd floatcap
scripts/setup.sh          # 下载 build-tools / platform / kotlinc / sherpa-onnx
scripts/build.sh          # 产出 dist/FloatCap-2.1.apk
```

构建脚本不依赖 Gradle：`aapt` 打包资源 → `kotlinc` 编译 → `d8` 生成 dex → `zipalign` + `apksigner`。
脚本会生成开发用签名 `out/floatcap.keystore`，**正式发布请换成你自己的 keystore 并妥善保管**，
否则后续版本无法覆盖升级。

## 模型

App 不包含模型权重，首次使用时按引擎从 Hugging Face / hf-mirror 下载：

| 引擎 | 文件 | 大小 |
|---|---|---|
| SenseVoice | `model.int8.onnx` + `tokens.txt` | ~239 MB |
| Paraformer | `encoder.int8.onnx` + `decoder.int8.onnx` + `tokens.txt` | ~237 MB |
| VAD | `silero_vad.onnx` | ~1.8 MB |

模型各自的许可见 [NOTICE](NOTICE)。下载支持断点续传与镜像回退，失败会在界面上显示具体错误。

## 隐私

- 无账号、无统计、无遥测
- 音频只在设备上做推理，不写盘、不上传
- 唯一的网络请求是下载模型
- 字幕记录只存在内存里，导出时才写到你选择的位置

## 已知限制

- **受版权保护的影视内容**（Netflix、会员视频等）禁止被采集，只能退回麦克风
- 部分 App 声明了 `allowAudioPlaybackCapture=false`，同样采不到
- 识别质量取决于模型；嘈杂环境、方言、多人抢话会掉准确率
- 悬浮窗在少数系统上需要额外允许「后台弹出界面」
- 暂只支持 arm64；x86 模拟器需要自行补 lib

## 路线图

- [ ] 导出 SRT（带时间轴）
- [ ] 字幕历史持久化与回看
- [ ] 首次授权一键引导
- [ ] 双语字幕（识别 + 翻译）
- [ ] 更多引擎（Whisper、Qwen3-ASR）

## 许可

源码采用 [Apache-2.0](LICENSE)。第三方组件与模型许可见 [NOTICE](NOTICE)。

---

## English

**FloatCap** turns whatever your Android tablet is playing into live on-screen subtitles.
It captures other apps' audio with `AudioPlaybackCapture`, runs speech recognition fully
on-device with [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (SenseVoice or streaming
Paraformer), and renders a draggable, themeable subtitle overlay. No root, no account,
no audio leaves the device.

Build: `scripts/setup.sh && scripts/build.sh` (JDK 17, no Gradle needed). Requires Android 10+,
arm64-v8a. Licensed under Apache-2.0.

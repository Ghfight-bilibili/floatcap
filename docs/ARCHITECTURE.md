# 架构

```
MainActivity ──start──▶ CaptureService (前台服务, type=mediaProjection)
                              │
                     MediaProjection + AudioPlaybackCapture
                              │  16 kHz / mono / PCM16
                              ▼
                        Engine (SenseVoice | Paraformer)
                              │  partial / final
                              ▼
                          Overlay (TYPE_APPLICATION_OVERLAY)
```

## 关键点

- **内录**：`AudioPlaybackCaptureConfiguration` 匹配 `USAGE_MEDIA / GAME / UNKNOWN`，
  用 `AudioRecord.Builder().setAudioPlaybackCaptureConfig()` 建流。目标 App 若声明
  `allowAudioPlaybackCapture=false` 或内容受 DRM 保护，则采不到，这是系统限制。
- **前台服务顺序**：Android 14+ 要求先 `startForeground(type=mediaProjection)` 再
  `getMediaProjection()`，否则抛 `SecurityException`。
- **模型加载**：sherpa-onnx 的 Kotlin API 在传 `AssetManager` 时按 assets 路径解析；
  我们用绝对文件路径，必须传 `null`，否则 native 层崩溃。
- **字幕模型**：按「行」维护 `ArrayDeque<String>`，最后一行是正在增长的实时行。
  行数超限丢最早一行；换行时用两个 TextView 做上滚交叉动画。
- **逐字浮现**：把新增字符各挂一个 `CharacterStyle`，用 `ValueAnimator` 统一推进 alpha。
  局部改字（流式识别常见）只对差异尾部做淡入，避免整块重排导致闪烁。
- **线程**：采集/解码/推理都在一个专用线程；UI 更新 post 到主线程。

## 目录

| 路径 | 作用 |
|---|---|
| `app/src/CaptureService.kt` | 前台服务、音频采集、引擎生命周期 |
| `app/src/Engines.kt` | SenseVoice / Paraformer 封装 |
| `app/src/Overlay.kt` | 悬浮字幕窗与动画 |
| `app/src/Models.kt` | 模型下载（断点续传、镜像、校验） |
| `app/src/MainActivity.kt` | 主界面与授权流程 |
| `scripts/` | 无 Gradle 构建链 |

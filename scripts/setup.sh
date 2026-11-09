#!/usr/bin/env bash
# 一次性准备构建依赖：Android build-tools / platform、Kotlin 编译器、sherpa-onnx AAR。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CACHE="$ROOT/.cache"
SDK="$ROOT/.sdk"
mkdir -p "$CACHE" "$SDK"

need() { command -v "$1" >/dev/null 2>&1 || { echo "缺少 $1，请先安装"; exit 1; }; }
need java; need curl; need unzip

echo "== 下载 Android build-tools 34"
[ -f "$CACHE/build-tools.zip" ] || curl -L --retry 3 -o "$CACHE/build-tools.zip" \
  https://dl.google.com/android/repository/build-tools_r34-linux.zip
echo "== 下载 Android platform 34"
[ -f "$CACHE/platform.zip" ] || curl -L --retry 3 -o "$CACHE/platform.zip" \
  https://dl.google.com/android/repository/platform-34-ext7_r03.zip

[ -d "$SDK/android-14" ] || unzip -q -o "$CACHE/build-tools.zip" -d "$SDK"
[ -d "$SDK/android-34" ] || unzip -q -o "$CACHE/platform.zip" -d "$SDK"

echo "== 下载 Kotlin 编译器"
[ -d "$SDK/kotlinc" ] || {
  [ -f "$CACHE/kotlin.zip" ] || curl -L --retry 3 -o "$CACHE/kotlin.zip" \
    https://github.com/JetBrains/kotlin/releases/download/v2.1.20/kotlin-compiler-2.1.20.zip
  unzip -q -o "$CACHE/kotlin.zip" -d "$SDK"
}

echo "== 下载 sherpa-onnx Android 运行库"
[ -f "$CACHE/sherpa.aar" ] || curl -L --retry 3 -o "$CACHE/sherpa.aar" \
  https://github.com/k2-fsa/sherpa-onnx/releases/download/v2.0.1/sherpa-onnx-2.0.1.aar
[ -d "$SDK/aar" ] || {
  mkdir -p "$SDK/aar/classes" "$SDK/aar/libs"
  unzip -q -o "$CACHE/sherpa.aar" classes.jar -d "$SDK/aar"
  (cd "$SDK/aar" && unzip -q -o classes.jar -d classes)
  unzip -j -o "$CACHE/sherpa.aar" "jni/arm64-v8a/*" -d "$SDK/aar/libs" >/dev/null
}

echo "依赖就绪：$SDK"

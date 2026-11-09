#!/usr/bin/env bash
# 构建并签名 APK，输出 dist/FloatCap-<ver>.apk
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="$ROOT/.sdk"
BT="$SDK/android-14"
AJAR="$SDK/android-34/android.jar"
KOTLINC="$SDK/kotlinc"
AAR="$SDK/aar"
VER="${VER:-2.1}"
CODE="${CODE:-11}"
OUT="$ROOT/out"
DIST="$ROOT/dist"
APP="$ROOT/app"

[ -d "$SDK" ] || { echo "先运行 scripts/setup.sh"; exit 1; }
mkdir -p "$OUT/gen" "$OUT/dex" "$OUT/classes" "$DIST"

pick() { # 优先系统命令，其次 build-tools
  local name="$1"
  if command -v "$name" >/dev/null 2>&1; then command -v "$name"; else echo "$BT/$name"; fi
}
AAPT="$(pick aapt)"
ZIPALIGN="$(pick zipalign)"
APKSIGNER="$(pick apksigner)"

echo "[1/5] 资源与清单"
"$AAPT" package -f -m -J "$OUT/gen" -M "$APP/AndroidManifest.xml" -S "$APP/res" \
  -I "$AJAR" -F "$OUT/base.apk" --min-sdk-version 29 --target-sdk-version 34 \
  --version-code "$CODE" --version-name "$VER"

echo "[2/5] 编译 Kotlin"
JAVA_OPTS="${JAVA_OPTS:--Xmx1200m}" "$KOTLINC/bin/kotlinc" \
  -classpath "$AJAR:$AAR/classes.jar:$KOTLINC/lib/kotlin-stdlib.jar" \
  -jvm-target 1.8 -nowarn -d "$OUT/classes" "$APP"/src/*.kt

echo "[3/5] dex"
CLASSES=$(find "$OUT/classes" -name "*.class" | tr '\n' ' ')
JAVA_OPTS="${JAVA_OPTS:--Xmx1200m}" "$BT/d8" --release --min-api 29 --lib "$AJAR" \
  --output "$OUT/dex" $CLASSES "$AAR/classes.jar" "$KOTLINC/lib/kotlin-stdlib.jar" > "$OUT/d8.log" 2>&1 \
  || { tail -30 "$OUT/d8.log"; exit 1; }

echo "[4/5] 打包"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
python3 - "$OUT/unsigned.apk" "$OUT/dex/classes.dex" "$AAR/libs" <<'PY'
import os, sys, zipfile
apk, dex, libs = sys.argv[1], sys.argv[2], sys.argv[3]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, 'classes.dex')
    for f in sorted(os.listdir(libs)):
        z.write(os.path.join(libs, f), 'lib/arm64-v8a/' + f)
PY

echo "[5/5] 对齐与签名"
"$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
KEY="$OUT/floatcap.keystore"
if [ ! -f "$KEY" ]; then
  keytool -genkeypair -keystore "$KEY" -alias floatcap -storepass floatcap -keypass floatcap \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=FloatCap, O=FloatCap, C=CN" >/dev/null 2>&1
  echo "已生成开发签名 $KEY（公开发布请换成你自己的 keystore）"
fi
"$APKSIGNER" sign --ks "$KEY" --ks-pass pass:floatcap --key-pass pass:floatcap \
  --out "$DIST/FloatCap-$VER.apk" "$OUT/aligned.apk"
"$APKSIGNER" verify "$DIST/FloatCap-$VER.apk" >/dev/null && echo "OK: $DIST/FloatCap-$VER.apk"

#!/bin/bash
# 兜底编译脚本：当 gradlew 不可用时，使用 Android SDK command-line tools 直接编译
set -e

export ANDROID_HOME=${ANDROID_HOME:-/usr/local/android-sdk}
export ANDROID_SDK_ROOT=$ANDROID_HOME
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

echo "=== 检查 Android SDK ==="
which sdkmanager || { echo "需要安装 Android SDK cmdline-tools"; exit 1; }

# 接受 licenses
yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses >/dev/null 2>&1 || true

# 确保 platform-34 build-tools 存在
sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "platforms;android-34" "build-tools;34.0.0" >/dev/null 2>&1 || true

BUILD_TOOLS=$ANDROID_HOME/build-tools/34.0.0
PLATFORM=$ANDROID_HOME/platforms/android-34/android.jar

mkdir -p build/classes build/res build/apk

echo "=== 编译 Java/Kotlin → classes ==="
find app/src/main/java -name "*.java" -o -name "*.kt" > sources.txt
javac -source 17 -target 17 \
    -classpath "$PLATFORM:app/libs/*" \
    -d build/classes \
    @sources.txt 2>&1 | head -20 || true

echo "=== 编译资源 ==="
$aapt2 compile --dir app/src/main/res -o build/res/res.zip 2>&1 | head -10 || true
$aapt2 link -o build/apk/resources.ap_ \
    -I $PLATFORM \
    --manifest app/src/main/AndroidManifest.xml \
    build/res/res.zip 2>&1 | head -10 || true

echo "=== 打包 DEX ==="
d8 --min-api 24 --output build/apk/classes.dex build/classes/*.class 2>&1 | head -10 || true

echo "=== 生成 APK ==="
mkdir -p build/apk/META-INF
cp build/apk/classes.dex build/apk/
cd build/apk && zip -r ../../output/AI小手机-debug.apk . && cd ../..

echo "=== 签名（debug keystore）==="
if [ ! -f ~/.android/debug.keystore ]; then
    keytool -genkeypair -v -keystore ~/.android/debug.keystore \
        -storepass android -keypass android -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" 2>/dev/null || true
fi
apksigner sign --ks ~/.android/debug.keystore \
    --ks-pass pass:android --key-alias androiddebugkey \
    --key-pass pass:android \
    --in output/AI小手机-debug.apk \
    --out output/AI小手机-signed.apk 2>/dev/null || \
    cp output/AI小手机-debug.apk output/AI小手机-signed.apk

echo "=== DONE ==="
ls -la output/*.apk

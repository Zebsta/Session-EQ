#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
SDK_DIR="${SESSION_EQ_SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/.cache/session-eq-sdk}}}"
if [[ -f "$SDK_DIR/platforms/android-35/android.jar" ]]; then
    TOOLS_DIR="$SDK_DIR/build-tools/35.0.0"
    ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"
else
    TOOLS_DIR="$SDK_DIR/android-15"
    ANDROID_JAR="$SDK_DIR/android-35/android.jar"
fi
if [[ ! -f "$ANDROID_JAR" || ! -f "$TOOLS_DIR/lib/d8.jar" ]]; then
    echo 'Set SESSION_EQ_SDK to the SDK directory described in README.md.' >&2
    exit 1
fi
python3 - <<'PY'
from pathlib import Path
import shutil
for name in ('res','generated','classes','dex'):
    path=Path('build')/name
    if path.exists(): shutil.rmtree(path)
    path.mkdir(parents=True)
PY
mkdir -p dist
APP_VERSION="$(python3 - <<'PY'
import xml.etree.ElementTree as E
print(E.parse('app/src/main/AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName'))
PY
)"
OUTPUT_APK="dist/Session-EQ-$APP_VERSION.apk"
"$TOOLS_DIR/aapt2" compile --dir app/src/main/res -o build/res
mapfile -t RES_FILES < <(find build/res -type f -name '*.flat' | sort)
"$TOOLS_DIR/aapt2" link -o build/resources.apk -I "$ANDROID_JAR" \
    --manifest app/src/main/AndroidManifest.xml --java build/generated \
    --min-sdk-version 28 --target-sdk-version 35 "${RES_FILES[@]}"
mapfile -t JAVA_FILES < <(find app/src/main/java build/generated -name '*.java' | sort)
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$TOOLS_DIR/core-lambda-stubs.jar" \
    -d build/classes "${JAVA_FILES[@]}"
jar cf build/classes.jar -C build/classes .
java -cp "$TOOLS_DIR/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api 28 --lib "$ANDROID_JAR" --output build/dex build/classes.jar
python3 - <<'PY'
import pathlib, shutil, zipfile
shutil.copyfile('build/resources.apk','build/unsigned.apk')
with zipfile.ZipFile('build/unsigned.apk','a') as apk:
    for path in sorted(pathlib.Path('build/dex').glob('*.dex')):
        apk.write(path,path.name)
PY
"$TOOLS_DIR/zipalign" -f 4 build/unsigned.apk build/aligned.apk
SIGNING_KEY="${SESSION_EQ_SIGNING_KEY:-$HOME/.local/share/session-eq/debug.keystore}"
if [[ ! -f "$SIGNING_KEY" ]]; then
    mkdir -p "$(dirname "$SIGNING_KEY")"
    keytool -genkeypair -keystore "$SIGNING_KEY" -storepass android -keypass android \
        -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
        -dname 'CN=Session EQ Development,O=Local,C=US' >/dev/null 2>&1
    chmod 600 "$SIGNING_KEY"
fi
java -jar "$TOOLS_DIR/lib/apksigner.jar" sign --ks "$SIGNING_KEY" \
    --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --out "$OUTPUT_APK" build/aligned.apk
java -jar "$TOOLS_DIR/lib/apksigner.jar" verify --verbose "$OUTPUT_APK"
"$TOOLS_DIR/zipalign" -c 4 "$OUTPUT_APK"
sha256sum dist/*.apk > dist/SHA256SUMS
echo "Built $OUTPUT_APK"

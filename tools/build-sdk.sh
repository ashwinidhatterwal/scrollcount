#!/usr/bin/env bash
# Dependency-free release-candidate build; reuse your existing keystore to preserve signing identity.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME to an installed Android SDK}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17}"
bt="$ANDROID_HOME/build-tools/35.0.0"
platform="$ANDROID_HOME/platforms/android-36/android.jar"
out="build/sdk-probe"
mkdir -p "$out" app/src/main/assets
rm -rf "$out/classes" "$out/dex"
mkdir -p "$out/classes" "$out/dex"
"$bt/aapt2" compile --no-crunch --dir app/src/main/res -o "$out/resources.zip"
sed 's/<manifest /<manifest package="in.scrollcount.probe" /' app/src/main/AndroidManifest.xml > "$out/AndroidManifest.xml"
"$bt/aapt2" link -o "$out/unsigned.apk" -I "$platform" --manifest "$out/AndroidManifest.xml" \
  -A app/src/main/assets --min-sdk-version 26 --target-sdk-version 36 --version-code 30 --version-name 1.2.12 "$out/resources.zip"
find app/src/main/java -name '*.java' -print > "$out/sources.txt"
"$JAVA_HOME/bin/javac" --release 8 -classpath "$platform" -d "$out/classes" @"$out/sources.txt"
"$JAVA_HOME/bin/jar" cf "$out/classes.jar" -C "$out/classes" .
JAVA_HOME="$JAVA_HOME" "$bt/d8" --min-api 26 --lib "$platform" --output "$out/dex" "$out/classes.jar"
(cd "$out/dex" && zip -q -u ../unsigned.apk classes.dex)
"$bt/zipalign" -f -p 4 "$out/unsigned.apk" "$out/aligned.apk"
if [ ! -f "$out/debug.keystore" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$out/debug.keystore" -storepass android -keypass android \
    -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
fi
JAVA_HOME="$JAVA_HOME" "$bt/apksigner" sign --ks "$out/debug.keystore" --ks-pass pass:android --out "$out/ScrollCount-Debug.apk" "$out/aligned.apk"
JAVA_HOME="$JAVA_HOME" "$bt/apksigner" verify --verbose "$out/ScrollCount-Debug.apk"
"$bt/aapt2" dump badging "$out/ScrollCount-Debug.apk"

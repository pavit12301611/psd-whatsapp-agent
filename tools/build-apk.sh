#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BT=${BUILD_TOOLS:-${ANDROID_HOME:-}/build-tools/34.0.0}; JAR=${ANDROID_JAR:-${ANDROID_HOME:-}/platforms/android-34/android.jar}; S=app/src/main; B=build/apk
mkdir -p "$B/classes" "$B/dex" releases
"$BT/aapt" package -f -M "$S/AndroidManifest.xml" -S "$S/res" -A "$S/assets" -I "$JAR" -F "$B/resources.apk"
javac -encoding UTF-8 --release 8 -classpath "$JAR" -d "$B/classes" $(find "$S/java" -name '*.java')
jar cf "$B/code.jar" -C "$B/classes" .
"$BT/d8" --min-api 28 --lib "$JAR" --output "$B/dex" "$B/code.jar"
cp "$B/resources.apk" "$B/unsigned.apk"; (cd "$B/dex" && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
K=${MIMI_KEYSTORE:-$HOME/.android/mimi-test.jks}; export MIMI_STORE_PASS=${MIMI_STORE_PASS:-android}
if [[ ! -f "$K" ]]; then mkdir -p "$(dirname "$K")"; keytool -genkeypair -keystore "$K" -alias mimi -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Mimi Local Build' -storepass:env MIMI_STORE_PASS -keypass:env MIMI_STORE_PASS;fi
"$BT/apksigner" sign --ks "$K" --ks-pass env:MIMI_STORE_PASS --out releases/Mimi-Voice-Desk.apk "$B/aligned.apk"
"$BT/apksigner" verify --verbose releases/Mimi-Voice-Desk.apk

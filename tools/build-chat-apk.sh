#!/usr/bin/env bash
# Offline on the phone; dependency and ABI runtime downloads happen at build time only.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD; BT=${BUILD_TOOLS:-${ANDROID_HOME:-}/build-tools/34.0.0}; JAR=${ANDROID_JAR:-${ANDROID_HOME:-}/platforms/android-34/android.jar}; B=build/chat-apk
mkdir -p "$B"/{assets,classes,dex,lib/arm64-v8a,downloads} releases
MANIFEST=chat-android/AndroidManifest.xml
OUT=releases/Mimi-Chat-Desk.apk
K=${MIMI_KEYSTORE:-$HOME/.android/mimi-test.jks}; export MIMI_STORE_PASS=${MIMI_STORE_PASS:-android}
if [[ ${1:-} == --pocket-preview || ${1:-} == --pocket-next ]]; then
  # A separate app ID keeps the existing install and its private data intact.
  if [[ ${1:-} == --pocket-next ]]; then
    ID=app.mimi.desk.pocket2; LABEL='Mimi Pocket 2'; OUT=${MIMI_OUT:-releases/Mimi-Pocket-2.apk}
  else
    ID=app.mimi.desk.pocket; LABEL='Mimi Pocket Desk'; OUT=${MIMI_OUT:-releases/Mimi-Pocket-Desk.apk}
  fi
  sed "s/package=\"app.mimi.desk\"/package=\"$ID\"/; s/android:name=\"[.]Chat/android:name=\"app.mimi.desk.Chat/g; s/android:label=\"Mimi Chat Desk\"/android:label=\"$LABEL\"/" "$MANIFEST" > "$B/preview-manifest.tmp"
  mkdir -p "$B/preview"; mv "$B/preview-manifest.tmp" "$B/preview/AndroidManifest.xml"
  MANIFEST=$B/preview/AndroidManifest.xml
elif [[ -f "$OUT" && ! -f "$K" ]]; then
  echo 'Original signing key missing. Use --pocket-preview for a separate install.' >&2; exit 1
fi
cp -R chat-android/pocket "$B/assets/"
TGZ=$B/downloads/oven-bun-linux-aarch64-android-1.4.5.tgz
if [[ ! -f "$TGZ" ]]; then (cd "$B/downloads" && npm pack --silent @oven/bun-linux-aarch64-android@1.4.5); fi
[[ $(openssl dgst -sha512 -binary "$TGZ" | openssl base64 -A) == SI502JpXyu6em6y8E3F48GO3LwjYXSG3DWEOPhf9LXJWjjnPG9OwrWajtM91saa2vAixZ0ErlZa8UzNORcgcyQ== ]]
tar -xOf "$TGZ" package/bin/bun > "$B/lib/arm64-v8a/libmimirun.so"; chmod 755 "$B/lib/arm64-v8a/libmimirun.so"
(cd chat && npm ci --omit=optional --no-audit --no-fund && npm test)
node --input-type=module -e 'import {away} from "./chat/routing.mjs";console.log(JSON.stringify(Object.fromEntries(["en","hi"].map(l=>[l,Object.fromEntries(["busy","sleeping","driving"].map(m=>[m,away(m,l)]))]))))' > "$B/assets/greetings.json"
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
with ZipFile('build/chat-apk/assets/chat-runtime.zip','w',ZIP_DEFLATED) as z:
    base=Path('chat')
    for file in [*(base/name for name in ('mobile.mjs','mobile-engine.mjs','call-shield.mjs','routing.mjs','routine.mjs','providers.mjs','package.json')),*(base/'node_modules').rglob('*')]:
        if not file.is_file() or file.is_symlink() or '.bin' in file.parts or file.suffix in ('.ts','.map'):continue
        z.write(file,file.relative_to(base))
    z.write('licenses/Bun-1.4.5.md','licenses/Bun-1.4.5.md')
PY
"$BT/aapt" package -f -M "$MANIFEST" -S chat-android/res -A "$B/assets" -I "$JAR" -F "$B/unsigned.apk"
javac -encoding UTF-8 --release 8 -classpath "$JAR" -d "$B/classes" app/src/main/java/app/mimi/desk/Store.java chat-android/java/app/mimi/desk/*.java
jar cf "$B/code.jar" -C "$B/classes" .; "$BT/d8" --min-api 28 --lib "$JAR" --output "$B/dex" "$B/code.jar"
(cd "$B/dex" && zip -q ../unsigned.apk classes.dex); (cd "$B" && zip -q -9 unsigned.apk lib/arm64-v8a/libmimirun.so)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
if [[ ! -f "$K" ]]; then mkdir -p "$(dirname "$K")"; keytool -genkeypair -keystore "$K" -alias mimi -keyalg RSA -keysize 3072 -validity 3650 -dname 'CN=Mimi Local Build' -storepass:env MIMI_STORE_PASS -keypass:env MIMI_STORE_PASS;fi
"$BT/apksigner" sign --ks "$K" --ks-pass env:MIMI_STORE_PASS --out "$B/signed.apk" "$B/aligned.apk"
"$BT/apksigner" verify --verbose --print-certs "$B/signed.apk"
cert() { "$BT/apksigner" verify --print-certs "$1" | sed -n '/Signer #1 certificate SHA-256 digest:/p'; }
if [[ -f "$OUT" && "$(cert "$OUT")" != "$(cert "$B/signed.apk")" ]]; then
  echo 'Different signing certificate; existing APK left untouched.' >&2; exit 1
fi
cp "$B/signed.apk" "$OUT"

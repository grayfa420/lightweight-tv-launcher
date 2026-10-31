#!/bin/zsh
# Builds and signs Clean Home without Gradle.
#   ./build.sh               -> CleanHome.apk        (Xiaomi Mi TV Stick: stars wallpaper + Wi-Fi QR from assets/)
#   ./build.sh assets-tesla  -> CleanHome-tesla.apk  (Tesla TV: rotating photos + the same Wi-Fi QR)
set -e
cd "${0:A:h}"
ASSETS=${1:-assets}
OUT=CleanHome${ASSETS#assets}.apk
SDK=/opt/homebrew/share/android-commandlinetools
BT=$SDK/build-tools/37.0.0
JAR=$SDK/platforms/android-30/android.jar
rm -rf build && mkdir -p build/classes build/dex build/gen
$BT/aapt2 compile --dir res -o build/res.zip
$BT/aapt2 link -o build/base.apk -I $JAR --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 30 \
  -A $ASSETS --java build/gen build/res.zip
javac --release 11 -nowarn -cp $JAR -d build/classes $(find src build/gen -name '*.java')
$BT/d8 --release --min-api 26 --lib $JAR --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q ../base.apk classes.dex)
$BT/zipalign -f 4 build/base.apk build/aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias cleanhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Clean Home" >/dev/null 2>&1
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --out $OUT build/aligned.apk
echo "Built $(pwd)/$OUT"

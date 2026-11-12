#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
PLATFORM="${1:-}"
case "$PLATFORM" in
  mobile) VARIANT=Mobile; VERSION_FILE=android-version.json;;
  tv) VARIANT=Tv; VERSION_FILE=tv-version.json;;
  *) echo 'Usage: scripts/ci-build.sh mobile|tv' >&2; exit 2;;
esac
: "${SIGNING_KEYSTORE_PATH:?Signed CI build requires SIGNING_KEYSTORE_PATH}"
: "${SIGNING_STORE_PASSWORD:?Signed CI build requires SIGNING_STORE_PASSWORD}"
: "${SIGNING_KEY_ALIAS:?Signed CI build requires SIGNING_KEY_ALIAS}"
[[ -s "$SIGNING_KEYSTORE_PATH" ]] || { echo 'Signing keystore is missing/empty' >&2; exit 1; }
python3 scripts/validate-config.py
GRADLE=${GRADLE_CMD:-gradle}
"$GRADLE" -p android ":app:assemble${VARIANT}Release" ":app:bundle${VARIANT}Release"
APK="android/app/build/outputs/apk/${PLATFORM}/release/app-${PLATFORM}-release.apk"
AAB="android/app/build/outputs/bundle/${PLATFORM}Release/app-${PLATFORM}-release.aab"
[[ -s "$APK" && -s "$AAB" ]] || { echo 'Expected signed APK/AAB not found' >&2; exit 1; }
BUILD_TOOLS=$(sed -n 's/^BUILD_TOOLS_VERSION=//p' android-sdk.properties)
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK_ROOT" ]] || { echo 'ANDROID_HOME or ANDROID_SDK_ROOT required for apksigner verification' >&2; exit 1; }
"$SDK_ROOT/build-tools/$BUILD_TOOLS/apksigner" verify --verbose "$APK"
bash scripts/verify-aab.sh "$AAB"
VERSION_NAME=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["versionName"])' "$VERSION_FILE")
VERSION_CODE=$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["versionCode"])' "$VERSION_FILE")
OUT="artifacts/$PLATFORM"
mkdir -p "$OUT"
BASENAME="vizhi-tivi-${PLATFORM}-v${VERSION_NAME}-b${VERSION_CODE}"
cp "$APK" "$OUT/${BASENAME}.apk"
cp "$AAB" "$OUT/${BASENAME}.aab"
echo "Signed artifacts verified and copied: $OUT/$BASENAME.{apk,aab}"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  echo "## Vizhi TiVi ${PLATFORM} — ${VERSION_NAME} (${VERSION_CODE})" >> "$GITHUB_STEP_SUMMARY"
  echo '- Signed APK and AAB verified successfully.' >> "$GITHUB_STEP_SUMMARY"
fi

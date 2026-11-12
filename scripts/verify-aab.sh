#!/usr/bin/env bash
set -euo pipefail
: "${SIGNING_KEYSTORE_PATH:?Signing keystore required}"
: "${SIGNING_STORE_PASSWORD:?Signing store password required}"
: "${SIGNING_KEY_ALIAS:?Expected signing alias required}"
[[ $# == 1 && -s "$1" && -s "$SIGNING_KEYSTORE_PATH" ]] || { echo 'Missing bundle or keystore' >&2; exit 1; }
VERIFY_OUTPUT=$(LC_ALL=C jarsigner -verify -strict -keystore "$SIGNING_KEYSTORE_PATH" -storepass:env SIGNING_STORE_PASSWORD "$1" "$SIGNING_KEY_ALIAS")
printf '%s\n' "$VERIFY_OUTPUT" | grep -Fxq 'jar verified.' || { echo 'AAB is not signed by the expected signer' >&2; exit 1; }

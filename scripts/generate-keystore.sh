#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
KEYSTORE="${1:-release-keystore.jks}"
ALIAS="${KEY_ALIAS:-vizhitivi}"
[[ ! -e "$KEYSTORE" ]] || { echo "Refusing to overwrite existing signing key: $KEYSTORE" >&2; exit 1; }
command -v keytool >/dev/null || { echo 'Java keytool is required.' >&2; exit 1; }
if [[ -z "${KEYSTORE_PASSWORD:-}" ]]; then
  read -rsp 'Enter new keystore password (12+ chars): ' KEYSTORE_PASSWORD; echo
fi
[[ ${#KEYSTORE_PASSWORD} -ge 12 ]] || { echo 'Password must be at least 12 characters.' >&2; exit 1; }
# PKCS12 normally uses the same store/key password; no credentials are printed.
keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$KEYSTORE_PASSWORD" \
  -keypass "$KEYSTORE_PASSWORD" -alias "$ALIAS" -keyalg RSA -keysize 3072 \
  -validity 10000 -dname 'CN=Vizhi TiVi, OU=Apps, O=AR Apps, C=IN' -noprompt
chmod 600 "$KEYSTORE"
echo "Created $KEYSTORE; key alias: $ALIAS"
echo "Encode safely: base64 -w0 $KEYSTORE > keystore.b64  (do not commit either file)"
echo 'Back up the original keystore securely. Keep KEYSTORE_PASSWORD private.'

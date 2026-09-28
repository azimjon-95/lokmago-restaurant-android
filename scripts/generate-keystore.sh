#!/usr/bin/env bash
# Creates the production upload keystore OUTSIDE the repository.
# The keystore + passwords are the identity of the app: lose them and you cannot ship updates
# (with Play App Signing you can request an upload-key reset, without it the app is orphaned).
# BACK IT UP (password manager + offline copy) before the first release.
set -euo pipefail
DIR="${LOKMAGO_SIGNING_DIR:-$HOME/.lokmago-signing}"
FILE="$DIR/lokmago-release.jks"
ALIAS="${KEY_ALIAS:-lokmago}"
[ -e "$FILE" ] && { echo "Refusing to overwrite $FILE"; exit 1; }
command -v keytool >/dev/null || { echo "keytool (JDK) not found"; exit 1; }
mkdir -p "$DIR" && chmod 700 "$DIR"
read -r -s -p "New keystore/key password (min 12 chars): " PASS; echo
[ "${#PASS}" -ge 12 ] || { echo "Password too short"; exit 1; }
keytool -genkeypair -v -keystore "$FILE" -storetype JKS -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=LokmaGo, O=LokmaGo, C=UZ"
chmod 600 "$FILE"
cat <<MSG

Created: $FILE   (alias: $ALIAS)
Put in env.properties (git-ignored):
  KEYSTORE_PATH=$FILE
  KEY_ALIAS=$ALIAS
  KEYSTORE_PASSWORD=<the password you just typed>
  KEY_PASSWORD=<same>
For GitHub Actions: base64 -w0 "$FILE"  -> secret KEYSTORE_BASE64, plus KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD.
MSG

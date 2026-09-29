#!/usr/bin/env bash
# Post-build gate for a release APK. Used locally and by .github/workflows/release.yml.
#   scripts/verify-apk.sh app/build/outputs/apk/release/app-release.apk
# Checks: valid v2+ signature (not the debug key), production host inside, no test hosts,
# no server-secret patterns. Exit code != 0 means: do NOT ship this APK.
set -uo pipefail
APK="${1:?usage: verify-apk.sh <apk>}"
PROD_HOST="${PROD_HOST:-restoran-api.lokma.uz}"
fail=0; bad() { echo "FAIL: $*"; fail=1; }; ok() { echo "ok:   $*"; }
[ -f "$APK" ] || { echo "no such file: $APK"; exit 2; }

# ---- signature ----
APKSIGNER="$(command -v apksigner || ls -1 "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/nonexistent}}"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)"
if [ -n "$APKSIGNER" ] && [ -x "$APKSIGNER" ]; then
  out="$("$APKSIGNER" verify --verbose --print-certs "$APK" 2>&1)"; rc=$?
  if [ $rc -eq 0 ]; then ok "signature verifies"; else bad "apksigner verify failed: $out"; fi
  echo "$out" | grep -qi "Android Debug" && bad "signed with the Android DEBUG key"
  echo "$out" | grep -E "Signer #1 certificate (SHA-256|DN)" | sed 's/^/      /'
else
  [ "${REQUIRE_APKSIGNER:-0}" = 1 ] && bad "apksigner not found" || echo "skip: apksigner not found (set ANDROID_HOME) - signature NOT verified"
fi
unzip -l "$APK" | grep -q "META-INF/.*\.\(RSA\|EC\|DSA\)" || bad "no signature block in META-INF (unsigned APK?)"

# ---- contents ----
T="$(mktemp -d)"; trap 'rm -rf "$T"' EXIT
unzip -q -o "$APK" -d "$T" || { echo "cannot unzip"; exit 2; }
# Only files that really exist: a missing path makes grep exit 2 and, with pipefail, would hide real matches.
ALL=(); for f in "$T"/classes*.dex "$T"/resources.arsc "$T"/AndroidManifest.xml; do [ -f "$f" ] && ALL+=("$f"); done
[ ${#ALL[@]} -gt 0 ] || { echo "FAIL: APK has no dex/manifest"; exit 1; }
grep -aq "$PROD_HOST" "${ALL[@]}" 2>/dev/null && ok "contains $PROD_HOST" || bad "$PROD_HOST not found - wrong API baked in?"

# test / local endpoints must not be present
if grep -aEoh "(https?://)?(localhost|127\.0\.0\.1|10\.0\.2\.2|192\.168\.[0-9.]+|[a-z0-9.-]*ngrok[a-z0-9.-]*|api\.example\.com)" "${ALL[@]}" 2>/dev/null | sort -u | grep . ; then
  bad "test/local endpoint strings found above"; else ok "no test/local endpoints"; fi
grep -aEoh "http://[a-zA-Z0-9.-]+" "${ALL[@]}" 2>/dev/null | grep -v -E "schemas\.android\.com|www\.w3\.org|xmlpull\.org|ns\.adobe\.com|apache\.org|json\.org|localhost" | sort -u | sed 's/^/      cleartext-looking: /'

# server secrets (Firebase 'AIza' keys are public client keys and are allowed)
PAT='mongodb(\+srv)?://|JWT_SECRET|BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY|cloudinary://|CLOUDINARY_API_SECRET|CLICK_SECRET|PAYNET_SECRET|INTERNAL_WEBHOOK_SECRET|ghp_[A-Za-z0-9]{20,}|github_pat_|sk-ant-|-----BEGIN'
if grep -rEal "$PAT" "$T" 2>/dev/null | grep . ; then bad "secret-like pattern found in files above"; else ok "no server-secret patterns"; fi

# debuggable must be off
if command -v aapt >/dev/null 2>&1 || ls "${ANDROID_HOME:-/nonexistent}"/build-tools/*/aapt >/dev/null 2>&1; then
  AAPT="$(command -v aapt || ls -1 "$ANDROID_HOME"/build-tools/*/aapt | sort -V | tail -1)"
  "$AAPT" dump badging "$APK" | grep -q "application-debuggable" && bad "APK is debuggable"
  "$AAPT" dump badging "$APK" | grep -E "^package:" | sed 's/^/      /'
fi
[ $fail -eq 0 ] && echo "RESULT: PASS" || echo "RESULT: FAIL"
exit $fail

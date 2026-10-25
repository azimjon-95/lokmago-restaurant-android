#!/usr/bin/env bash
# One source file -> the right sound for BOTH apps.
#   tools/sound/make_sounds.sh my_sound.mp3            (also accepts .wav/.m4a/.caf)
#   tools/sound/make_sounds.sh my_sound.mp3 --dry-run  (only prints what it would do)
# Android: app/src/main/res/raw/order_alert.mp3   (the old order_alert.wav is removed: two files with one name break the build)
# iOS:     ios/LokmaRestaurant/order_alert.wav    (notification sounds must be linear PCM/IMA4/µ-law/A-law and <= 30 s; mp3 is NOT allowed)
set -euo pipefail
IN="${1:?usage: make_sounds.sh <sound-file> [--dry-run]}"; DRY="${2:-}"
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
[ -f "$IN" ] || { echo "no such file: $IN"; exit 1; }
run() { echo "+ $*"; [ "$DRY" = "--dry-run" ] || "$@"; }

# --- Android ---
run cp "$IN" "$ROOT/app/src/main/res/raw/order_alert.mp3"
run rm -f "$ROOT/app/src/main/res/raw/order_alert.wav"

# --- iOS (max 30 s, mono, 44.1 kHz, 16-bit PCM) ---
OUT="$ROOT/ios/LokmaRestaurant/order_alert.wav"
if command -v afconvert >/dev/null 2>&1; then
  run afconvert -f WAVE -d LEI16@44100 -c 1 -t 30 "$IN" "$OUT"
elif command -v ffmpeg >/dev/null 2>&1; then
  run ffmpeg -y -loglevel error -i "$IN" -t 30 -ar 44100 -ac 1 -c:a pcm_s16le "$OUT"
else
  echo "Need 'afconvert' (macOS) or 'ffmpeg' to make the iOS sound"; exit 1
fi
echo "Done. Android: order_alert.mp3 — iOS: order_alert.wav. If the app is already installed, bump the channel id (Android Notifier.CH_ORDERS) or reinstall."

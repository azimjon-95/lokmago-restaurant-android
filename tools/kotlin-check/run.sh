#!/usr/bin/env bash
# Runs the PURE-Kotlin unit tests (no Android SDK, no Gradle, no Maven) with a bare Kotlin compiler.
#   tools/kotlin-check/run.sh                 # all suites below
# What it covers: domain/OrderQueue, push/PushPayload, push/DeviceRegistration (+ coroutines, shipped inside the compiler zip).
# What it CANNOT cover: anything touching Android, Hilt, Retrofit, Firebase or kotlinx.serialization (those run in `./gradlew testDebugUnitTest`).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KT_VERSION="${KT_VERSION:-2.0.21}"
CACHE="${KT_CACHE:-$HOME/.cache/lokma-kotlinc}"
if [ ! -x "$CACHE/kotlinc/bin/kotlinc" ]; then
  mkdir -p "$CACHE"; echo "downloading kotlinc $KT_VERSION ..."
  curl -sL -o "$CACHE/k.zip" "https://github.com/JetBrains/kotlin/releases/download/v$KT_VERSION/kotlin-compiler-$KT_VERSION.zip"
  unzip -q -o "$CACHE/k.zip" -d "$CACHE" && rm -f "$CACHE/k.zip"
fi
KC="$CACHE/kotlinc/bin/kotlinc"; COROUTINES="$CACHE/kotlinc/lib/kotlinx-coroutines-core-jvm.jar"
M="$ROOT/app/src/main/java/uz/lokmago/restaurant"; T="$ROOT/app/src/test/java/uz/lokmago/restaurant"
W="$(mktemp -d)"; trap 'rm -rf "$W"' EXIT
mkdir -p "$W/tests"
for f in "$T/OrderQueueTest.kt" "$T/AlertAttentionTest.kt" "$T/PhoneSetupTest.kt" "$T/push/PushPayloadTest.kt" "$T/push/DeviceRegistrationTest.kt"; do
  sed -e 's/^import org\.junit\.Assert\.\*/import lokma.shim.*/' -e 's/^import org\.junit\.Test/import lokma.shim.Test/' "$f" > "$W/tests/$(basename "$f")"
done
"$KC" -nowarn -cp "$COROUTINES" -d "$W/out" \
  "$ROOT/tools/kotlin-check/shim/Shim.kt" "$ROOT/tools/kotlin-check/shim/Runner.kt" \
  "$M/domain/Order.kt" "$M/domain/OrderQueue.kt" "$M/util/Format.kt" "$M/push/PushPayload.kt" "$M/push/DeviceRegistration.kt" "$M/alert/AlertAttention.kt" "$M/alert/PhoneSetup.kt" \
  "$W"/tests/*.kt 2>&1 | grep -v "^warning:" || true
java -cp "$W/out:$CACHE/kotlinc/lib/kotlin-stdlib.jar:$COROUTINES" lokma.shim.RunnerKt \
  uz.lokmago.restaurant.OrderQueueTest uz.lokmago.restaurant.AlertAttentionTest uz.lokmago.restaurant.PhoneSetupTest uz.lokmago.restaurant.push.PushPayloadTest uz.lokmago.restaurant.push.DeviceRegistrationTest

# ---- JSON contract with the server (needs kotlinx-serialization; the compiler plugin ships with kotlinc, the runtime is looked up) ----
SER_DIR="${SER_DIR:-}"
if [ -z "$SER_DIR" ]; then
  for d in /opt/gradle*/lib "$HOME"/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-serialization-*/*/*; do
    ls "$d"/kotlinx-serialization-json-jvm-*.jar >/dev/null 2>&1 && ls "$d"/kotlinx-serialization-core-jvm-*.jar >/dev/null 2>&1 && { SER_DIR="$d"; break; }
  done
fi
if [ -z "$SER_DIR" ]; then echo "JSON suite skipped: kotlinx-serialization runtime not found (set SER_DIR). It still runs in ./gradlew testDebugUnitTest."; exit 0; fi
SER_CP="$(ls "$SER_DIR"/kotlinx-serialization-core-jvm-*.jar | head -1):$(ls "$SER_DIR"/kotlinx-serialization-json-jvm-*.jar | head -1)"
J="$(mktemp -d)"; trap 'rm -rf "$W" "$J"' EXIT
mkdir -p "$J/src"
sed -e 's/^import org\.junit\.Assert\.\*/import lokma.shim.*/' -e 's/^import org\.junit\.Test/import lokma.shim.Test/' "$T/data/remote/ApiJsonTest.kt" > "$J/src/ApiJsonTest.kt"
"$KC" -nowarn -Xplugin="$CACHE/kotlinc/lib/kotlinx-serialization-compiler-plugin.jar" -cp "$SER_CP:$COROUTINES" -d "$J/out" \
  "$ROOT/tools/kotlin-check/shim/Shim.kt" "$ROOT/tools/kotlin-check/shim/Runner.kt" "$ROOT/tools/kotlin-check/shim/RetrofitStub.kt" \
  "$M/data/remote/ApiJson.kt" "$M/data/remote/dto/Dto.kt" "$M"/domain/*.kt "$M/util/Format.kt" "$M/courier/CourierApi.kt" "$J/src/ApiJsonTest.kt" 2>&1 | grep -v "^warning:" || true
java -cp "$J/out:$CACHE/kotlinc/lib/kotlin-stdlib.jar:$SER_CP:$COROUTINES" lokma.shim.RunnerKt uz.lokmago.restaurant.data.remote.ApiJsonTest


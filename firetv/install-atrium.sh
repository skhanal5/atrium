#!/usr/bin/env bash
# Build Atrium, push it to the Fire TV and install it.
#
# Grants log access. The Amazon launcher stays enabled.
#
# Usage: TV_HOST=10.0.0.71 firetv/install-atrium.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$HERE/.."
TV_HOST="${TV_HOST:-10.0.0.71}"; export TV_HOST
PY="$ROOT/tools/venv/bin/python"
TV="$ROOT/tools/tv.py"
APK="$ROOT/atrium/app/build/outputs/apk/debug/app-debug.apk"
PKG="dev.skhanal.atrium"

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
export PATH="$JAVA_HOME/bin:$PATH"

echo "--- build ---"
cd "$ROOT/atrium"
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
ls -l "$APK"

echo "--- push ---"
# A direct ADB push hangs on this device, so the APK goes over in base64 chunks
# through the shell. It is small enough that this takes a few seconds.
"$PY" -I "$ROOT/tools/b64push.py" "$APK" /data/local/tmp/atrium.apk

echo "--- install ---"
printf '%s\n' \
  "pm install -r /data/local/tmp/atrium.apk" \
  "pm grant $PKG android.permission.READ_LOGS" \
  "rm -f /data/local/tmp/atrium.apk" \
  "pm list packages $PKG" \
  | "$PY" -I "$TV" --batch

echo "--- open it ---"
printf '%s\n' "am start -n $PKG/.AtriumActivity" | "$PY" -I "$TV" --batch

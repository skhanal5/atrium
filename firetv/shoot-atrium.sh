#!/usr/bin/env bash
# Drive Atrium through its states and save a screenshot of each one.
#
# This takes over the screen, so only run it when nobody is watching the TV.
# It does not install anything. Run firetv/install-atrium.sh first.
#
# Usage: TV_HOST=10.0.0.71 firetv/shoot-atrium.sh [outdir]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$HERE/.."
OUT="${1:-$HERE/shots}"
TV_HOST="${TV_HOST:-10.0.0.71}"; export TV_HOST
PY="$ROOT/tools/venv/bin/python"
TV="$ROOT/tools/tv.py"
PKG="dev.skhanal.atrium"

mkdir -p "$OUT"

# Key codes. 19 up, 20 down, 21 left, 22 right, 23 select, 4 back, 82 menu.
keys() { [ -n "$1" ] && printf 'input keyevent %s\n' $1 || true; }

# shoot <name> <keys before the shot> <settle seconds>
shoot() {
  local name="$1" before="${2:-}" settle="${3:-2}"
  {
    keys "$before"
    echo "sleep $settle"
    echo "screencap -p /sdcard/shot.png"
  } | "$PY" -I "$TV" --batch > /dev/null
  "$PY" -I "$TV" --pull /sdcard/shot.png "$OUT/$name.png" > /dev/null
  echo "  $name"
}

echo "--- restart, so every run starts the same way ---"
printf '%s\n' "am force-stop $PKG" "sleep 1" "am start -n $PKG/.AtriumActivity" \
  | "$PY" -I "$TV" --batch > /dev/null

echo "--- shots ---"
shoot atrium-01-home            ""        4
shoot atrium-02-home-third-app  "22 22"   2
shoot atrium-03-app-menu        "82"      2
shoot atrium-04-tools-row       "4 20"    2
shoot atrium-05-apps-grid       "19 21 20 23" 3
shoot atrium-06-preferences     "21 20 23"    3

echo "--- back to the home screen ---"
printf '%s\n' "input keyevent 4" "input keyevent 4" "rm -f /sdcard/shot.png" \
  | "$PY" -I "$TV" --batch > /dev/null
echo "done. shots are in $OUT"

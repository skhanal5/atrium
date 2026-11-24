#!/usr/bin/env bash
# Remove Atrium and everything it saved.
#
# Amazon's launcher stays enabled throughout.
#
# Usage: TV_HOST=10.0.0.71 firetv/uninstall-atrium.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$HERE/.."
TV_HOST="${TV_HOST:-10.0.0.71}"; export TV_HOST
PY="$ROOT/tools/venv/bin/python"
TV="$ROOT/tools/tv.py"
PKG="dev.skhanal.atrium"

printf '%s\n' \
  "am force-stop $PKG" \
  "pm uninstall $PKG" \
  "pm list packages $PKG" \
  | "$PY" -I "$TV" --batch

echo "If the last line printed nothing, Atrium is gone."

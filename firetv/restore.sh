#!/usr/bin/env bash
# Re-enable every package in debloat.txt, putting the TV back to stock.
#
# Usage: TV_HOST=10.0.0.71 ./restore.sh [list.txt]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TV_HOST="${TV_HOST:-10.0.0.71}"; export TV_HOST
PY="${PY:-$HERE/../tools/venv/bin/python}"
TV="${TV:-$HERE/../tools/tv.py}"
LIST="${1:-$HERE/debloat.txt}"
{
  grep -vE '^[[:space:]]*(#|$)' "$LIST" | awk '{print "pm enable " $1}'
  echo "pm enable com.amazon.tv.launcher"
  echo "cmd package set-home-activity --user 0 com.amazon.tv.launcher/.ui.HomeActivity_vNext"
} | "$PY" -I "$TV" --batch

#!/usr/bin/env bash
# Disable every package in debloat.txt on the Fire TV.
# Nothing is uninstalled. Undo with ./restore.sh
#
# Usage: TV_HOST=10.0.0.71 ./debloat.sh [list.txt]
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TV_HOST="${TV_HOST:-10.0.0.71}"; export TV_HOST
PY="${PY:-$HERE/../tools/venv/bin/python}"
TV="${TV:-$HERE/../tools/tv.py}"
LIST="${1:-$HERE/debloat.txt}"
grep -vE '^[[:space:]]*(#|$)' "$LIST" | awk '{print "pm disable-user --user 0 " $1}' \
  | "$PY" -I "$TV" --batch

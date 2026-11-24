#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TEST_CLASSES="$(mktemp -d)"
trap 'rm -rf "$TEST_CLASSES"' EXIT
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$PATH"
javac -d "$TEST_CLASSES" \
  "$ROOT/atrium/app/src/main/java/dev/skhanal/atrium/system/HomePress.java" \
  "$ROOT/atrium/app/src/test/java/dev/skhanal/atrium/system/HomePressTest.java"
java -cp "$TEST_CLASSES" dev.skhanal.atrium.system.HomePressTest

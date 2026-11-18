#!/usr/bin/env bash
# Runs the integrity engine's host tests:
#   1. builds synthetic APKs (good + tampered variants),
#   2. runs the REAL Gradle task printReleaseChecksums on good.apk to generate a header,
#   3. compiles integrity_guard.cpp (IG_HOST_TEST) against that header and asserts every verdict,
#   4. runs verifyReleaseChecksums (must pass on good.apk, must fail on a tampered APK).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${1:-$(mktemp -d)}"
mkdir -p "$WORK"
python3 "$HERE/make_test_apk.py" "$WORK"
GRADLE="${GRADLE:-gradle}"
"$GRADLE" -q -p "$HERE/gradle-harness" printReleaseChecksums \
  -PintegrityApk="$WORK/good.apk" -PintegrityHeader="$WORK/expected_hashes.h"
g++ -std=c++17 -O2 -Wall -Wextra -Werror -DIG_EXPECTED_HEADER="\"$WORK/expected_hashes.h\"" \
  "$HERE/host_test.cpp" -lz -o "$WORK/host_test"
"$WORK/host_test" "$WORK"
"$GRADLE" -q -p "$HERE/gradle-harness" verifyReleaseChecksums \
  -PintegrityApk="$WORK/good.apk" -PintegrityHeader="$WORK/expected_hashes.h"
if "$GRADLE" -q -p "$HERE/gradle-harness" verifyReleaseChecksums \
  -PintegrityApk="$WORK/tamper_dex.apk" -PintegrityHeader="$WORK/expected_hashes.h" >/dev/null 2>&1; then
  echo "FAIL: verifyReleaseChecksums accepted a tampered APK"; exit 1
fi
echo "verifyReleaseChecksums correctly rejected tamper_dex.apk"
echo "HOST TESTS PASSED"

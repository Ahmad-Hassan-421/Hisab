#!/usr/bin/env bash
set -euo pipefail

# Run from Hisab/. Retry dependency-server throttling/outages, never failing app tests.
report_dir='build/ci-instrumentation'
mkdir -p "$report_dir"
for attempt in 1 2 3; do
  report="$report_dir/gradle-attempt-$attempt.log"
  if bash ./gradlew --no-daemon connectedDebugAndroidTest -PHISAB_QA=true \
    -PHISAB_DEBUG_API_URL=http://127.0.0.1:18093/ \
    -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true 2>&1 | tee "$report"; then
    adb pull /sdcard/Android/data/com.example.hisab.qa/files/splash-previews build/splash-previews
    exit 0
  fi
  grep -qE 'Received status code (429|5[0-9]{2})' "$report" || exit 1
  [ "$attempt" -lt 3 ] || exit 1
  sleep $((attempt * 30))
done
exit 1

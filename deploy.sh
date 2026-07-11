#!/usr/bin/env bash
# Build + install + launch rideman on a connected Android device.
#
#   ./deploy.sh          build debug, install, launch
#   ./deploy.sh -r       release build instead of debug
#   ./deploy.sh -l       after launch, follow the app's logcat (Ctrl-C to stop)
#   ./deploy.sh -l -r    combine
set -euo pipefail

cd "$(dirname "$0")"

PKG="com.two17industries.rideman"
ACTIVITY=".MainActivity"
VARIANT="debug"; GRADLE_TASK="installDebug"
FOLLOW_LOG=0

for arg in "$@"; do
  case "$arg" in
    -r|--release) VARIANT="release"; GRADLE_TASK="installRelease" ;;
    -l|--logcat)  FOLLOW_LOG=1 ;;
    -h|--help)    grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown arg: $arg (try -h)" >&2; exit 2 ;;
  esac
done

# Resolve adb: PATH first, then ANDROID_HOME / ANDROID_SDK_ROOT.
ADB="$(command -v adb || true)"
for base in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}"; do
  [ -n "$ADB" ] && break
  [ -x "$base/platform-tools/adb" ] && ADB="$base/platform-tools/adb"
done
[ -n "$ADB" ] || { echo "adb not found (set ANDROID_HOME or put adb on PATH)" >&2; exit 1; }

# Require exactly one connected device.
DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
COUNT="$(printf '%s\n' "$DEVICES" | grep -c . || true)"
if [ "$COUNT" -eq 0 ]; then
  echo "no device connected — plug in a phone (USB debugging on) or start an emulator" >&2
  exit 1
elif [ "$COUNT" -gt 1 ]; then
  echo "multiple devices attached; pick one and set ANDROID_SERIAL:" >&2
  printf '  %s\n' $DEVICES >&2
  exit 1
fi
echo "==> device: $DEVICES  (variant: $VARIANT)"

echo "==> building + installing ($GRADLE_TASK)…"
./gradlew ":app:$GRADLE_TASK"

echo "==> launching $PKG$ACTIVITY…"
"$ADB" shell am start -n "$PKG/$ACTIVITY" >/dev/null

echo "==> done."
if [ "$FOLLOW_LOG" -eq 1 ]; then
  echo "==> logcat (Ctrl-C to stop)…"
  "$ADB" logcat --pid="$("$ADB" shell pidof -s "$PKG")"
fi

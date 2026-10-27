#!/usr/bin/env bash
# Takes the website's screenshots from a connected phone running AdWizz:
#   docs/screenshots/{home-light,home-dark,spell,settings,devices}.webp
#
# Uses Android's demo mode for a clean status bar (fixed time, full battery), and shows the
# Settings tab rather than Activity so no personal browsing data ends up on the website.
set -euo pipefail
cd "$(dirname "$0")/.."
ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
OUT=docs/screenshots
TMP=$(mktemp -d)
mkdir -p "$OUT"
"$ADB" get-state >/dev/null

ui() { "$ADB" shell "uiautomator dump /sdcard/adwizz-ui.xml >/dev/null; cat /sdcard/adwizz-ui.xml" | tr '>' '\n'; }
tap() {   # tap the first node whose attributes match $1
  local b; b=$(ui | grep -E "$1" | grep -o 'bounds="[^"]*"' | head -1)
  [[ -n "$b" ]] || { echo "not found: $1"; return 1; }
  read -r x1 y1 x2 y2 <<<"$(echo "$b" | tr -c '0-9' ' ')"
  "$ADB" shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
shot() { "$ADB" exec-out screencap -p > "$TMP/$1.png"; }
theme() {  # light | dark, via the sun/moon in the sky
  if [[ $1 == dark ]]; then ui | grep -q 'Switch to dark mode' && tap 'content-desc="Switch to dark mode"' || true
  else ui | grep -q 'Switch to light mode' && tap 'content-desc="Switch to light mode"' || true; fi
  sleep 1.5
}

"$ADB" shell settings put global sysui_demo_allowed 1
dm() { "$ADB" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
dm enter; dm clock -e hhmm 0930; dm battery -e level 100 -e plugged false; dm network -e wifi show -e level 4; dm notifications -e visible false
trap 'dm exit' EXIT

"$ADB" shell am start -n rocks.chirag.adshield/.ui.MainActivity >/dev/null; sleep 3
tap 'text="Settings"'; sleep 1
# Start unprotected so the spell can be captured.
ui | grep -q 'Turn protection off' && { tap 'content-desc="Turn protection off"'; sleep 3; }
theme light; shot home-light
tap 'content-desc="Turn protection on"'; sleep 1.6; shot spell      # mid-spell: ads lifting off
sleep 4; theme dark; shot home-dark
for _ in 1 2 3; do "$ADB" shell input swipe 720 2600 720 1700 250; sleep 0.4; done; shot settings
theme light; tap 'text="Devices"'; sleep 3; shot devices
tap 'text="Settings"'

python3 - "$TMP" "$OUT" <<'EOF'
import sys, glob, os
from PIL import Image
tmp, out = sys.argv[1:]
for p in glob.glob(os.path.join(tmp, "*.png")):
    im = Image.open(p).convert("RGB")
    w = 540
    im.resize((w, round(im.height * w / im.width)), Image.LANCZOS).save(
        os.path.join(out, os.path.basename(p)[:-4] + ".webp"), quality=82, method=6)
    print("wrote", os.path.basename(p)[:-4] + ".webp")
EOF

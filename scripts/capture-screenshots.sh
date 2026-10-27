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
# Screen size, so swipes work on any phone or emulator.
read -r SW SH <<<"$("$ADB" shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -1 | tr x ' ')"
scroll() { "$ADB" shell input swipe $((SW / 2)) $((SH * 85 / 100)) $((SW / 2)) $((SH * 55 / 100)) 250; sleep 0.4; }

ui() { "$ADB" shell "uiautomator dump /sdcard/adwizz-ui.xml >/dev/null; cat /sdcard/adwizz-ui.xml" | tr '>' '\n'; }
tap() {   # tap the first node whose attributes match $1
  local b; b=$(ui | grep -E "$1" | grep -o 'bounds="[^"]*"' | head -1)
  [[ -n "$b" ]] || { echo "not found: $1"; return 1; }
  read -r x1 y1 x2 y2 <<<"$(echo "$b" | tr -c '0-9' ' ')"
  "$ADB" shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
waitfor() {  # wait (up to 15 s) until something matching $1 is on screen
  for _ in $(seq 1 15); do has "$1" && return 0; sleep 1; done
  echo "timed out waiting for: $1"; return 1
}
# "Is $1 on screen?" (not grep -q: under pipefail its early exit makes the pipeline fail)
has() { ui | grep -E "$1" >/dev/null; }
shot() { "$ADB" exec-out screencap -p > "$TMP/$1.png"; }
theme() {  # light | dark, via the sun/moon in the sky
  if [[ $1 == dark ]]; then has 'Switch to dark mode' && tap 'content-desc="Switch to dark mode"' || true
  else has 'Switch to light mode' && tap 'content-desc="Switch to light mode"' || true; fi
  sleep 1.5
}

"$ADB" shell settings put global sysui_demo_allowed 1
dm() { "$ADB" shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
dm enter; dm clock -e hhmm 0930; dm battery -e level 100 -e plugged false; dm network -e wifi show -e level 4; dm notifications -e visible false
trap 'dm exit' EXIT

"$ADB" shell am start -n rocks.chirag.adwizz/.ui.MainActivity >/dev/null; sleep 3
tap 'text="Settings"'; sleep 1
# First run on a device: accept Android's one-time VPN prompt, then stop so the spell can be timed.
if has 'Turn protection on'; then
  tap 'content-desc="Turn protection on"'; sleep 2
  has 'text="(OK|Ok|Allow)"' && { tap 'text="(OK|Ok|Allow)"'; sleep 4; }
fi
if has 'Turn protection off'; then
  tap 'content-desc="Turn protection off"'; waitfor 'Turn protection on'; sleep 1.5   # let the ads tumble back
fi
theme light; shot home-light
tap 'content-desc="Turn protection on"'; sleep 1.6; shot spell      # mid-spell: ads lifting off
waitfor 'text="Protected"'; sleep 2; theme dark; shot home-dark
scroll; shot settings   # one scroll: the Protection switches
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

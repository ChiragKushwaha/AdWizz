#!/usr/bin/env bash
# End-to-end ad-blocking test on a real phone.
#
# Needs: a debug build of AdWizz installed and switched ON, the phone connected over adb, Chrome.
# Opens canyoublockit.com's test pages in Chrome, then reads AdWizz's DNS decisions from logcat
# (debug builds log "AdWizzDns: BLOCK x" / "ALLOW x") and fails if any known ad host got through
# or a pop-under tab opened.
#
#   scripts/device-adblock-test.sh            # uses the only connected device
#   ANDROID_SERIAL=XYZ scripts/device-adblock-test.sh
set -euo pipefail

ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
PKG=rocks.chirag.adshield
PAGES=("https://canyoublockit.com/" "https://canyoublockit.com/extreme-test/")
# Hosts that must be blocked (kept in sync with CanYouBlockIt.AD_HOSTS in the unit tests).
AD_HOSTS=(12ezo5v60.com fvcwqkkqmuv.com ybs2ffs7v.com kjfhenoqfyfljo.com d3cod80thn7qnd.cloudfront.net
          antiadblocksystems.com googlesyndication.com doubleclick.net googletagmanager.com pgjlctmswgnwf.com)
# Hosts that must stay reachable.
SITE_HOSTS=(canyoublockit.com)

fail=0
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }

"$ADB" get-state >/dev/null || { red "No device connected"; exit 2; }
"$ADB" shell ip -o addr show tun0 >/dev/null 2>&1 || { red "AdWizz VPN isn't running (no tun0). Turn it on first."; exit 2; }
"$ADB" shell dumpsys package "$PKG" | grep -q "DEBUGGABLE" || { red "Install a debug build (it logs DNS decisions)."; exit 2; }

tabs_count() {
  "$ADB" shell "uiautomator dump /sdcard/adwizz-ui.xml >/dev/null 2>&1; cat /sdcard/adwizz-ui.xml" \
    | grep -oE 'content-desc="[0-9]+ (standard )?tabs?' | grep -oE '[0-9]+' | head -1 || true
}

"$ADB" logcat -c
for url in "${PAGES[@]}"; do
  echo "Opening $url"
  # Same application_id => Chrome reuses one tab instead of opening a new one per page.
  "$ADB" shell am start -a android.intent.action.VIEW -d "$url" \
    -e com.android.browser.application_id adwizz-test com.android.chrome >/dev/null
  sleep 12
  for _ in 1 2 3; do "$ADB" shell input swipe 720 2200 720 800 250; sleep 1; done
done

# Pop-under check: tapping the page must not spawn an ad tab.
before=$(tabs_count)
"$ADB" shell input tap 540 1500; sleep 4
after=$(tabs_count)
if [[ -n "$before" && -n "$after" && "$after" -gt "$before" ]]; then
  red "FAIL pop-under: tab count went $before -> $after"; fail=1
else
  green "ok   no pop-under tab opened"
fi

log=$("$ADB" logcat -d -s AdWizzDns:D | sed -nE 's/.*AdWizzDns: (BLOCK|ALLOW) (.*)/\1 \2/p' | sort -u)
[[ -n "$log" ]] || { red "No DNS decisions logged — is Chrome using its own Secure DNS?"; exit 1; }

matches() { grep -E "^$1 (.*\.)?$2\$" <<<"$log" || true; }
for h in "${AD_HOSTS[@]}"; do
  leaked=$(matches ALLOW "${h//./\\.}")
  blocked=$(matches BLOCK "${h//./\\.}")
  if [[ -n "$leaked" ]]; then red "FAIL leaked: $leaked"; fail=1
  elif [[ -n "$blocked" ]]; then green "ok   blocked $h"
  else echo "--   $h not requested this run (page rotates its ad networks)"; fi
done
for h in "${SITE_HOSTS[@]}"; do
  if [[ -n "$(matches BLOCK "${h//./\\.}")" ]]; then red "FAIL site broken: $h was blocked"; fail=1
  else green "ok   $h reachable"; fi
done

echo
echo "Other third-party hosts allowed during the test (review for new ad networks):"
grep '^ALLOW' <<<"$log" | grep -vE 'canyoublockit\.com|google(apis|usercontent)?\.com|gstatic\.com|vimeo|android|samsung' | sed 's/^/   /' || true

exit $fail

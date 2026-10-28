#!/usr/bin/env bash
# Publishes the "AdWizz Reels Blocker" add-on (the :focus module) to its own GitHub release and
# adds it to docs/update.json, where AdWizz looks for it when the user turns on
# "Block Reels & Shorts". Run it only when the add-on changed:
#
#   1. Bump versionCode (+1) and versionName in focus/build.gradle.kts
#   2. scripts/release-addon.sh
#
# Needs keystore.properties (it must be signed with AdWizz's key) and the GitHub CLI.
set -euo pipefail
cd "$(dirname "$0")/.."

command -v gh >/dev/null || { echo "Install the GitHub CLI first: brew install gh && gh auth login"; exit 2; }
[[ -f keystore.properties ]] || { echo "keystore.properties missing: the add-on must be signed with the AdWizz key"; exit 2; }
[[ -z "$(git status --porcelain)" ]] || { echo "Commit or stash your changes first."; exit 2; }

CODE=$(sed -nE 's/.*versionCode = ([0-9]+).*/\1/p' focus/build.gradle.kts)
NAME=$(sed -nE 's/.*versionName = "([^"]+)".*/\1/p' focus/build.gradle.kts)
TAG="reels-blocker-v$NAME"
REPO="ChiragKushwaha/AdWizz"
git rev-parse "$TAG" >/dev/null 2>&1 && { echo "Tag $TAG already exists: bump the add-on's versionName/versionCode first."; exit 2; }
LAST=$(python3 -c 'import json;print(json.load(open("docs/update.json")).get("focus",{}).get("versionCode",0))')
(( CODE > LAST )) || { echo "Add-on versionCode $CODE must be higher than the published $LAST."; exit 2; }

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
./gradlew --quiet :focus:testDebugUnitTest :focus:lintDebug :focus:assembleRelease

mkdir -p build/release
APK=build/release/AdWizzReelsBlocker.apk
cp focus/build/outputs/apk/release/focus-release.apk "$APK"
SHA=$(shasum -a 256 "$APK" | cut -d' ' -f1)
SIZE=$(stat -f%z "$APK" 2>/dev/null || stat -c%s "$APK")
URL="https://github.com/$REPO/releases/download/$TAG/AdWizzReelsBlocker.apk"

CODE="$CODE" URL="$URL" SHA="$SHA" SIZE="$SIZE" python3 - <<'PY'
import json, os
m = json.load(open("docs/update.json"))
m["focus"] = {"versionCode": int(os.environ["CODE"]), "apkUrl": os.environ["URL"],
              "sha256": os.environ["SHA"], "sizeBytes": int(os.environ["SIZE"])}
json.dump(m, open("docs/update.json", "w"), indent=2)
PY

git add docs/update.json
git commit -m "Release Reels Blocker add-on $NAME"
git tag -a "$TAG" -m "AdWizz Reels Blocker $NAME"
git push origin HEAD "$TAG"
# Not "latest": the site's download button must keep pointing at AdWizz itself.
gh release create "$TAG" "$APK" --repo "$REPO" --title "AdWizz Reels Blocker $NAME" --latest=false --notes \
  "Optional add-on for AdWizz's \"Block Reels & Shorts\" switch. You don't need to download this: AdWizz installs it for you when you turn the switch on.

**SHA-256:** \`$SHA\`"
echo "Published add-on $NAME. AdWizz will offer it once GitHub Pages refreshes docs/update.json."

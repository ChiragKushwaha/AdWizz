#!/usr/bin/env bash
# Publishes a new AdWizz version to GitHub Releases and tells every installed app about it.
#
#   1. Bump versionCode (+1) and versionName in app/build.gradle.kts
#   2. scripts/release.sh "What's new in one or two sentences" [release-notes.md]
#      The sentence is shown in the app's update panel/notification; the optional Markdown file
#      becomes the full GitHub release notes.
#
# It runs the tests, builds the signed APK (needs keystore.properties), writes docs/update.json
# (the manifest the app checks daily), commits + tags, pushes, and creates the GitHub release
# with AdWizz.apk attached. GitHub Pages then serves the new update.json within a minute or two,
# and installed apps offer the update (and notify in the background).
#
# Needs: the GitHub CLI, logged in once with `gh auth login`, and Node (for the website).
set -euo pipefail
cd "$(dirname "$0")/.."

NOTES="${1:-}"
NOTES_FILE="${2:-}"
[[ -z "$NOTES_FILE" || -f "$NOTES_FILE" ]] || { echo "notes file not found: $NOTES_FILE"; exit 2; }
[[ -n "$NOTES" ]] || { echo "usage: scripts/release.sh \"release notes\""; exit 2; }
command -v gh >/dev/null || { echo "Install the GitHub CLI first: brew install gh && gh auth login"; exit 2; }
gh auth status >/dev/null 2>&1 || { echo "Run: gh auth login"; exit 2; }
[[ -f keystore.properties ]] || { echo "keystore.properties missing: release must be signed with the AdWizz key"; exit 2; }
[[ -z "$(git status --porcelain)" ]] || { echo "Commit or stash your changes first."; exit 2; }

CODE=$(sed -nE 's/.*versionCode = ([0-9]+).*/\1/p' app/build.gradle.kts)
NAME=$(sed -nE 's/.*versionName = "([^"]+)".*/\1/p' app/build.gradle.kts)
TAG="v$NAME"
REPO="ChiragKushwaha/AdWizz"
git rev-parse "$TAG" >/dev/null 2>&1 && { echo "Tag $TAG already exists: bump versionName/versionCode first."; exit 2; }
if [[ -f docs/update.json ]]; then
  LAST=$(python3 -c 'import json;print(json.load(open("docs/update.json"))["versionCode"])')
  (( CODE > LAST )) || { echo "versionCode $CODE must be higher than the published $LAST."; exit 2; }
fi

export JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
./gradlew --quiet testDebugUnitTest lintDebug assembleRelease

mkdir -p build/release
APK=build/release/AdWizz.apk
cp app/build/outputs/apk/release/app-release.apk "$APK"
SHA=$(shasum -a 256 "$APK" | cut -d' ' -f1)
SIZE=$(stat -f%z "$APK" 2>/dev/null || stat -c%s "$APK")
URL="https://github.com/$REPO/releases/download/$TAG/AdWizz.apk"

NOTES="$NOTES" CODE="$CODE" NAME="$NAME" URL="$URL" SHA="$SHA" SIZE="$SIZE" python3 - <<'EOF'
import json, os
# Keep other entries (e.g. "focus", the Reels Blocker add-on from scripts/release-addon.sh).
try: m = json.load(open("docs/update.json"))
except FileNotFoundError: m = {}
m.update({
    "versionCode": int(os.environ["CODE"]), "versionName": os.environ["NAME"],
    "apkUrl": os.environ["URL"], "sha256": os.environ["SHA"],
    "sizeBytes": int(os.environ["SIZE"]), "notes": os.environ["NOTES"],
})
json.dump(m, open("docs/update.json", "w"), indent=2)
EOF

# The website shows the version (structured data): rebuild every language's page.
node scripts/build-site.mjs
git add docs
git commit -m "Release $TAG"
git tag -a "$TAG" -m "AdWizz $TAG"
git push origin HEAD "$TAG"
{
  if [[ -n "$NOTES_FILE" ]]; then cat "$NOTES_FILE"; else printf '%s\n' "$NOTES"; fi
  printf '\n---\n**Download:** `AdWizz.apk` below (%s KB) · Android 7.0+\n\n**SHA-256:** `%s`\n' "$((SIZE / 1024))" "$SHA"
} > build/release/notes.md
gh release create "$TAG" "$APK" --repo "$REPO" --title "AdWizz $TAG" --notes-file build/release/notes.md --latest
echo "Published $TAG ($SIZE bytes). Apps will see it once GitHub Pages refreshes docs/update.json."

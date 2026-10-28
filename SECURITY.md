# Security policy

AdWizz sits between every app on your phone and the internet's address book (DNS), installs its own
updates, and can install a companion app. A flaw in any of that matters, so security reports are
taken seriously and handled privately.

## Supported versions

| Component | Supported |
|---|---|
| AdWizz app, latest release ([Releases](https://github.com/ChiragKushwaha/AdWizz/releases/latest)) | ✅ |
| AdWizz Reels Blocker add-on, latest `reels-blocker-v*` release | ✅ |
| Older releases | ❌ Installed apps update themselves; please check the issue still happens on the latest version |
| Debug builds (`rocks.chirag.adwizz.debug`) | ❌ For development only |

## Reporting a vulnerability

**Please don't open a public issue for security problems.**

Report it privately through GitHub: **[Report a vulnerability](https://github.com/ChiragKushwaha/AdWizz/security/advisories/new)**
(the repository's *Security* tab → *Report a vulnerability*). Only the maintainer can see it.

Helpful to include:

- what an attacker can do, and what they need (another app on the phone, the same Wi-Fi, a malicious list mirror…)
- steps to reproduce, or a proof of concept
- AdWizz version (Settings → Updates), Android version and phone model
- logs or screenshots, with any personal data removed

What happens next:

| | Target |
|---|---|
| Acknowledgement | within 7 days |
| First assessment (accepted / not a vulnerability / need more info) | within 14 days |
| Fix released | within 30 days for high and critical issues, 90 days for the rest |

You'll be kept updated, and once a fix has shipped (and installed apps have had a few days to update)
the advisory is published with credit to you, unless you'd rather stay anonymous. Please keep the
details private until then. AdWizz is a free, unpaid project, so there is no bug bounty.

## Scope

**In scope**

- **The app:** the VPN / DNS filtering (`vpn/`, `core/`), anything that leaks DNS look-ups, browsing data
  or the Recent activity list off the phone, crashes another app can trigger remotely (e.g. with crafted
  DNS packets), exported components another app can abuse.
- **Updates:** anything that gets an APK installed that wasn't built and signed by AdWizz — the
  update manifest (`docs/update.json`), downloads, SHA-256 or signing-key checks, `PackageInstaller` use.
- **The Reels Blocker add-on** and its link to AdWizz (the signature-only `FOCUS_STATE` permission and
  provider), including the add-on's Accessibility service reading more than it should.
- **The Devices setup page:** the local server, its secret link, and the Apple DNS profile it serves.
- **The website** (`docs/`, GitHub Pages): script injection or anything that changes the download link.

**Out of scope** (open a normal issue instead)

- An ad, tracker or site that isn't blocked, or a site that's blocked by mistake. The lists come from
  their authors (StevenBlack, AdGuard, HaGeZi, OISD, …); wrong entries are best reported to them too.
- Known limits of DNS blocking: ads served from the same servers as the content (YouTube, Instagram
  feed ads), apps that bypass the VPN on purpose, Private DNS set to a custom hostname.
- Play Protect or browser warnings about installing an APK from outside the Play Store.
- Attacks that need a rooted phone, an unlocked phone in the attacker's hands, or a compromised Android.
- Denial of service against GitHub, GitHub Pages or the public blocklist mirrors.
- Reports from automated scanners without a demonstrated impact.

## Safe harbor

Good-faith research is welcome. If you stay within this policy — test only on devices and accounts you
own, don't access other people's data, don't degrade the service for others, and give a reasonable
time to fix before disclosing — no legal action will be taken or supported against you.

## How AdWizz protects you

- **Your data stays on the phone.** DNS look-ups are answered on the device; blocked names never leave
  it, and the rest go only to the DNS server you chose. No account, no AdWizz servers. Firebase gets
  anonymous usage and crash data only, never sites or apps, with the advertising ID turned off.
- **Only DNS is routed into the VPN.** Browsing, video and downloads use the normal network directly;
  malformed packets from any app are dropped, never allowed to take the VPN down (fuzz-tested).
- **Updates can't be swapped.** The update manifest is only accepted from this repository's GitHub
  Pages over HTTPS, may only point at this repository's release assets, and every APK must match its
  published SHA-256 **and** be signed with AdWizz's own key before it's installed. Android enforces the
  same key for updates.
- **The add-on is locked to AdWizz.** It's downloaded and verified the same way, and the switch it reads
  is behind a `signature`-level permission: only apps signed with AdWizz's key can read it.
- **The setup page is short-lived and private.** It listens only on the hotspot / Wi-Fi address (never
  mobile data), only while the Devices screen is open, behind a random 128-bit link that expires after
  15 minutes; every other path returns 404.

## Verifying a download

Every release lists the APK's SHA-256. Releases are signed with this certificate:

```
SHA-256: 70:FB:B2:60:50:4A:07:50:F1:C7:61:23:94:F9:BA:56:0F:75:26:3D:66:3F:0E:A7:04:7F:94:16:AD:A9:10:E2
```

Check an APK with Android's build tools:

```bash
apksigner verify --print-certs AdWizz.apk     # "Signer #1 certificate SHA-256 digest: 70fbb260…10e2"
shasum -a 256 AdWizz.apk                      # compare with the release notes
```

An APK signed with any other certificate is not from this project.

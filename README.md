# AdWizz — system-wide ad blocker for Android (local VPN)

Blocks ads and trackers in **every app** on the phone, without root, by running a local
`VpnService` that filters DNS on-device. No traffic leaves the phone through a remote server.

## How it works

```
 any app ──DNS query──► 10.111.222.1 (fake DNS, routed into the TUN)
                              │
                     AdBlockVpnService reads packet
                              │
             ┌── domain on blocklist? ──┐
            yes                          no
             │                            │
   answer 0.0.0.0 / ::            forward via protected socket
   instantly (ad never loads)     to 1.1.1.1 (or your choice) → reply
```

- Only the fake DNS address (plus well-known public resolvers, if "Block DNS bypass" is on) is
  routed into the tunnel. All other traffic goes over the normal network at full speed and
  battery cost stays near zero.
- **Block DNS bypass**: apps that hard-code 8.8.8.8 / 1.1.1.1 / Quad9 / etc. are also caught.
  Plain DNS to them is filtered; DNS-over-TLS/HTTPS to them gets an instant ICMP "prohibited",
  so the app falls back to plain DNS.
- Blocklists: a bundled starter list (works offline), plus StevenBlack Unified, AdGuard DNS,
  HaGeZi Pro and HaGeZi Pop-Up Ads (redirect / pop-under networks). Missing lists download on
  start and all lists refresh every 2 days while the VPN runs.
- **CNAME cloaking**: if an allowed-looking domain is just an alias for a blocked ad domain, it's
  blocked too.
- **Browser DoH bypass**: Firefox's DoH canary gets NXDOMAIN, and well-known DoH/DoT endpoints
  (Google, Cloudflare, Quad9, NextDNS, Control D, Mullvad, …) are refused so browsers fall back.
- **Block trackers & analytics scripts** (EasyPrivacy, hidden first-party trackers, phone-maker
  telemetry) and **Block malicious & crypto-mining scripts** (Phishing Army, NoCoin) — both on by default.
- **Excluded apps**: picked apps skip the VPN entirely, for apps that refuse to run when a VPN is on.
- Light / Dark / System theme.
- Allowlist always wins over blocklists. Tap any entry in the Activity tab to allow/block it.
- Quick Settings tile, foreground notification with "Turn off", Always-on VPN support.

## Look & feel

Pixel-art design (`design/image.png`): sky, drifting clouds, castle and pine forest, chunky maroon
buttons with hard shadows, Pixelify Sans font (OFL, subset to Latin). Light = daytime sky,
dark = night sky with moon and stars. Tapping **Cast spell** makes the wizard cast
"Wingardium Leviosa!" and the ads on the monitor float off into the sky.

- `scripts/make_wizard.py` generates the wizard sprites, launcher icon (adaptive + PNG) and
  notification icon from one definition.
- `scripts/extract_design_assets.py` lifts the bat and heart sprites from the design image.
  The design's boy wizard / owl / golden ball are Harry Potter IP and are not used.
- The spell text is a Harry Potter phrase; swap `SPELL` in `ui/WizardStage.kt` for an original one
  before a Play Store release.

## Testing

- `./gradlew testDebugUnitTest` — 170 JVM + Robolectric tests (core, data, share server, every UI tab,
  theme reveal, wizard stage, canyoublockit.com regression).
- `./gradlew testDebugUnitTest -Plive=true` — also checks the live canyoublockit.com pages against the
  live blocklists.
- `scripts/device-adblock-test.sh` — on a connected phone with a debug build running: opens the test
  site in Chrome and verifies every ad host was blocked and no pop-under opened.

## Build & install

1. Open the project folder in Android Studio (Ladybug or newer). Let Gradle sync.
2. Plug in a phone with USB debugging, press **Run**.
   Or from a terminal: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`.
3. Open the app, tap the shield, accept the VPN prompt.

`assembleRelease` also works out of the box (signed with the debug key — set your own
`signingConfig` before publishing).

## Phone settings that matter

- **Private DNS** (Settings → Network & internet → Private DNS) must be **Off** or **Automatic**.
  A specific hostname makes Android bypass any VPN's DNS.
- **Always-on VPN** (Settings → Network & internet → VPN → ⚙ AdShield) keeps protection across
  reboots. Enable "Block connections without VPN" only if you want zero leaks.
- Android allows one VPN at a time — AdShield can't run alongside another VPN app.

## Limits (true of every DNS-level blocker)

- Ads served from the same domain as content can't be separated by DNS — notably **YouTube**
  and Instagram/Facebook in-feed ads. Those need in-app or browser-level blocking.
- **Hotspot clients aren't filtered.** Android's VpnService only covers apps on the phone itself;
  tethered devices' traffic is forwarded by the system and never enters the VPN. Give those
  devices an ad-blocking DNS directly (e.g. Private DNS `dns.adguard-dns.com`).
- Apps can always tell a VPN is running (Android reports it to them). Use Excluded apps instead.
- Chrome's "Use secure DNS" with a custom provider bypasses system DNS; leave it on the
  default ("With your current service provider").

## Code map

| Path | What it does |
| --- | --- |
| `core/IpPacket.java` | IPv4/UDP parse & build, checksums, ICMP unreachable |
| `core/DnsMessage.java` | Parse query name/type, synthesise blocked & SERVFAIL replies |
| `core/DomainMatcher.java` | Hosts/domain/Adblock list parsing; parent-domain matching; allowlist |
| `vpn/AdBlockVpnService.kt` | The VPN: TUN setup, read loop, filtering, upstream forwarding |
| `vpn/ToggleTileService.kt` | Quick Settings tile |
| `data/` | Settings, blocklist download/loading, observable state |
| `ui/` | Jetpack Compose UI |

The `core` package is plain Java with no Android dependencies; `./gradlew test` runs its unit tests.

package rocks.chirag.adshield.share

/**
 * What other devices (on this phone's hotspot or the same Wi-Fi) are served to set up ad blocking.
 *
 * Without root, AdShield can't run a DNS server those devices could use (port 53 needs root), and
 * hotspot traffic never enters an Android VPN. So other devices are pointed at AdGuard's public
 * ad-blocking DNS instead. Pure Kotlin so it can be unit-tested on the JVM.
 */
object ShareContent {
    const val DOH_URL = "https://dns.adguard-dns.com/dns-query"
    const val DOT_HOST = "dns.adguard-dns.com"
    val DNS4 = listOf("94.140.14.14", "94.140.15.15")
    val DNS6 = listOf("2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff")

    const val PROFILE_PATH = "/adshield.mobileconfig"

    enum class Platform { IOS, MAC, ANDROID, WINDOWS, OTHER }

    data class Response(val status: Int, val contentType: String, val body: ByteArray, val extraHeaders: Map<String, String> = emptyMap())

    fun detect(userAgent: String?): Platform {
        val ua = userAgent.orEmpty()
        return when {
            ua.contains("iPhone") || ua.contains("iPad") || ua.contains("iPod") -> Platform.IOS
            ua.contains("Android") -> Platform.ANDROID
            // iPadOS Safari reports itself as a Mac; a Mac profile works there too.
            ua.contains("Macintosh") || ua.contains("Mac OS X") -> Platform.MAC
            ua.contains("Windows") -> Platform.WINDOWS
            else -> Platform.OTHER
        }
    }

    /** Request routing for the local setup server. */
    fun route(method: String, path: String, userAgent: String?): Response {
        if (method != "GET" && method != "HEAD") return text(405, "Method not allowed")
        return when (path.substringBefore('?')) {
            "/", "/index.html" -> Response(200, "text/html; charset=utf-8", indexHtml(detect(userAgent)).toByteArray())
            PROFILE_PATH -> Response(
                200, "application/x-apple-aspen-config", mobileConfig().toByteArray(),
                mapOf("Content-Disposition" to "attachment; filename=\"adshield.mobileconfig\"")
            )
            else -> text(404, "Not found")
        }
    }

    private fun text(status: Int, msg: String) = Response(status, "text/plain; charset=utf-8", msg.toByteArray())

    /**
     * Apple configuration profile (iOS 14+, macOS 11+) that turns on encrypted DNS through AdGuard.
     * Unsigned, so the device shows "Not verified" — that's normal for profiles not from a CA.
     */
    fun mobileConfig(): String {
        val addrs = (DNS4 + DNS6).joinToString("") { "<string>$it</string>" }
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>PayloadContent</key>
  <array>
    <dict>
      <key>DNSSettings</key>
      <dict>
        <key>DNSProtocol</key><string>HTTPS</string>
        <key>ServerURL</key><string>$DOH_URL</string>
        <key>ServerAddresses</key><array>$addrs</array>
      </dict>
      <key>PayloadDisplayName</key><string>AdWizz DNS (AdGuard)</string>
      <key>PayloadIdentifier</key><string>rocks.chirag.adshield.dns.settings</string>
      <key>PayloadType</key><string>com.apple.dnsSettings.managed</string>
      <key>PayloadUUID</key><string>6F0B6D0E-3C0B-4C55-9D0E-5A1E8C9B2A11</string>
      <key>PayloadVersion</key><integer>1</integer>
    </dict>
  </array>
  <key>PayloadDescription</key><string>Blocks ads and trackers by using AdGuard's encrypted ad-blocking DNS. Remove it any time in Settings.</string>
  <key>PayloadDisplayName</key><string>AdWizz ad blocking</string>
  <key>PayloadIdentifier</key><string>rocks.chirag.adshield.dns</string>
  <key>PayloadRemovalDisallowed</key><false/>
  <key>PayloadType</key><string>Configuration</string>
  <key>PayloadUUID</key><string>A3C1E2B4-7D5F-4E8A-9B6C-1D2E3F4A5B6C</string>
  <key>PayloadVersion</key><integer>1</integer>
</dict>
</plist>
"""
    }

    /** Mobile-friendly setup page; the visitor's own platform is shown first. */
    fun indexHtml(platform: Platform): String {
        val apple = """
<section id="apple">
  <h2>iPhone, iPad or Mac</h2>
  <a class="btn" href="${PROFILE_PATH.removePrefix("/")}">Download setup profile</a>
  <ol>
    <li>Tap <b>Allow</b> to download the profile.</li>
    <li>iPhone/iPad: open <b>Settings → Profile Downloaded → Install</b>.<br>
        Mac: open <b>System Settings → Privacy &amp; Security → Profiles</b> and install it.</li>
    <li>Done. Remove it any time from the same place.</li>
  </ol>
</section>"""
        val android = """
<section id="android">
  <h2>Android</h2>
  <p>Android doesn't let a link change DNS, but it's one setting:</p>
  <ol>
    <li>Open <b>Settings → Network &amp; internet → Private DNS</b><br>
        (Samsung: <b>Connections → More connection settings → Private DNS</b>).</li>
    <li>Choose <b>Private DNS provider hostname</b> and enter:</li>
  </ol>
  <div class="copy"><code id="host">$DOT_HOST</code>
    <button onclick="navigator.clipboard&amp;&amp;navigator.clipboard.writeText('$DOT_HOST');this.textContent='Copied'">Copy</button></div>
</section>"""
        val windows = """
<section id="windows">
  <h2>Windows</h2>
  <ol>
    <li>Open <b>Settings → Network &amp; internet → Wi-Fi → Hardware properties</b>.</li>
    <li>Next to <b>DNS server assignment</b> tap <b>Edit → Manual</b>, turn on IPv4.</li>
    <li>Preferred DNS <code>${DNS4[0]}</code>, alternate <code>${DNS4[1]}</code>.
        Set <b>DNS over HTTPS</b> to <b>On (manual template)</b> with <code>$DOH_URL</code>.</li>
  </ol>
</section>"""
        val other = """
<section id="other">
  <h2>Anything else (Linux, consoles, TVs)</h2>
  <p>Set the DNS servers to <code>${DNS4[0]}</code> and <code>${DNS4[1]}</code>.</p>
</section>"""
        val ordered = when (platform) {
            Platform.IOS, Platform.MAC -> listOf(apple, android, windows, other)
            Platform.ANDROID -> listOf(android, apple, windows, other)
            Platform.WINDOWS -> listOf(windows, apple, android, other)
            Platform.OTHER -> listOf(apple, android, windows, other)
        }
        return """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>AdWizz setup</title>
<style>
:root{--bg:#f7faf9;--fg:#111827;--muted:#4b5563;--card:#fff;--accent:#0f766e;--line:#d1d5db}
@media (prefers-color-scheme:dark){:root{--bg:#0b1211;--fg:#e5e7eb;--muted:#9ca3af;--card:#141d1c;--accent:#5eead4;--line:#374151}}
body{margin:0;padding:16px;font:16px/1.5 system-ui,sans-serif;background:var(--bg);color:var(--fg)}
main{max-width:560px;margin:auto}
h1{font-size:22px}h2{font-size:18px;margin:0 0 8px}
section{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:16px;margin:12px 0}
.btn{display:inline-block;background:var(--accent);color:var(--bg);padding:12px 18px;border-radius:10px;text-decoration:none;font-weight:600}
code{background:var(--bg);padding:2px 6px;border-radius:6px;word-break:break-all}
.copy{display:flex;gap:8px;align-items:center}
button{border:1px solid var(--line);background:var(--card);color:var(--fg);border-radius:8px;padding:6px 12px}
p.note{color:var(--muted);font-size:14px}
</style></head>
<body><main>
<h1>Block ads on this device</h1>
<p class="note">Shared from AdWizz. This device will use AdGuard's free, encrypted ad-blocking DNS.
It keeps working after you leave this hotspot or Wi-Fi.</p>
${ordered.joinToString("\n")}
</main></body></html>
"""
    }
}

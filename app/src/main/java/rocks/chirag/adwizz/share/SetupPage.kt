package rocks.chirag.adwizz.share

/** Mobile-friendly setup page served to other devices; the visitor's platform is shown first. */
internal object SetupPage {
    /** Mobile-friendly setup page; the visitor's own platform is shown first. */
    fun html(platform: ShareContent.Platform): String {
        val apple = """
<section id="apple">
  <h2>iPhone, iPad or Mac</h2>
  <a class="btn" href="${ShareContent.PROFILE_PATH.removePrefix("/")}">Download setup profile</a>
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
  <div class="copy"><code id="host">${ShareContent.DOT_HOST}</code>
    <button onclick="navigator.clipboard&amp;&amp;navigator.clipboard.writeText('${ShareContent.DOT_HOST}');this.textContent='Copied'">Copy</button></div>
</section>"""
        val windows = """
<section id="windows">
  <h2>Windows</h2>
  <ol>
    <li>Open <b>Settings → Network &amp; internet → Wi-Fi → Hardware properties</b>.</li>
    <li>Next to <b>DNS server assignment</b> tap <b>Edit → Manual</b>, turn on IPv4.</li>
    <li>Preferred DNS <code>${ShareContent.DNS4[0]}</code>, alternate <code>${ShareContent.DNS4[1]}</code>.
        Set <b>DNS over HTTPS</b> to <b>On (manual template)</b> with <code>${ShareContent.DOH_URL}</code>.</li>
  </ol>
</section>"""
        val other = """
<section id="other">
  <h2>Anything else (Linux, consoles, TVs)</h2>
  <p>Set the DNS servers to <code>${ShareContent.DNS4[0]}</code> and <code>${ShareContent.DNS4[1]}</code>.</p>
</section>"""
        val ordered = when (platform) {
            ShareContent.Platform.IOS, ShareContent.Platform.MAC -> listOf(apple, android, windows, other)
            ShareContent.Platform.ANDROID -> listOf(android, apple, windows, other)
            ShareContent.Platform.WINDOWS -> listOf(windows, apple, android, other)
            ShareContent.Platform.OTHER -> listOf(apple, android, windows, other)
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

package rocks.chirag.adwizz.share

/** Apple configuration profile that turns on AdGuard's encrypted DNS on an iPhone, iPad or Mac. */
internal object MobileConfig {
    /**
     * Apple configuration profile (iOS 14+, macOS 11+) that turns on encrypted DNS through AdGuard.
     * Unsigned, so the device shows "Not verified" — that's normal for profiles not from a CA.
     * PayloadScope System: macOS 26.1+ refuses user-scoped DNS profiles with "The VPN service could
     * not be created" (DNS settings and VPNs share NetworkExtension). iOS ignores the key.
     */
    fun xml(): String {
        val addrs = (ShareContent.DNS4 + ShareContent.DNS6).joinToString("") { "<string>$it</string>" }
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
        <key>ServerURL</key><string>${ShareContent.DOH_URL}</string>
        <key>ServerAddresses</key><array>$addrs</array>
      </dict>
      <key>PayloadDisplayName</key><string>AdWizz DNS (AdGuard)</string>
      <key>PayloadIdentifier</key><string>rocks.chirag.adwizz.dns.settings</string>
      <key>PayloadType</key><string>com.apple.dnsSettings.managed</string>
      <key>PayloadUUID</key><string>6F0B6D0E-3C0B-4C55-9D0E-5A1E8C9B2A11</string>
      <key>PayloadVersion</key><integer>1</integer>
    </dict>
  </array>
  <key>PayloadDescription</key><string>Blocks ads and trackers by using AdGuard's encrypted ad-blocking DNS. Remove it any time in Settings.</string>
  <key>PayloadDisplayName</key><string>AdWizz ad blocking</string>
  <key>PayloadIdentifier</key><string>rocks.chirag.adwizz.dns</string>
  <key>PayloadRemovalDisallowed</key><false/>
  <key>PayloadScope</key><string>System</string>
  <key>PayloadType</key><string>Configuration</string>
  <key>PayloadUUID</key><string>A3C1E2B4-7D5F-4E8A-9B6C-1D2E3F4A5B6C</string>
  <key>PayloadVersion</key><integer>1</integer>
</dict>
</plist>
"""
    }
}

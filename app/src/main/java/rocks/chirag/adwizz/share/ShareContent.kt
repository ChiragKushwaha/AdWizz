package rocks.chirag.adwizz.share

/**
 * What other devices (on this phone's hotspot or the same Wi-Fi) are served to set up ad blocking.
 *
 * Without root, AdWizz can't run a DNS server those devices could use (port 53 needs root), and
 * hotspot traffic never enters an Android VPN. So other devices are pointed at AdGuard's public
 * ad-blocking DNS instead. Pure Kotlin so it can be unit-tested on the JVM.
 */
object ShareContent {
    const val DOH_URL = "https://dns.adguard-dns.com/dns-query"
    const val DOT_HOST = "dns.adguard-dns.com"
    val DNS4 = listOf("94.140.14.14", "94.140.15.15")
    val DNS6 = listOf("2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff")

    const val PROFILE_PATH = "/adwizz.mobileconfig"

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
                mapOf("Content-Disposition" to "attachment; filename=\"adwizz.mobileconfig\"")
            )
            else -> text(404, "Not found")
        }
    }

    private fun text(status: Int, msg: String) = Response(status, "text/plain; charset=utf-8", msg.toByteArray())

    /** Apple configuration profile; see [MobileConfig]. */
    fun mobileConfig(): String = MobileConfig.xml()

    /** Setup page for [platform]; see [SetupPage]. */
    fun indexHtml(platform: Platform): String = SetupPage.html(platform)
}

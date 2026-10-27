package rocks.chirag.adwizz.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/**
 * Every packet the VPN reads comes from some app on the phone, so a hostile app can send anything.
 * None of the parsers may throw on garbage (that used to be able to crash the whole VPN).
 */
class SecurityFuzzTest {
    private val rnd = Random(42)

    private fun randomBytes(max: Int) = ByteArray(rnd.nextInt(max + 1)).also { rnd.nextBytes(it) }

    /** Valid packet with a few random bytes flipped: reaches much deeper into the parsers than pure noise. */
    private fun mutated(base: ByteArray): ByteArray = base.copyOf().also { b ->
        repeat(1 + rnd.nextInt(4)) { if (b.isNotEmpty()) b[rnd.nextInt(b.size)] = rnd.nextInt(256).toByte() }
    }

    private val validDns = DnsFixtures.sampleQuery("ads.example.com", 1)
    private val validV4 = IpBuilder.udp(byteArrayOf(10, 111, 222.toByte(), 2), byteArrayOf(10, 111, 222.toByte(), 1), 4000, 53, validDns)
    private val validV6 = IpBuilder.udp(ByteArray(16).also { it[0] = 0xfd.toByte() }, ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 1 }, 4000, 53, validDns)

    @Test fun ipParserNeverThrows() {
        repeat(50_000) {
            val b = when (it % 3) { 0 -> randomBytes(80); 1 -> mutated(validV4); else -> mutated(validV6) }
            val len = if (b.isEmpty()) 0 else rnd.nextInt(b.size + 1)
            val p = IpPacket.parse(b, len) ?: return@repeat
            // Whatever parsed must also be safe to answer.
            if (p.protocol != IpPacket.PROTO_UDP) IpBuilder.icmpUnreachable(b.copyOf(len), len)
        }
    }

    @Test fun dnsParsersNeverThrow() {
        repeat(50_000) {
            val b = if (it % 2 == 0) randomBytes(120) else mutated(validDns)
            DnsResponse.cnameTargets(b)
            DnsResponse.isResponseTo(validDns, b)
            val q = DnsMessage.parseQuery(b) ?: return@repeat
            DnsMessage.blockedResponse(b, q)
            DnsMessage.servFail(b, q)
            DnsMessage.nxDomain(b, q)
        }
    }

    @Test fun matcherHandlesHostileNames() {
        val m = DomainMatcher(setOf("ads.com"), emptySet()).withGeneratedDomainBlocking(true)
        listOf("", ".", "..", ".ads.com", "ads.com.", "\u0000ads.com", "a".repeat(10_000), "ü.ads.com", "...ads.com")
            .forEach { m.isBlocked(it); m.isAllowed(it) }
        repeat(20_000) { m.isBlocked(String(CharArray(rnd.nextInt(300)) { (rnd.nextInt(256)).toChar() })) }
    }

    @Test fun onlyTheMatchingAnswerIsAccepted() {
        val q = validDns
        val answer = DnsMessage.blockedResponse(q, DnsMessage.parseQuery(q)!!)
        assertTrue(DnsResponse.isResponseTo(q, answer))
        val wrongId = answer.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFalse("spoofed answer with another ID", DnsResponse.isResponseTo(q, wrongId))
        assertFalse("a query is not an answer", DnsResponse.isResponseTo(q, q))
        assertFalse(DnsResponse.isResponseTo(q, ByteArray(5)))
        assertFalse(DnsResponse.isResponseTo(null, answer))
    }
}

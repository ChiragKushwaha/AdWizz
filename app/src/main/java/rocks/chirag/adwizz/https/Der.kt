package rocks.chirag.adwizz.https

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Just enough DER (the binary encoding of certificates) to build our own X.509 certificates. */
internal object Der {
    fun tlv(tag: Int, body: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(tag)
        val n = body.size
        when {
            n < 0x80 -> write(n)
            n < 0x100 -> { write(0x81); write(n) }
            n < 0x10000 -> { write(0x82); write(n shr 8); write(n and 0xFF) }
            else -> { write(0x83); write(n shr 16); write((n shr 8) and 0xFF); write(n and 0xFF) }
        }
        write(body)
    }.toByteArray()

    fun seq(vararg parts: ByteArray) = tlv(0x30, concat(parts))
    fun set(vararg parts: ByteArray) = tlv(0x31, concat(parts))
    fun int(v: BigInteger) = tlv(0x02, v.toByteArray())
    fun int(v: Long) = int(BigInteger.valueOf(v))
    fun bool(v: Boolean) = tlv(0x01, byteArrayOf(if (v) 0xFF.toByte() else 0))
    fun octets(b: ByteArray) = tlv(0x04, b)
    fun utf8(s: String) = tlv(0x0C, s.toByteArray(Charsets.UTF_8))
    /** BIT STRING with [unused] padding bits in the last byte. */
    fun bits(b: ByteArray, unused: Int = 0) = tlv(0x03, byteArrayOf(unused.toByte()) + b)
    /** Context-specific constructed tag [n] (EXPLICIT). */
    fun explicit(n: Int, body: ByteArray) = tlv(0xA0 or n, body)
    /** Context-specific primitive tag [n] (IMPLICIT), e.g. a dNSName in subjectAltName. */
    fun implicit(n: Int, body: ByteArray) = tlv(0x80 or n, body)

    fun oid(dotted: String): ByteArray {
        val p = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((p[0] * 40 + p[1]).toInt())
        for (v in p.drop(2)) {
            val groups = ArrayList<Int>()
            var x = v
            do { groups += (x and 0x7F).toInt(); x = x shr 7 } while (x > 0)
            for (i in groups.indices.reversed()) out.write(groups[i] or (if (i > 0) 0x80 else 0))
        }
        return tlv(0x06, out.toByteArray())
    }

    /** UTCTime until 2049, GeneralizedTime after (as X.509 requires). */
    fun time(d: Date): ByteArray {
        val utc = d.before(Date(2524608000000L))   // 2050-01-01
        val f = SimpleDateFormat(if (utc) "yyMMddHHmmss'Z'" else "yyyyMMddHHmmss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
        return tlv(if (utc) 0x17 else 0x18, f.format(d).toByteArray(Charsets.US_ASCII))
    }

    private fun concat(parts: Array<out ByteArray>) =
        ByteArrayOutputStream().apply { parts.forEach { write(it) } }.toByteArray()
}

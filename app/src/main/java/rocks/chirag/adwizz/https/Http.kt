package rocks.chirag.adwizz.https

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** One HTTP/1.1 request or response head: the first line plus headers, in order. */
internal class HttpHead(var line: String, val headers: MutableList<Pair<String, String>>) {
    operator fun get(name: String): String? = headers.firstOrNull { it.first.equals(name, true) }?.second
    fun set(name: String, value: String) { remove(name); headers += name to value }
    fun remove(name: String) { headers.removeAll { it.first.equals(name, true) } }

    fun write(out: OutputStream) {
        val sb = StringBuilder(line).append("\r\n")
        headers.forEach { (k, v) -> sb.append(k).append(": ").append(v).append("\r\n") }
        out.write(sb.append("\r\n").toString().toByteArray(Charsets.ISO_8859_1))
    }

    val isChunked get() = this["Transfer-Encoding"]?.contains("chunked", true) == true
    val contentLength get() = this["Content-Length"]?.trim()?.toLongOrNull()
    val wantsClose get() = this["Connection"]?.contains("close", true) == true
}

internal object Http {
    private const val MAX_HEAD = 64 * 1024

    /** Reads a head up to the blank line. Null at a clean end of stream (connection closed). */
    fun readHead(input: InputStream): HttpHead? {
        val buf = ByteArrayOutputStream()
        var matched = 0
        while (true) {
            val b = input.read()
            if (b < 0) { if (buf.size() == 0) return null else throw IOException("Truncated HTTP head") }
            buf.write(b)
            matched = when {
                b == '\r'.code && (matched == 0 || matched == 2) -> matched + 1
                b == '\n'.code && (matched == 1 || matched == 3) -> matched + 1
                else -> 0
            }
            if (matched == 4) break
            if (buf.size() > MAX_HEAD) throw IOException("HTTP head too large")
        }
        val lines = buf.toString(Charsets.ISO_8859_1.name()).split("\r\n").filter { it.isNotEmpty() }
        if (lines.isEmpty()) throw IOException("Empty HTTP head")
        val headers = lines.drop(1).mapNotNull { l ->
            val i = l.indexOf(':')
            if (i <= 0) null else l.substring(0, i).trim() to l.substring(i + 1).trim()
        }.toMutableList()
        return HttpHead(lines[0], headers)
    }

    /** True if a response to [method] with this status line carries a body at all. */
    fun responseHasBody(method: String, status: Int) =
        !method.equals("HEAD", true) && status !in 100..199 && status != 204 && status != 304

    /**
     * Copies one message body exactly as framed (Content-Length, chunked, or until close for a
     * response without either). Returns false if the connection must close afterwards.
     */
    fun copyBody(head: HttpHead, input: InputStream, out: OutputStream, isResponse: Boolean): Boolean {
        when {
            head.isChunked -> copyChunked(input, out)
            head.contentLength != null -> copyExactly(input, out, head.contentLength!!)
            isResponse -> { input.copyTo(out, 64 * 1024); return false }
        }
        out.flush()
        return true
    }

    /** Reads a whole body into memory (for responses AdWizz rewrites), with a size cap. */
    fun readBody(head: HttpHead, input: InputStream, isResponse: Boolean, max: Int): ByteArray {
        val out = CappedOutput(max)
        copyBody(head, input, out, isResponse)
        return out.toByteArray()
    }

    private fun copyExactly(input: InputStream, out: OutputStream, n: Long) {
        val buf = ByteArray(64 * 1024)
        var left = n
        while (left > 0) {
            val r = input.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (r < 0) throw IOException("Body ended early")
            out.write(buf, 0, r)
            left -= r
        }
    }

    /** Re-chunking isn't needed: copies the chunk framing through to a streaming peer, or decodes for buffering. */
    private fun copyChunked(input: InputStream, out: OutputStream) {
        val decoding = out is CappedOutput
        while (true) {
            val sizeLine = readLine(input)
            val size = sizeLine.substringBefore(';').trim().toLong(16)
            if (!decoding) out.write("$sizeLine\r\n".toByteArray(Charsets.ISO_8859_1))
            if (size == 0L) {
                // Trailers, then the final blank line.
                while (true) {
                    val t = readLine(input)
                    if (!decoding) out.write("$t\r\n".toByteArray(Charsets.ISO_8859_1))
                    if (t.isEmpty()) return
                }
            }
            copyExactly(input, out, size)
            readLine(input)   // CRLF after the chunk
            if (!decoding) out.write("\r\n".toByteArray())
        }
    }

    private fun readLine(input: InputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw IOException("Stream ended in a chunk header")
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
            if (sb.length > 8192) throw IOException("Chunk header too long")
        }
    }

    private class CappedOutput(private val max: Int) : ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (size() + len > max) throw IOException("Body too large to filter")
            super.write(b, off, len)
        }
    }
}

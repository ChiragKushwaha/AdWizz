package rocks.chirag.adwizz.share

import java.io.InputStream
import java.io.OutputStream

/** The few bits of HTTP/1.1 the setup server needs, with hard limits on what it will read. */
internal object HttpIo {
    const val MAX_LINE = 4096
    const val MAX_HEADERS = 64

    class Request(val method: String, val path: String, val userAgent: String?)

    /** Reads one CRLF/LF-terminated line of at most [MAX_LINE] bytes; null at EOF or if too long. */
    fun readLineLimited(input: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length >= MAX_LINE) return null
            sb.append(b.toChar())
        }
    }

    /** Request line + headers (only User-Agent is kept). Null if the client sent nothing usable. */
    fun readRequest(input: InputStream): Request? {
        val requestLine = readLineLimited(input) ?: return null
        var userAgent: String? = null
        for (i in 0 until MAX_HEADERS) {
            val line = readLineLimited(input) ?: break
            if (line.isEmpty()) break
            if (line.startsWith("User-Agent:", ignoreCase = true)) userAgent = line.substringAfter(':').trim().take(512)
        }
        val parts = requestLine.split(' ')
        return Request(parts.getOrElse(0) { "" }, parts.getOrElse(1) { "/" }, userAgent)
    }

    fun writeResponse(out: OutputStream, r: ShareContent.Response, includeBody: Boolean) {
        val head = buildString {
            append("HTTP/1.1 ${r.status} ${reason(r.status)}\r\n")
            append("Content-Type: ${r.contentType}\r\n")
            append("Content-Length: ${r.body.size}\r\n")
            append("Cache-Control: no-store\r\n")
            append("Connection: close\r\n")
            r.extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
            append("\r\n")
        }
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        if (includeBody) out.write(r.body)
        out.flush()
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error"
    }
}

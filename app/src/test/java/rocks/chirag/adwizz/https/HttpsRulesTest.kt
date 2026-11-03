package rocks.chirag.adwizz.https

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/** What decrypted traffic goes through: blocked ad URLs, YouTube ad data renamed away, pages cleaned. */
class HttpsRulesTest {
    @Test fun blocksYoutubeAdUrlsButNotVideos() {
        assertTrue(HttpsRules.isBlockedUrl("www.youtube.com", "/pagead/viewthroughconversion/123"))
        assertTrue(HttpsRules.isBlockedUrl("www.youtube.com", "/api/stats/ads?ver=2"))
        assertTrue(HttpsRules.isBlockedUrl("m.youtube.com", "/ptracking?pltype=adhost"))
        assertFalse(HttpsRules.isBlockedUrl("www.youtube.com", "/watch?v=abc"))
        assertFalse(HttpsRules.isBlockedUrl("www.youtube.com", "/youtubei/v1/player"))
        assertFalse(HttpsRules.isBlockedUrl("example.com", "/pagead/x"))
    }

    @Test fun youtubePlayerDataLosesItsAds() {
        val json = """{"playabilityStatus":{},"adPlacements":[{"x":1}],"playerAds":[1],"videoDetails":{"title":"t"}}"""
        val out = String(HttpsRules.rewrite("www.youtube.com", "application/json", json.toByteArray()))
        assertFalse(out.contains("\"adPlacements\""))
        assertFalse(out.contains("\"playerAds\""))
        assertTrue(out.contains("\"videoDetails\""))
        org.json.JSONObject(out)   // still valid JSON
    }

    @Test fun otherSitesJsonIsUntouched() {
        val json = """{"adPlacements":[1]}""".toByteArray()
        assertArrayEquals(json, HttpsRules.rewrite("example.com", "application/json", json))
    }

    @Test fun pagesGetTheHidingStyleInTheirHead() {
        val html = "<html><HEAD><title>x</title></HEAD><body>hi</body></html>".toByteArray()
        val out = String(HttpsRules.rewrite("news.example", "text/html; charset=utf-8", html))
        assertTrue(out.indexOf("<style id=\"adwizz\">") < out.indexOf("</HEAD>"))
        assertTrue(out.contains(".adsbygoogle"))
        assertTrue(String(HttpsRules.injectStyle("<p>no head</p>".toByteArray())).startsWith("<style"))
    }

    @Test fun decidesWhatToRewrite() {
        assertTrue(HttpsRules.wantsRewrite("www.youtube.com", "/youtubei/v1/player?key=1", "*/*", "empty"))
        assertTrue(HttpsRules.wantsRewrite("news.example", "/", "text/html,application/xhtml+xml", "document"))
        assertFalse(HttpsRules.wantsRewrite("news.example", "/app.js", "*/*", "script"))
        assertTrue(HttpsRules.canRewrite("application/json; charset=UTF-8"))
        assertFalse(HttpsRules.canRewrite("video/mp4"))
    }

    @Test fun gunzipAndReplace() {
        val gz = ByteArrayOutputStream().also { GZIPOutputStream(it).use { z -> z.write("hello".toByteArray()) } }.toByteArray()
        assertEquals("hello", String(HttpsRules.gunzip(gz)))
        assertEquals("a-b-a-b", String(HttpsRules.replace("a.b.a.b".toByteArray(), ".", "-")))
    }

    @Test fun onlyBrowsersAreDecrypted() {
        assertTrue(HttpsProxy.isBrowser("Mozilla/5.0 (Linux; Android 17; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"))
        assertFalse(HttpsProxy.isBrowser("Mozilla/5.0 (Linux; Android 17; wv) AppleWebKit/537.36 Chrome/141.0 Mobile Safari/537.36".replace("17; wv)", "17; SM; wv)")))
        assertFalse(HttpsProxy.isBrowser("com.google.android.youtube/21.43 (Linux; U; Android 17)"))
        assertFalse(HttpsProxy.isBrowser("okhttp/4.12.0"))
        assertFalse(HttpsProxy.isBrowser(""))
    }
}

/** HTTP/1.1 framing: heads, fixed-length and chunked bodies. */
class HttpFramingTest {
    @Test fun readsHeadAndChunkedBody() {
        val raw = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nContent-Type: text/html\r\n\r\n5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n"
        val input = ByteArrayInputStream(raw.toByteArray())
        val head = Http.readHead(input)!!
        assertEquals("HTTP/1.1 200 OK", head.line)
        assertEquals("text/html", head["content-type"])
        assertEquals("hello world", String(Http.readBody(head, input, isResponse = true, max = 1000)))
    }

    @Test fun streamsChunkedBodyWithItsFraming() {
        val body = "5\r\nhello\r\n0\r\n\r\n"
        val head = HttpHead("HTTP/1.1 200 OK", mutableListOf("Transfer-Encoding" to "chunked"))
        val out = ByteArrayOutputStream()
        assertTrue(Http.copyBody(head, ByteArrayInputStream(body.toByteArray()), out, isResponse = true))
        assertEquals(body, out.toString())
    }

    @Test fun fixedLengthAndHeadWriting() {
        val head = HttpHead("GET / HTTP/1.1", mutableListOf("Host" to "a", "Content-Length" to "3"))
        val out = ByteArrayOutputStream()
        Http.copyBody(head, ByteArrayInputStream("abcdef".toByteArray()), out, isResponse = false)
        assertEquals("abc", out.toString())
        head.set("Accept-Encoding", "gzip"); head.remove("host")
        val w = ByteArrayOutputStream().also { head.write(it) }.toString()
        assertEquals("GET / HTTP/1.1\r\nContent-Length: 3\r\nAccept-Encoding: gzip\r\n\r\n", w)
        assertNull(Http.readHead(ByteArrayInputStream(ByteArray(0))))
        assertFalse(Http.responseHasBody("HEAD", 200))
        assertFalse(Http.responseHasBody("GET", 304))
    }
}

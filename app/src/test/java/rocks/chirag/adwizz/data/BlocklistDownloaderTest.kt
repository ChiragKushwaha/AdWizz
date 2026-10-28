package rocks.chirag.adwizz.data

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.nio.file.Files
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Downloads with mirrors, size checks, and "not modified" answers that skip re-downloading. */
@RunWith(AndroidJUnit4::class)   // android.util.Log
@Config(sdk = [35])
class BlocklistDownloaderTest {
    private val body = (1..200).joinToString("\n") { "0.0.0.0 ad$it.example.com" }   // > 1 KB
    private val requests = mutableListOf<Map<String, String?>>()
    private lateinit var server: ServerSocket
    private lateinit var dir: File
    private val base get() = "http://127.0.0.1:${server.localPort}"

    /** Just enough HTTP/1.0 for the downloader: one request per connection. */
    private fun respond(path: String, headers: Map<String, String>): Triple<Int, Map<String, String>, ByteArray> = when (path) {
        "/list" -> if (headers["if-none-match"] == "\"v1\"") Triple(304, emptyMap(), ByteArray(0))
            else Triple(200, mapOf("ETag" to "\"v1\"", "Last-Modified" to "Wed, 28 Oct 2026 10:00:00 GMT"), body.toByteArray())
        "/tiny" -> Triple(200, emptyMap(), "0.0.0.0 a.com".toByteArray())
        else -> Triple(500, emptyMap(), ByteArray(0))
    }

    @Before fun start() {
        dir = Files.createTempDirectory("lists").toFile()
        server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val c = runCatching { server.accept() }.getOrNull() ?: break
                c.use {
                    val r = c.getInputStream().bufferedReader()
                    val path = r.readLine().split(' ')[1]
                    val headers = generateSequence { r.readLine()?.takeIf { it.isNotEmpty() } }
                        .associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
                    synchronized(requests) {
                        requests += mapOf("If-None-Match" to headers["if-none-match"], "If-Modified-Since" to headers["if-modified-since"])
                    }
                    val (code, extra, bytes) = respond(path, headers)
                    val head = buildString {
                        append("HTTP/1.0 $code X\r\nContent-Length: ${bytes.size}\r\n")
                        extra.forEach { (k, v) -> append("$k: $v\r\n") }
                        append("\r\n")
                    }
                    c.getOutputStream().apply { write(head.toByteArray()); write(bytes); flush() }
                }
            }
        }
    }

    @After fun stop() { server.close(); dir.deleteRecursively() }

    private fun source(vararg paths: String) = BlocklistSource("test", "Test", paths.map { base + it }, "")

    @Test fun downloadsAndRemembersTheVersion() {
        BlocklistDownloader(dir).download(source("/list"))
        assertEquals(body, File(dir, "test.txt").readText())
        assertEquals(listOf("\"v1\"", "Wed, 28 Oct 2026 10:00:00 GMT"), File(dir, "test.etag").readLines())
        assertNull(requests.single()["If-None-Match"])
    }

    @Test fun unchangedListsAreNotDownloadedAgain() {
        val d = BlocklistDownloader(dir)
        d.download(source("/list"))
        File(dir, "test.txt").setLastModified(0)
        d.download(source("/list"))
        assertEquals("\"v1\"", requests.last()["If-None-Match"])
        assertEquals("Wed, 28 Oct 2026 10:00:00 GMT", requests.last()["If-Modified-Since"])
        assertEquals(body, File(dir, "test.txt").readText())          // kept as it was
        assertEquals(0L, File(dir, "test.txt").lastModified())        // untouched: no database rebuild
    }

    @Test fun aMissingListIsFetchedInFullEvenWithAnOldTag() {
        val d = BlocklistDownloader(dir)
        d.download(source("/list"))
        File(dir, "test.txt").delete()
        d.download(source("/list"))
        assertNull(requests.last()["If-None-Match"])
        assertEquals(body, File(dir, "test.txt").readText())
    }

    @Test fun fallsBackToTheNextMirror() {
        BlocklistDownloader(dir).download(source("/broken", "/list"))
        assertEquals(body, File(dir, "test.txt").readText())
    }

    @Test fun rejectsSuspiciouslySmallLists() {
        assertThrows(IllegalStateException::class.java) { BlocklistDownloader(dir).download(source("/tiny")) }
        assertFalse(File(dir, "test.txt").exists())
    }

    @Test fun failsWhenEveryMirrorFails() {
        assertThrows(IllegalStateException::class.java) { BlocklistDownloader(dir).download(source("/broken")) }
    }
}

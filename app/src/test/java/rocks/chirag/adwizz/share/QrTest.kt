package rocks.chirag.adwizz.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.*
import org.junit.Test
import rocks.chirag.adwizz.ui.qrMatrix

class QrTest {
    private fun decode(text: String): String {
        val m = qrMatrix(text)
        val scale = 4; val margin = 4 * scale
        val w = m.width * scale + 2 * margin
        val pixels = IntArray(w * w) { 0xFFFFFF }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) {
            for (dy in 0 until scale) for (dx in 0 until scale)
                pixels[(margin + y * scale + dy) * w + margin + x * scale + dx] = 0
        }
        val bmp = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, w, pixels)))
        return QRCodeReader().decode(bmp).text
    }

    @Test fun qrRoundTripsTheShareUrl() {
        val url = "http://192.168.43.1:8080/"
        assertEquals(url, decode(url))
    }

    @Test fun qrHandlesLongUrls() {
        val url = "http://192.168.100.200:65535/" + "x".repeat(200)
        assertEquals(url, decode(url))
    }

    @Test fun qrIsSquareWithNoQuietZone() {
        val m = qrMatrix("http://10.0.0.1:8080/")
        assertEquals(m.width, m.height)
        assertTrue(m[0, 0]) // finder pattern starts at the very corner
    }
}

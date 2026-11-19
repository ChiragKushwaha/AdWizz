package rocks.chirag.adwizz.https

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only a real rejection of our certificate may stop decrypting a host; dropped sockets must not. */
class MitmSessionTest {
    @Test fun alertsAreRefusals() {
        assertTrue(MitmSession.isRefusal("Read error: ssl=0x7b: Failure in SSL library, usually a protocol error\nerror:10000416:SSL routines:OPENSSL_internal:SSLV3_ALERT_CERTIFICATE_UNKNOWN"))
        assertTrue(MitmSession.isRefusal("error:1000042e:SSL routines:OPENSSL_internal:TLSV1_ALERT_UNKNOWN_CA"))
        assertTrue(MitmSession.isRefusal("Received fatal alert: bad_certificate"))
    }

    @Test fun droppedConnectionsAreNot() {
        assertFalse(MitmSession.isRefusal("connection closed"))
        assertFalse(MitmSession.isRefusal("Read error: ssl=0x7b: I/O error during system call, Connection reset by peer"))
        assertFalse(MitmSession.isRefusal(null))
    }
}

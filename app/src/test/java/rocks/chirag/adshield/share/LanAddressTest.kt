package rocks.chirag.adshield.share

import org.junit.Assert.*
import org.junit.Test

class LanAddressTest {
    @Test fun prefersHotspotInterface() {
        val ip = LanAddress.pick(listOf("wlan0" to "192.168.1.20", "swlan0" to "192.168.43.1", "rmnet0" to "10.1.2.3"))
        assertEquals("192.168.43.1", ip)
        assertEquals("10.42.0.1", LanAddress.pick(listOf("wlan0" to "192.168.1.5", "ap0" to "10.42.0.1")))
        assertEquals("192.168.50.1", LanAddress.pick(listOf("wlan0" to "192.168.1.5", "wlan1" to "192.168.50.1")))
    }

    @Test fun fallsBackToWifi() {
        assertEquals("192.168.1.20", LanAddress.pick(listOf("rmnet_data0" to "10.5.5.5", "wlan0" to "192.168.1.20")))
    }

    @Test fun skipsVpnMobileAndPublicAddresses() {
        assertNull(LanAddress.pick(listOf(
            "tun0" to "10.111.222.2", "rmnet0" to "10.0.0.5", "ccmni0" to "10.0.0.6",
            "wlan0" to "8.8.8.8", "lo" to "127.0.0.1", "dummy0" to "192.168.9.9"
        )))
        // Our own tunnel address is skipped even if the interface is oddly named.
        assertNull(LanAddress.pick(listOf("eth9" to "10.111.222.2")))
        assertNull(LanAddress.pick(emptyList()))
    }

    @Test fun privateRanges() {
        listOf("10.0.0.1", "172.16.5.4", "172.31.255.1", "192.168.0.1").forEach { assertTrue(it, LanAddress.isPrivate(it)) }
        listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "fe80::1", "not-an-ip").forEach { assertFalse(it, LanAddress.isPrivate(it)) }
    }

    @Test fun urlFormat() {
        assertEquals("http://192.168.43.1:8080/abc/", LanAddress.url("192.168.43.1", 8080, "abc"))
    }

    @Test fun candidatesNeverThrow() {
        LanAddress.candidates().forEach { (name, ip) -> assertTrue(name.isNotEmpty() && ip.contains('.')) }
    }
}

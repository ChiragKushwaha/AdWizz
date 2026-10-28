package rocks.chirag.adwizz.data

import org.junit.Assert.*
import org.junit.Test

class SourcesTest {
    @Test fun idsAreUniqueAndFileSafe() {
        val ids = Sources.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach { assertTrue(it, it.matches(Regex("[a-z0-9_\\-]+"))) }
    }

    @Test fun everySourceHasHttpsUrlsAndText() {
        Sources.ALL.forEach { s ->
            assertTrue(s.id, s.urls.isNotEmpty())
            s.urls.forEach { assertTrue(it, it.startsWith("https://")) }
            assertTrue(s.name.isNotBlank())
            assertTrue(s.description.isNotBlank())
        }
    }

    @Test fun hageziUsesMirrorsNotJsdelivr() {
        // jsDelivr refuses the HaGeZi repo (package too large), so it must not be used.
        Sources.ALL.flatMap { it.urls }.forEach { assertFalse(it, it.contains("cdn.jsdelivr.net")) }
        Sources.ALL.filter { it.id.startsWith("hagezi") || it.id.startsWith("native_") }
            .forEach { assertTrue(it.id, it.urls.size >= 2) }
    }

    @Test fun defaultsAreAdListsThatExist() {
        val ads = Sources.ADS.map { it.id }.toSet()
        assertTrue(Sources.DEFAULT_ENABLED.isNotEmpty())
        assertTrue(ads.containsAll(Sources.DEFAULT_ENABLED))
        assertTrue(Sources.ADS.all { it.group == SourceGroup.ADS })
    }

    @Test fun usesSeveralStrongAdListsByDefault() {
        assertEquals(setOf("stevenblack", "adguard_dns", "hagezi_pro", "oisd_big", "adguard_mobile"), Sources.DEFAULT_ENABLED)
        assertTrue(Sources.ALL.single { it.id == "hagezi_pro" }.urls.all { it.endsWith("/pro.txt") })
        // Pro++ is offered but off: it blocks graph.instagram.com (see PopularAppsLiveTest).
        val plus = Sources.ALL.single { it.id == "hagezi_proplus" }
        assertTrue(plus.urls.all { it.endsWith("/pro.plus.txt") })
        assertTrue(plus.group == SourceGroup.ADS && plus.id !in Sources.DEFAULT_ENABLED)
        assertTrue(Sources.ALL.single { it.id == "adguard_mobile" }.urls.single().contains("filters/11.txt"))
    }

    @Test fun trackersIncludeThirdPartyAndPhoneMakerLists() {
        val trackers = Sources.ALL.filter { it.group == SourceGroup.TRACKERS }.map { it.id }
        assertTrue(trackers.containsAll(listOf("easyprivacy", "frogeye_firstparty", "frogeye_multiparty", "native_samsung", "native_xiaomi")))
    }

    @Test fun neverUsesListsThatBreakPopularApps() {
        // Perflyst's Android list blocks graph.instagram.com / mqtt-mini.facebook.com (breaks Instagram and Messenger);
        // "YouTube ads" lists block googlevideo.com hosts that also serve the videos.
        Sources.ALL.flatMap { it.urls }.forEach { url ->
            assertFalse(url, url.contains("Perflyst") || url.contains("youTube_ads") || url.contains("youtube", ignoreCase = true))
        }
    }

    @Test fun everyGroupHasLists() {
        SourceGroup.entries.forEach { g -> assertTrue(g.name, Sources.ALL.any { it.group == g }) }
    }

    @Test fun removedSourcesAreGone() {
        Sources.REMOVED.forEach { r -> assertTrue(Sources.ALL.none { it.id == r }) }
        assertFalse(Sources.DEFAULT_ENABLED.any { it in Sources.REMOVED })
    }

    @Test fun dnsPresetsAreValidIpv4() {
        DnsPresets.ALL.forEach { p ->
            listOf(p.primary, p.secondary).forEach { ip ->
                val parts = ip.split('.')
                assertEquals(4, parts.size)
                assertTrue(parts.all { it.toInt() in 0..255 })
            }
        }
    }
}

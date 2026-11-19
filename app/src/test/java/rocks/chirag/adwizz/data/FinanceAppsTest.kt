package rocks.chirag.adwizz.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import rocks.chirag.adwizz.core.DomainMatcher

/** Banking and payment apps must keep working: they're found, and payment servers are never blocked. */
class FinanceAppsTest {
    @Test fun knownAndNamedFinanceAppsAreFound() {
        listOf(
            "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
            "com.sbi.lotusintouch", "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "com.axis.mobile",
            "com.examplebank.mobile", "in.co.somebank.netbanking", "com.foo.upi", "com.paypal.android.p2pmobile",
            "com.amazonpay.wallet", "com.oplus.pay",
        ).forEach { assertTrue(it, FinanceApps.looksLikeFinance(it)) }
    }

    @Test fun ordinaryAppsAreStillFiltered() {
        listOf(
            "com.android.chrome", "com.google.android.youtube", "com.spotify.music", "com.instagram.android",
            "com.google.android.play.games", "com.netflix.mediaclient", "com.example.display", "com.replay.player",
            "com.whatsapp", "com.sec.android.app.sbrowser",
        ).forEach { assertFalse(it, FinanceApps.looksLikeFinance(it)) }
    }

    @Test fun coreSystemTapToPayHostsAreNotExcluded() {
        assertTrue(FinanceApps.isCoreSystem("com.google.android.gms"))
        assertTrue(FinanceApps.isCoreSystem("com.android.systemui.plugin.globalactions.wallet"))
        assertFalse(FinanceApps.isCoreSystem("com.google.android.apps.walletnfcrel"))
    }

    @Test fun paymentServersBeatTheBlocklists() {
        // Tracker lists carry ThreatMetrix etc.; a payment must still reach them.
        val blocked = setOf("online-metrix.net", "doubleclick.net", "sift.com", "razorpay.com")
        val m = DomainMatcher(blocked, FinanceApps.ESSENTIAL_DOMAINS.toSet())
        assertFalse(m.isBlocked("h.online-metrix.net"))
        assertFalse(m.isBlocked("cdn.sift.com"))
        assertFalse(m.isBlocked("checkout.razorpay.com"))
        assertTrue(m.isBlocked("ad.doubleclick.net"))
    }

    @Test fun essentialDomainsAreNotAdNetworks() {
        val ads = listOf("doubleclick", "googlesyndication", "adservice", "taboola", "outbrain", "criteo", "facebook")
        assertEquals(emptyList<String>(), FinanceApps.ESSENTIAL_DOMAINS.filter { d -> ads.any { it in d } })
    }
}

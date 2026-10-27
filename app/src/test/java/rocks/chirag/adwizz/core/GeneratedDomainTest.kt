package rocks.chirag.adwizz.core

import org.junit.Assert.*
import org.junit.Test

class GeneratedDomainTest {
    @Test fun catchesRotatingAdDomains() {
        listOf("www.pgjlctmswgnwf.com", "pgjlctmswgnwf.com", "cdn.x.qdlcqrmjjwx.com", "bcdfghjklmnp.co.uk")
            .forEach { assertTrue(it, GeneratedDomain.looksGenerated(it)) }
    }

    /** Real sites that looser rules wrongly flagged during tuning; these must never be blocked. */
    @Test fun leavesRealSitesAlone() {
        listOf(
            "letsencrypt.org", "crunchyrollcdn.com", "healthychildren.org", "simplypsychology.org",
            "smythstoys.com", "msftstatic.com", "skyscrapercity.com", "psychcentral.com", "plentysystems.com",
            "linksyssmartwifi.com", "d3cod80thn7qnd.cloudfront.net", "rbxcdn.com", "crwdcntrl.net",
            "google.com", "x.com", "bbc.co.uk", "9ffyu1g9c.com", "cdnhwctnm107.com", "",
        ).forEach { assertFalse(it, GeneratedDomain.looksGenerated(it)) }
        assertFalse(GeneratedDomain.looksGenerated(null))
    }

    @Test fun siteLabel() {
        assertEquals("example", GeneratedDomain.siteLabel("a.b.example.com"))
        assertEquals("example", GeneratedDomain.siteLabel("ads.example.co.uk"))
        assertEquals("example", GeneratedDomain.siteLabel("example.de"))
        assertNull(GeneratedDomain.siteLabel("localhost"))
    }

    @Test fun matcherUsesItOnlyWhenEnabledAndAllowlistWins() {
        val off = DomainMatcher(emptySet(), emptySet())
        assertFalse(off.isBlocked("www.pgjlctmswgnwf.com"))
        val on = DomainMatcher(emptySet(), setOf("pgjlctmswgnwf.com")).withGeneratedDomainBlocking(true)
        assertFalse(on.isBlocked("www.pgjlctmswgnwf.com")) // allowlisted
        assertTrue(DomainMatcher(emptySet(), emptySet()).withGeneratedDomainBlocking(true).isBlocked("www.pgjlctmswgnwf.com"))
    }
}

package rocks.chirag.adwizz.core

import org.junit.Assert.assertEquals
import org.junit.Test

/** blockedBy() says which list blocked a name, so the dashboard can tell ads from trackers. */
class DomainMatcherCategoryTest {
    private fun set(vararg d: String) = DomainSet().apply { d.forEach { add(it) } }
    private val m = DomainMatcher(arrayOf<DomainLookup>(set("mine.com"), set("ads.com"), set("track.net", "ads.com")), set("ok.ads.com"))
        .withGeneratedDomainBlocking(true)

    @Test fun firstMatchingListWins() {
        assertEquals(0, m.blockedBy("x.mine.com"))
        assertEquals(1, m.blockedBy("cdn.ads.com"))
        assertEquals(2, m.blockedBy("track.net"))
    }

    @Test fun allowlistAndGenerated() {
        assertEquals(DomainMatcher.NOT_BLOCKED, m.blockedBy("ok.ads.com"))
        assertEquals(DomainMatcher.NOT_BLOCKED, m.blockedBy("example.org"))
        assertEquals(DomainMatcher.GENERATED, m.blockedBy("pgjlctmswgnwf.com"))
    }
}

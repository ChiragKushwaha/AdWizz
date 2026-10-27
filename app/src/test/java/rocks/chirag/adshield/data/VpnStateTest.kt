package rocks.chirag.adshield.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class VpnStateTest {
    @Before fun reset() {
        VpnState.setStatus(Status.STOPPED)
        VpnState.resetSession()
    }

    @Test fun recordsCounts() {
        VpnState.record("a.com", true)
        VpnState.record("b.com", false)
        VpnState.record("c.com", true)
        assertEquals(3, VpnState.stats.value.queries)
        assertEquals(2, VpnState.stats.value.blocked)
        assertEquals(listOf("c.com", "b.com", "a.com"), VpnState.log.value.map { it.domain })
    }

    @Test fun collapsesImmediateRepeatsButStillCounts() {
        repeat(3) { VpnState.record("same.com", true) }
        assertEquals(1, VpnState.log.value.size)
        assertEquals(3, VpnState.stats.value.queries)
        VpnState.record("same.com", false) // different outcome: new row
        assertEquals(2, VpnState.log.value.size)
    }

    @Test fun logIsCapped() {
        repeat(400) { VpnState.record("d$it.com", false) }
        assertEquals(300, VpnState.log.value.size)
        assertEquals("d399.com", VpnState.log.value.first().domain)
    }

    @Test fun resetSessionClearsLogAndCounts() {
        VpnState.record("a.com", true)
        VpnState.resetSession()
        assertEquals(0, VpnState.stats.value.queries)
        assertTrue(VpnState.log.value.isEmpty())
    }

    @Test fun statusAndError() {
        VpnState.setStatus(Status.ERROR, "boom")
        assertEquals(Status.ERROR, VpnState.status.value)
        assertEquals("boom", VpnState.error.value)
        VpnState.setStatus(Status.RUNNING)
        assertNull(VpnState.error.value)
    }

    @Test fun updatingFlag() {
        VpnState.setUpdating(true)
        assertTrue(VpnState.updatingLists.value)
        VpnState.setUpdating(false)
        assertFalse(VpnState.updatingLists.value)
    }
}

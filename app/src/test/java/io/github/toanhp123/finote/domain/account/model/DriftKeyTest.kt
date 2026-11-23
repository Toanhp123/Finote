package io.github.toanhp123.finote.domain.account.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriftKeyTest {
    @Test fun `digest is the lowercase SHA-256 hex of account, time and balance`() =
        assertEquals(
            "1620d65cf740eea1e620539e84b99f5a14c1fcd6c04eada51c7bf286f08ba01b",
            DriftKey(5L, -1_000L).digest("acc-1"),
        )

    @Test fun `digest is 64 lowercase hex characters`() =
        assertTrue(DriftKey(5L, 1_000L).digest("acc-1").matches(Regex("[0-9a-f]{64}")))

    @Test fun `digest changes with the account, the time and the balance`() {
        val base = DriftKey(5L, 1_000L).digest("acc-1")
        assertNotEquals(base, DriftKey(5L, 1_000L).digest("acc-2"))
        assertNotEquals(base, DriftKey(6L, 1_000L).digest("acc-1"))
        assertNotEquals(base, DriftKey(5L, 1_001L).digest("acc-1"))
    }

    @Test fun `digest does not hold the balance`() =
        assertTrue(!DriftKey(5L, 123_456_789L).digest("acc-1").contains("123456789"))
}

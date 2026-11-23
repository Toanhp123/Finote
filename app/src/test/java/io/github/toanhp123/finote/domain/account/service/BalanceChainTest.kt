package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceChainTest {
    private fun e(
        from: Long,
        to: Long,
    ) = BalanceEdge(from, to)

    @Test fun `no edges chain from anything`() = assertTrue(chainsFrom(1000, emptyList()))

    @Test fun `path in any list order chains`() = assertTrue(chainsFrom(1000, listOf(e(900, 1200), e(1000, 900))))

    @Test fun `cycle back to start chains`() = assertTrue(chainsFrom(1000, listOf(e(900, 1000), e(1000, 900))))

    @Test fun `cycle with an exit chains`() =
        assertTrue(
            chainsFrom(1000, listOf(e(1000, 800), e(1000, 1100), e(1100, 1000))),
        )

    @Test fun `gap does not chain`() = assertFalse(chainsFrom(1000, listOf(e(1000, 900), e(850, 1150))))

    @Test fun `wrong start does not chain`() = assertFalse(chainsFrom(1300, listOf(e(1000, 900), e(900, 1200))))

    @Test fun `disconnected cycle after a gap does not chain`() =
        assertFalse(chainsFrom(1000, listOf(e(1000, 1200), e(800, 900), e(900, 800))))

    @Test fun `branch does not chain`() = assertFalse(chainsFrom(1000, listOf(e(1000, 900), e(1000, 800))))

    @Test fun `identical edges chain`() =
        assertTrue(
            chainsFrom(1000, listOf(e(1000, 1100), e(1100, 1000), e(1000, 1100))),
        )

    @Test fun `unique start of a swapped path`() = assertEquals(1000L, uniqueStart(listOf(e(900, 1200), e(1000, 900))))

    @Test fun `unique start of a cycle plus tail`() =
        assertEquals(1000L, uniqueStart(listOf(e(900, 1000), e(1000, 900), e(1000, 950))))

    @Test fun `pure cycle has no unique start`() = assertNull(uniqueStart(listOf(e(900, 1000), e(1000, 900))))

    @Test fun `gap has no unique start`() = assertNull(uniqueStart(listOf(e(1000, 900), e(850, 1150))))

    @Test fun `single edge starts at its from`() = assertEquals(1000L, uniqueStart(listOf(e(1000, 900))))

    @Test fun `no edges have no unique start`() = assertNull(uniqueStart(emptyList()))

    @Test fun `expense row gives an edge from before to after`() {
        val row = tx(TransactionType.EXPENSE, 100).copy(postBalance = 900)
        assertEquals(e(1000, 900), row.balanceEdge(A))
    }

    @Test fun `received transfer gives an edge through the receiver balance`() {
        val row =
            tx(TransactionType.TRANSFER, 100, account = B, to = A).copy(toPostBalance = 1100, toTimestamp = 5)
        assertEquals(e(1000, 1100), row.balanceEdge(A))
    }

    @Test fun `row without a balance gives no edge`() = assertNull(tx(TransactionType.EXPENSE, 100).balanceEdge(A))

    @Test fun `edge toString hides the balances`() = assertEquals("BalanceEdge", e(1, 2).toString())

    @Test fun `effective order sorts by bank time then createdAt then id`() {
        val late = tx(TransactionType.EXPENSE, 1, at = 20).copy(id = "a")
        val early = tx(TransactionType.EXPENSE, 1, at = 10).copy(id = "b")
        val received =
            tx(TransactionType.TRANSFER, 1, account = B, to = A, at = 5).copy(id = "c", toTimestamp = 15)
        assertEquals(listOf(early, received, late), listOf(late, received, early).sortedWith(effectiveOrder(A)))
    }
}

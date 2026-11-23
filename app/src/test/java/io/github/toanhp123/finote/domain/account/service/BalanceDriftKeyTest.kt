package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceDriftKeyTest {
    private val account = testAccount(A, initialBalance = 1_000)

    private fun m(n: Long) = n * 60_000L

    /** Bank row on account A: [amount] is signed, [post] is the bank balance after it. */
    private fun bank(
        id: String,
        amount: Long,
        at: Long,
        post: Long,
    ): Transaction =
        tx(if (amount > 0) TransactionType.INCOME else TransactionType.EXPENSE, Math.abs(amount), at = at)
            .copy(id = id, createdAt = at, postBalance = post)

    private fun key(vararg rows: Transaction) = balanceDriftKey(account, rows.toList())

    @Test fun `in-order stream of twenty rows has no banner`() {
        var balance = 1_000L
        val rows =
            (0 until 20).map { i ->
                val amount = if (i % 2 == 0) 100L else -50L
                balance += amount
                bank("r${i.toString().padStart(2, '0')}", amount, m(i / 2L), balance)
            }
        assertNull(balanceDriftKey(account, rows))
    }

    @Test fun `swapped pair has no banner`() = assertNull(key(bank("b", 300, m(1), 1_200), bank("a", -100, m(2), 900)))

    @Test fun `swap returning to the same balance has no banner`() =
        assertNull(key(bank("b", 100, m(1), 1_000), bank("a", -100, m(2), 900)))

    @Test fun `missing notification shows a banner`() =
        assertEquals(DriftKey(m(2), 1_200), key(bank("a", -100, m(1), 900), bank("b", 300, m(2), 1_150)))

    @Test fun `missing plus swap shows a banner`() =
        assertEquals(DriftKey(m(2), 1_200), key(bank("b", 300, m(1), 1_150), bank("a", -100, m(2), 850)))

    @Test fun `four rows interleaved have no banner`() =
        assertNull(
            key(
                bank("r1", -50, m(1), 1_150),
                bank("r2", 300, m(2), 1_450),
                bank("r3", -100, m(3), 1_350),
                bank("r4", 200, m(4), 1_200),
            ),
        )

    @Test fun `cycle with an exit after an older row has no banner`() =
        assertNull(
            balanceDriftKey(
                testAccount(A, initialBalance = 900),
                listOf(
                    bank("r0", 100, m(0), 1_000),
                    bank("r3", -200, m(40), 800),
                    bank("r1", 100, m(41), 1_100),
                    bank("r2", -100, m(42), 1_000),
                ),
            ),
        )

    @Test fun `swap split by the moving window has no banner`() =
        assertNull(
            key(
                bank("b", 300, m(0), 1_200),
                bank("a", -100, m(20), 900),
                bank("d", 500, m(40), 1_500),
                bank("c", -200, m(41), 1_000),
            ),
        )

    @Test fun `hidden missing income shows a banner`() =
        assertEquals(DriftKey(m(2), 1_200), key(bank("b", 300, m(1), 1_500), bank("a", -100, m(2), 1_200)))

    @Test fun `disconnected cycle shows a banner`() =
        assertEquals(
            DriftKey(m(3), 1_200),
            key(bank("a", 200, m(1), 1_200), bank("b", 100, m(2), 900), bank("c", -100, m(3), 800)),
        )

    @Test fun `adjustment after newest balance row settles`() {
        val settle = adjustment(AdjustmentDirection.OUT, 50, at = m(2)).copy(id = "adj", createdAt = m(2))
        assertNull(key(bank("a", -100, m(1), 850), settle))
    }

    @Test fun `balance-less row in window uses today's verdict`() {
        val noBalance = tx(TransactionType.INCOME, 50, at = m(2)).copy(id = "n", createdAt = m(2))
        val rows = listOf(bank("a", -100, m(1), 900), noBalance, bank("b", 100, m(3), 1_050))
        assertEquals(hasAnchorDrift(account, rows), balanceDriftKey(account, rows) != null)
        assertNull(balanceDriftKey(account, rows))
    }

    @Test fun `future manual row after T keeps the chain path`() {
        val future = tx(TransactionType.EXPENSE, 10, at = m(60 * 24 * 30)).copy(id = "f", createdAt = m(60 * 24 * 30))
        assertNull(key(bank("b", 300, m(1), 1_200), bank("a", -100, m(2), 900), future))
    }

    @Test fun `credit card has no banner`() =
        assertNull(
            balanceDriftKey(
                testAccount(A, initialBalance = 1_000, type = AccountType.CREDIT_CARD),
                listOf(bank("a", -100, m(1), 123)),
            ),
        )

    @Test fun `paired transfer receiver edge chains`() {
        val spend = bank("e", -100, m(2), 900)
        val transfer =
            tx(TransactionType.TRANSFER, 300, account = B, to = A, at = m(1))
                .copy(id = "t", createdAt = m(1), toPostBalance = 1_200, toTimestamp = m(3))
        assertNull(key(transfer, spend))
    }

    @Test fun `paired transfer with a wrong receiver balance shows a banner`() {
        val spend = bank("e", -100, m(2), 900)
        val transfer =
            tx(TransactionType.TRANSFER, 300, account = B, to = A, at = m(1))
                .copy(id = "t", createdAt = m(1), toPostBalance = 1_250, toTimestamp = m(3))
        assertEquals(DriftKey(m(3), 1_200), key(transfer, spend))
    }

    @Test fun `paired transfer clears a drift the old anchor would show`() {
        val spend = bank("e", -100, m(1), 1_200)
        val transfer =
            tx(TransactionType.TRANSFER, 300, account = B, to = A, at = m(2))
                .copy(id = "t", createdAt = m(2), toPostBalance = 1_300, toTimestamp = m(2))
        val rows = listOf(spend, transfer)
        assertTrue(hasAnchorDrift(account, rows))
        assertNull(balanceDriftKey(account, rows))
    }

    @Test fun `balance-less row after the newest balance row keeps the banner`() {
        val later = tx(TransactionType.INCOME, 100, at = m(2)).copy(id = "later", createdAt = m(2))
        assertEquals(DriftKey(m(1), 1_500), key(bank("a", 500, m(1), 1_700), later))
    }

    @Test fun `manual row after a matching balance row has no banner`() {
        val later = tx(TransactionType.INCOME, 300, at = m(2)).copy(id = "later", createdAt = m(2))
        assertNull(key(bank("a", 500, m(1), 1_500), later))
    }

    @Test fun `adjustment before the newest balance row does not hide a drift`() {
        val early = adjustment(AdjustmentDirection.IN, 50, at = m(1)).copy(id = "adj", createdAt = m(1))
        assertEquals(DriftKey(m(2), 1_550), key(early, bank("a", 500, m(2), 1_700)))
    }

    @Test fun `deleted rows and other accounts are ignored`() {
        val rows =
            listOf(
                bank("a", 500, m(1), 1_500),
                tx(TransactionType.INCOME, 100, at = 0).copy(id = "gone", deletedAt = 5),
                tx(TransactionType.INCOME, 9_000, account = B, at = 0).copy(id = "other"),
            )
        assertNull(balanceDriftKey(account, rows))
    }

    @Test fun `row without a bank balance gives no banner`() =
        assertNull(key(tx(TransactionType.INCOME, 500, at = m(1)).copy(id = "a", createdAt = m(1))))

    /** `e` breaks the chain from the start, yet `r0` agrees with the ledger by chance; only a checkpoint hides `e`. */
    private fun marginRows(newestAt: Long) =
        arrayOf(bank("e", 100, 0, 1_300), bank("r0", 200, m(1), 1_300), bank("r1", -50, newestAt, 1_250))

    @Test fun `agreeing row exactly one window before the newest is not a checkpoint`() =
        assertEquals(DriftKey(m(31), 1_250), key(*marginRows(newestAt = m(1) + BALANCE_ORDER_WINDOW_MILLIS)))

    @Test fun `agreeing row more than one window before the newest is a checkpoint`() =
        assertNull(key(*marginRows(newestAt = m(1) + BALANCE_ORDER_WINDOW_MILLIS + 1)))

    @Test fun `ties on time are ordered by createdAt then id`() {
        val first = tx(TransactionType.INCOME, 500, at = m(1)).copy(id = "a", createdAt = 1)
        val second = bank("b", 100, m(1), 1_600).copy(createdAt = 2)
        assertNull(key(second, first))
    }
}

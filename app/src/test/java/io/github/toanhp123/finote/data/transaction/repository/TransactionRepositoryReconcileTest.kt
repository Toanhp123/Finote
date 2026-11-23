package io.github.toanhp123.finote.data.transaction.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TransactionRepositoryReconcileTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var repo: TransactionRepositoryImpl

    @Before
    fun setUp() =
        runTest {
            repo = TransactionRepositoryImpl(dbRule.db, clock)
            dbRule.db.accountDao().insert(
                account(id = "acc-1", currentBalance = 1_000_000L).copy(isInitialized = false),
            )
        }

    private suspend fun stored() = checkNotNull(dbRule.db.accountDao().getById("acc-1"))

    @Test
    fun `zero difference locks a provisional account and books nothing`() =
        runTest {
            assertNull(repo.reconcile("adj-1", "acc-1", 1_000_000L, clock.millis()))

            assertTrue(stored().isInitialized)
            assertEquals(
                0,
                dbRule.db
                    .transactionDao()
                    .observeTransactions(null)
                    .first()
                    .size,
            )
        }

    @Test
    fun `non-zero difference books one adjustment and locks`() =
        runTest {
            assertFalse(stored().isInitialized)

            val tx = repo.reconcile("adj-1", "acc-1", 1_200_000L, clock.millis())

            assertEquals(TransactionType.ADJUSTMENT, tx?.type)
            assertEquals(AdjustmentDirection.IN, tx?.adjustmentDirection)
            assertEquals(200_000L, tx?.amount)
            assertTrue(stored().isInitialized)
            assertEquals(1_200_000L, stored().currentBalance)
        }
}

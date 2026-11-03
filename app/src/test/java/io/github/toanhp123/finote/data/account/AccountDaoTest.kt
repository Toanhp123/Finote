package io.github.toanhp123.finote.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.account
import io.github.toanhp123.finote.domain.account.model.AccountType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.accountDao()

    @Test
    fun `inserted account is read back`() =
        runTest {
            val account = account(type = AccountType.CREDIT_CARD)
            dao.insert(account)
            assertEquals(account, dao.getById(account.id))
        }

    @Test
    fun `account round-trips every column`() =
        runTest {
            val account =
                account(type = AccountType.CREDIT_CARD, currentBalance = -5_000_000_000L).copy(
                    initialBalance = 0L,
                    isExcludedFromNetWorth = true,
                    isArchived = true,
                    isInitialized = true,
                    deletedAt = null,
                )
            dao.insert(account)
            assertEquals(account, dao.getById(account.id))
        }

    @Test
    fun `update changes the stored row`() =
        runTest {
            val original = account()
            dao.insert(original)
            val changed = original.copy(name = "Renamed", currentBalance = 42L, updatedAt = 1_700_000_001_000L)
            dao.update(changed)
            assertEquals(changed, dao.getById(original.id))
        }

    @Test
    fun `observeAccounts hides soft-deleted rows but getById returns them`() =
        runTest {
            dao.insert(account(id = "acc-b", sortOrder = 2))
            dao.insert(account(id = "acc-a", sortOrder = 1))
            dao.insert(account(id = "acc-gone", sortOrder = 0, deletedAt = 1L))

            assertEquals(listOf("acc-a", "acc-b"), dao.observeAccounts().first().map { it.id })
            assertNotNull(dao.getById("acc-gone"))
        }
}

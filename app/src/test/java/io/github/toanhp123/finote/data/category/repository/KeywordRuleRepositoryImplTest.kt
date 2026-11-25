package io.github.toanhp123.finote.data.category.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.category.seed.SystemKeywordRules
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.model.RuleMatch
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val SYSTEM_ID = "kw_kfc"

private fun userRule(
    id: String = "user-1",
    keyword: String = "Ăn Vặt",
    merchantName: String? = "Snack",
) = KeywordRule(
    id = id,
    keyword = keyword,
    normalizedKeyword = "wrong",
    matchType = KeywordMatchType.CONTAINS,
    merchantName = merchantName,
    categoryId = "sys_food_restaurants",
    priority = 9,
    isUserDefined = false,
    isActive = true,
    version = 3,
    createdAt = 10L,
    updatedAt = 20L,
)

@RunWith(AndroidJUnit4::class)
class KeywordRuleRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedSystemCategories = true, seedKeywordRules = true)

    private lateinit var repo: KeywordRuleRepositoryImpl

    @Before
    fun setUp() {
        repo = KeywordRuleRepositoryImpl(dbRule.db)
    }

    @Test
    fun `upsert and observe a user rule`() =
        runTest {
            repo.observeRules().test {
                assertEquals(SystemKeywordRules.size, awaitItem().size)

                repo.upsertUserRule(userRule())

                val rules = awaitItem()
                assertEquals(SystemKeywordRules.size + 1, rules.size)
                // The repository forces the user shape and the normalized keyword.
                assertEquals(
                    userRule().copy(normalizedKeyword = "an vat", priority = 0, isUserDefined = true, version = 0),
                    rules.single { it.id == "user-1" },
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertNotNull(repo.getRule("user-1"))
        }

    @Test
    fun `upsertUserRule changes an existing user rule`() =
        runTest {
            repo.upsertUserRule(userRule())

            repo.upsertUserRule(userRule(keyword = "Cơm Tấm"))

            assertEquals("com tam", repo.getRule("user-1")?.normalizedKeyword)
            assertEquals(SystemKeywordRules.size + 1, repo.getRules().size)
        }

    @Test
    fun `upsertUserRule never overwrites a system rule`() =
        runTest {
            val before = repo.getRule(SYSTEM_ID)

            repo.upsertUserRule(userRule(id = SYSTEM_ID))

            assertEquals(before, repo.getRule(SYSTEM_ID))
        }

    @Test
    fun `deleteUserRule removes a user rule but not a system rule`() =
        runTest {
            repo.upsertUserRule(userRule())

            repo.deleteUserRule("user-1")
            repo.deleteUserRule(SYSTEM_ID)

            assertNull(repo.getRule("user-1"))
            assertNotNull(repo.getRule(SYSTEM_ID))
        }

    @Test
    fun `setActive flips only isActive and updatedAt`() =
        runTest {
            val before = checkNotNull(repo.getRule(SYSTEM_ID))

            repo.setActive(SYSTEM_ID, active = false, now = 5_000L)

            assertEquals(before.copy(isActive = false, updatedAt = 5_000L), repo.getRule(SYSTEM_ID))
        }

    private suspend fun seedRows() {
        val db = dbRule.db
        db.accountDao().insert(account())
        db.accountDao().insert(account(id = "acc-2"))
        (1..8).forEach { db.notificationLogDao().insert(notificationLogEntity(id = "log-$it", contentHash = "h-$it")) }
        val dao = db.transactionDao()
        dao.insert(transaction(id = "t-ok", notificationLogId = "log-1"))
        dao.insert(transaction(id = "t-income", type = TransactionType.INCOME, notificationLogId = "log-2"))
        dao.insert(transaction(id = "t-manual"))
        dao.insert(transaction(id = "t-cat", categoryId = "sys_food_restaurants", notificationLogId = "log-3"))
        dao.insert(
            transaction(
                id = "t-transfer",
                toAccountId = "acc-2",
                type = TransactionType.TRANSFER,
                notificationLogId = "log-4",
            ),
        )
        dao.insert(transaction(id = "t-refund", type = TransactionType.REFUND, notificationLogId = "log-5"))
        dao.insert(
            transaction(id = "t-adjust", type = TransactionType.ADJUSTMENT, notificationLogId = "log-6")
                .copy(adjustmentDirection = AdjustmentDirection.OUT),
        )
        dao.insert(transaction(id = "t-deleted", notificationLogId = "log-7").copy(deletedAt = 5L))
    }

    @Test
    fun `bulk apply skips manual rows, rows with a category, transfers and deleted rows`() =
        runTest {
            seedRows()

            val ids = repo.getUncategorizedNotificationRows().map { it.id }

            assertEquals(setOf("t-ok", "t-income"), ids.toSet())
        }

    @Test
    fun `bulk apply keeps updatedAt and keeps the merchant when the rule has no name`() =
        runTest {
            seedRows()
            val rule = userRule().copy(isUserDefined = true)
            val before = checkNotNull(dbRule.db.transactionDao().getById("t-ok"))

            repo.setCategory(
                listOf(
                    "t-ok" to RuleMatch(rule, "sys_food_restaurants", "Snack"),
                    "t-income" to RuleMatch(rule, "sys_income_salary", null),
                ),
            )

            val ok = checkNotNull(dbRule.db.transactionDao().getById("t-ok"))
            assertEquals(before.copy(categoryId = "sys_food_restaurants", merchantName = "Snack"), ok)
            val income = checkNotNull(dbRule.db.transactionDao().getById("t-income"))
            assertEquals("sys_income_salary", income.categoryId)
            assertEquals("Test merchant", income.merchantName)
            assertEquals(1_700_000_000_000L, income.updatedAt)
            // A written row is no longer waiting.
            assertEquals(emptyList<String>(), repo.getUncategorizedNotificationRows().map { it.id })
        }

    @Test
    fun `blank merchant name is stored as null`() =
        runTest {
            repo.upsertUserRule(userRule(merchantName = "   "))

            assertNull(repo.getRule("user-1")?.merchantName)
            assertNull(
                dbRule.db
                    .keywordRuleDao()
                    .getById("user-1")
                    ?.normalizedMerchantName,
            )
            assertEquals(
                repo.getRule("user-1"),
                dbRule.db
                    .keywordRuleDao()
                    .getById("user-1")
                    ?.toDomain(),
            )
        }
}

package io.github.toanhp123.finote.ui.cashentry

import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.category.error.KeywordRuleError
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.repository.KeywordRuleRepository
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.FakeKeywordRuleRepository
import io.github.toanhp123.finote.domain.category.usecase.testRule
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransferPairRepository
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Counts rule reads and user rule writes, so a test sees a second save even when it rewrites the same rule. */
private class CountingRules(
    val fake: FakeKeywordRuleRepository,
) : KeywordRuleRepository by fake {
    var upserts = 0
    var reads = 0

    override suspend fun getRules(): List<KeywordRule> {
        reads++
        return fake.getRules()
    }

    override suspend fun upsertUserRule(rule: KeywordRule) {
        upserts++
        fake.upsertUserRule(rule)
    }
}

/** The "Apply to similar?" step of the cash entry sheet in edit mode: offer, skip, create or update, bulk apply. */
@OptIn(ExperimentalCoroutinesApi::class)
class CashEntryRuleStepViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val accounts = FakeAccountRepository().apply { stored[CASH] = testAccount(CASH) }
    private val categories =
        FakeCategoryRepository().apply {
            listOf(testCategory("c1"), testCategory("c2"), testCategory("i1", type = CategoryType.INCOME))
                .forEach { stored[it.id] = it }
        }
    private val transactions =
        FakeTransactionRepository(
            activeAccounts = setOf(CASH),
            categories = mapOf("c1" to CategoryType.EXPENSE, "c2" to CategoryType.EXPENSE),
        )
    private var rules = CountingRules(FakeKeywordRuleRepository())

    private val CashEntryViewModel.current get() = state.value

    private fun CashEntryViewModel.send(intent: CashEntryIntent) {
        onIntent(intent)
        mainRule.dispatcher.scheduler.runCurrent()
    }

    private fun notificationRow(
        id: String,
        categoryId: String?,
        merchant: String = MERCHANT,
    ) = Transaction(
        id = id,
        amount = 50_000L,
        feeAmount = 0L,
        type = TransactionType.EXPENSE,
        adjustmentDirection = null,
        accountId = CASH,
        toAccountId = null,
        timestamp = clock.millis(),
        deletedAt = null,
        createdAt = 1L,
        updatedAt = 1L,
        categoryId = categoryId,
        note = "",
        merchantName = merchant,
        notificationLogId = "log-$id",
    )

    /** Opens the stored notification row (category c1, merchant [merchant]), picks [picked] and saves. */
    private fun TestScope.saveWithCategory(
        picked: String? = "c2",
        merchant: String = MERCHANT,
    ): CashEntryViewModel {
        transactions.stored[ROW_ID] = notificationRow(ROW_ID, "c1", merchant)
        val pairs = FakeTransferPairRepository(transactions)
        val vm = cashEntryViewModel(ROW_ID, accounts, categories, transactions, clock, pairs, rules)
        runCurrent()
        vm.send(CashEntryIntent.CategoryPicked(picked))
        vm.send(CashEntryIntent.SaveClicked)
        return vm
    }

    private fun userRule(keyword: String) =
        KeywordRule(
            id = "u1",
            keyword = keyword,
            normalizedKeyword = normalizeKeyword(keyword),
            matchType = KeywordMatchType.CONTAINS,
            merchantName = null,
            categoryId = "c1",
            priority = 0,
            isUserDefined = true,
            isActive = true,
            version = 0,
            createdAt = 0,
            updatedAt = 0,
        )

    @Test
    fun `edit save with a new category shows the rule step instead of leaving`() =
        runTest(mainRule.dispatcher) {
            val vm = saveWithCategory()

            assertEquals("c2", transactions.stored.getValue(ROW_ID).categoryId)
            assertTrue(vm.current.isVisible)
            val offer = checkNotNull(vm.current.ruleOffer)
            assertEquals(MERCHANT, offer.keyword)
            assertEquals("Momo Ho Chi Minh", offer.merchantName)
            assertEquals("c2", offer.categoryId)
            assertNull(offer.existingRuleId)
        }

    @Test
    fun `an unchanged category leaves as before`() =
        runTest(mainRule.dispatcher) {
            val vm = saveWithCategory(picked = "c1")

            assertNull(vm.current.ruleOffer)
            assertFalse(vm.current.isVisible)
        }

    @Test
    fun `skip leaves`() =
        runTest(mainRule.dispatcher) {
            val vm = saveWithCategory()

            vm.send(CashEntryIntent.RuleSkipped)

            assertFalse(vm.current.isVisible)
            assertTrue(rules.fake.rules.isEmpty())
        }

    @Test
    fun `a plain dismiss on the rule step also leaves without a rule`() =
        runTest(mainRule.dispatcher) {
            // Back and a swipe send RuleSkipped (see skip); Dismissed must not write a rule either.
            val vm = saveWithCategory()

            vm.send(CashEntryIntent.Dismissed)

            assertFalse(vm.current.isVisible)
            assertEquals(0, rules.upserts)
        }

    @Test
    fun `skip while a rule write runs leaves, and the late result asks nothing`() =
        runTest(mainRule.dispatcher) {
            rules.fake.uncategorized = listOf(notificationRow("old-1", null))
            val vm = saveWithCategory()

            vm.onIntent(CashEntryIntent.RuleSaveClicked)
            vm.onIntent(CashEntryIntent.RuleSkipped)
            assertFalse(vm.current.isVisible)
            runCurrent()

            assertFalse(vm.current.isVisible)
            assertNull(vm.current.bulkApplyCount)
            assertTrue(rules.fake.categorized.isEmpty())
        }

    @Test
    fun `a keyword edit drops the old update answer at once`() =
        runTest(mainRule.dispatcher) {
            rules.fake.upsertUserRule(userRule(MERCHANT))
            val vm = saveWithCategory()
            assertEquals("u1", vm.current.ruleOffer?.existingRuleId)

            vm.onIntent(CashEntryIntent.RuleKeywordChanged("MOMO"))

            assertNull(vm.current.ruleOffer?.existingRuleId)
        }

    @Test
    fun `a name edit changes the name without a look-up`() =
        runTest(mainRule.dispatcher) {
            val vm = saveWithCategory()
            val reads = rules.reads

            vm.send(CashEntryIntent.RuleNameChanged("MoMo"))

            assertEquals("MoMo", vm.current.ruleOffer?.merchantName)
            assertEquals(reads, rules.reads)
        }

    @Test
    fun `editing the keyword to one of a user rule switches to update`() =
        runTest(mainRule.dispatcher) {
            rules.fake.upsertUserRule(userRule("grab"))
            val vm = saveWithCategory()

            vm.send(CashEntryIntent.RuleKeywordChanged("Grab"))
            assertEquals("u1", vm.current.ruleOffer?.existingRuleId)
            vm.send(CashEntryIntent.RuleSaveClicked)

            val saved = rules.fake.rules.single()
            assertEquals("u1", saved.id)
            assertEquals("c2", saved.categoryId)
            assertEquals("Momo Ho Chi Minh", saved.merchantName)
        }

    @Test
    fun `the prefilled keyword of a user rule opens as update`() =
        runTest(mainRule.dispatcher) {
            rules.fake.upsertUserRule(userRule(MERCHANT))

            assertEquals("u1", saveWithCategory().current.ruleOffer?.existingRuleId)
        }

    @Test
    fun `a row renamed by a system rule offers that rule's keyword and creates a user rule`() =
        runTest(mainRule.dispatcher) {
            val system = testRule("kw_highlands", "highlands", "Highlands Coffee", "c1", isUserDefined = false)
            rules = CountingRules(FakeKeywordRuleRepository(listOf(system)))

            val vm = saveWithCategory(merchant = "Highlands Coffee")

            val offer = checkNotNull(vm.current.ruleOffer)
            assertEquals("highlands", offer.keyword)
            assertNull(offer.existingRuleId)
            vm.send(CashEntryIntent.RuleSaveClicked)
            val user = rules.fake.rules.single { it.isUserDefined }
            assertEquals("highlands", user.keyword)
            assertEquals("c2", user.categoryId)
        }

    @Test
    fun `a row renamed by a user rule offers update of that rule`() =
        runTest(mainRule.dispatcher) {
            rules.fake.upsertUserRule(testRule("u9", "highlands", "Highlands Coffee", "c1"))

            val vm = saveWithCategory(merchant = "Highlands Coffee")

            assertEquals("highlands", vm.current.ruleOffer?.keyword)
            assertEquals("u9", vm.current.ruleOffer?.existingRuleId)
        }

    @Test
    fun `save with matches shows the count, confirm applies and leaves`() =
        runTest(mainRule.dispatcher) {
            rules.fake.uncategorized = listOf(notificationRow("old-1", null), notificationRow("old-2", null))
            val vm = saveWithCategory()

            vm.send(CashEntryIntent.RuleSaveClicked)
            assertEquals(2, vm.current.bulkApplyCount)
            assertTrue(vm.current.isVisible)
            vm.send(CashEntryIntent.BulkApplyConfirmed)

            assertEquals(listOf("old-1", "old-2"), rules.fake.categorized.map { it.first })
            assertNull(vm.current.bulkApplyCount)
            assertFalse(vm.current.isVisible)
        }

    @Test
    fun `declining the bulk apply keeps the rule and leaves`() =
        runTest(mainRule.dispatcher) {
            rules.fake.uncategorized = listOf(notificationRow("old-1", null))
            val vm = saveWithCategory()

            vm.send(CashEntryIntent.RuleSaveClicked)
            vm.send(CashEntryIntent.BulkApplyDismissed)

            assertEquals(1, rules.fake.rules.size)
            assertTrue(rules.fake.categorized.isEmpty())
            assertFalse(vm.current.isVisible)
        }

    @Test
    fun `no matches saves and leaves`() =
        runTest(mainRule.dispatcher) {
            val vm = saveWithCategory()

            vm.send(CashEntryIntent.RuleSaveClicked)

            assertEquals(
                "c2",
                rules.fake.rules
                    .single()
                    .categoryId,
            )
            assertNull(vm.current.bulkApplyCount)
            assertFalse(vm.current.isVisible)
        }

    @Test
    fun `double tap saves once`() =
        runTest(mainRule.dispatcher) {
            val vm = saveWithCategory()

            vm.onIntent(CashEntryIntent.RuleSaveClicked)
            vm.onIntent(CashEntryIntent.RuleSaveClicked)
            runCurrent()

            assertEquals(1, rules.upserts)
        }

    @Test
    fun `error keeps the step open with the error`() =
        runTest(mainRule.dispatcher) {
            val vm = saveWithCategory()

            vm.send(CashEntryIntent.RuleKeywordChanged("a"))
            vm.send(CashEntryIntent.RuleSaveClicked)

            val offer = vm.current.ruleOffer
            assertEquals(KeywordRuleError.TooShort, offer?.error)
            assertEquals(false, offer?.isSaving)
            assertTrue(vm.current.isVisible)
            assertTrue(rules.fake.rules.isEmpty())

            vm.send(CashEntryIntent.RuleKeywordChanged("ab"))
            assertNull(vm.current.ruleOffer?.error)
        }

    @Test
    fun `a gone category shows its error`() =
        runTest(mainRule.dispatcher) {
            val vm = saveWithCategory()
            categories.stored["c2"] = categories.stored.getValue("c2").copy(isHidden = true)

            vm.send(CashEntryIntent.RuleSaveClicked)

            assertEquals(KeywordRuleError.CategoryGone, vm.current.ruleOffer?.error)
            assertTrue(vm.current.isVisible)
        }

    private companion object {
        const val CASH = "sys_cash"
        const val ROW_ID = "t1"
        const val MERCHANT = "MOMO HO CHI MINH"
    }
}

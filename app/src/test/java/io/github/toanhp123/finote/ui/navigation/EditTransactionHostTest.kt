package io.github.toanhp123.finote.ui.navigation

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.cashentry.CashEntryRoute
import io.github.toanhp123.finote.ui.cashentry.CashEntryViewModel
import io.github.toanhp123.finote.ui.cashentry.cashEntryViewModel
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** The editor sheet as a nav entry over the list: both stay composed, and a closed editor frees its ViewModel. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class EditTransactionHostTest {
    @get:Rule
    val rule = createComposeRule()

    private val backStack = mutableStateListOf<NavKey>(Overview, Transactions)
    private val probes = mutableListOf<Probe>()

    private inner class Probe(
        val id: String,
    ) : ViewModel() {
        var isCleared = false

        init {
            probes += this
        }

        override fun onCleared() {
            isCleared = true
        }
    }

    private fun showHost() {
        rule.setContent {
            NavDisplay(
                backStack = backStack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                sceneStrategies = remember { listOf(SheetSceneStrategy()) },
                entryProvider =
                    entryProvider<NavKey> {
                        entry<Overview> { Text("overview") }
                        entry<Transactions> { Text("list") }
                        entry<EditTransaction>(metadata = SheetSceneStrategy.sheet()) { key ->
                            Text(viewModel { Probe(key.id) }.id)
                        }
                    },
            )
        }
        rule.waitForIdle()
    }

    @Test
    fun `an open editor is drawn over the list`() {
        showHost()

        rule.runOnIdle { openEditor(backStack, "a") }
        rule.waitForIdle()

        rule.onNodeWithText("list").assertExists()
        rule.onNodeWithText("a").assertExists()
        assertEquals(1, probes.size)
    }

    @Test
    fun `closing and opening another id gives a new view model and clears the first`() {
        showHost()

        rule.runOnIdle { openEditor(backStack, "a") }
        rule.waitForIdle()
        rule.runOnIdle { closeEditor(backStack, EditTransaction("a")) }
        rule.waitForIdle()
        rule.runOnIdle { openEditor(backStack, "b") }
        rule.waitForIdle()

        rule.onNodeWithText("b").assertExists()
        assertEquals(listOf("a", "b"), probes.map { it.id })
        assertNotSame(probes[0], probes[1])
        assertTrue(probes[0].isCleared)
        assertFalse(probes[1].isCleared)
    }

    // CashEntryRoute with the real ViewModel: onClose is how the editor leaves the back stack.
    private val clock = Clock.fixed(Instant.parse("2026-11-04T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val accounts = FakeAccountRepository()
    private val categories = FakeCategoryRepository()
    private val transactions =
        FakeTransactionRepository(activeAccounts = setOf(CASH), categories = mapOf("c1" to CategoryType.EXPENSE))
    private var closeCount = 0

    private fun showRoute(isRowStored: Boolean): CashEntryViewModel {
        accounts.stored[CASH] = testAccount(CASH)
        categories.stored["c1"] = testCategory("c1")
        if (isRowStored) transactions.stored[ROW_ID] = row()
        val viewModel = cashEntryViewModel(ROW_ID, accounts, categories, transactions, clock)
        rule.setContent {
            FinoteTheme(dynamicColor = false) {
                CashEntryRoute(viewModel = viewModel, onClose = { closeCount++ })
            }
        }
        rule.waitForIdle()
        return viewModel
    }

    private fun row() =
        Transaction(
            id = ROW_ID,
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
            categoryId = "c1",
            note = "",
        )

    @Test
    fun `a saved edit calls onClose once`() {
        val viewModel = showRoute(isRowStored = true)
        assertEquals(0, closeCount)

        rule.runOnIdle { viewModel.onIntent(CashEntryIntent.SaveClicked) }
        rule.waitForIdle()

        assertEquals(1, closeCount)
    }

    @Test
    fun `a dismissed sheet calls onClose once`() {
        val viewModel = showRoute(isRowStored = true)

        rule.runOnIdle { viewModel.onIntent(CashEntryIntent.Dismissed) }
        rule.waitForIdle()

        assertEquals(1, closeCount)
    }

    @Test
    fun `a row that does not exist calls onClose once at once`() {
        showRoute(isRowStored = false)

        assertEquals(1, closeCount)
    }

    private companion object {
        const val CASH = "sys_cash"
        const val ROW_ID = "t1"
    }
}

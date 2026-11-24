package io.github.toanhp123.finote.ui.settings.accounts

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.DismissMergeSuggestionUseCase
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountMergeRepository
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.FakeMergeDismissalRepository
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveBankAppsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveMergeSuggestionsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ReorderAccountsUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.FakeParserRuleRepository
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.settings.accounts.logic.AccountListItem
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsSettingsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC)
    private val accounts = FakeAccountRepository()
    private val dismissals = FakeMergeDismissalRepository()
    private val rules = FakeParserRuleRepository()

    private class FailingReorderRepository(
        fake: FakeAccountRepository,
    ) : AccountRepository by fake {
        override suspend fun reorder(
            orderedIds: List<String>,
            now: Long,
        ): Unit = throw StorageException(IllegalStateException("disk"))
    }

    private fun bank(
        id: String,
        pkg: String? = "vcb",
        sortOrder: Int = 0,
        isArchived: Boolean = false,
    ) = testAccount(id = id, name = id).copy(bankPackageName = pkg, sortOrder = sortOrder, isArchived = isArchived)

    private fun TestScope.viewModel(
        vararg seeded: Account,
        repo: AccountRepository = accounts,
    ): AccountsSettingsViewModel {
        seeded.forEach { accounts.stored[it.id] = it }
        val vm =
            AccountsSettingsViewModel(
                ObserveAccountsUseCase(repo),
                ObserveBankAppsUseCase(rules),
                ObserveMergeSuggestionsUseCase(repo, FakeAccountMergeRepository(), dismissals),
                DismissMergeSuggestionUseCase(dismissals),
                ReorderAccountsUseCase(repo, FakeTransactionRunner(), clock),
                isDebuggable = true,
            )
        runCurrent()
        return vm
    }

    private fun AccountsSettingsViewModel.rowIds() = state.value.items.map { it.key }

    @Test
    fun `live accounts are grouped by bank and archived ones are kept apart`() =
        runTest(mainRule.dispatcher) {
            rules.stored.value = listOf(testRule(packageName = "vcb", bankName = "Vietcombank"))
            val vm =
                viewModel(
                    bank("a", sortOrder = 0),
                    bank("b", sortOrder = 1),
                    bank("old", sortOrder = 2, isArchived = true),
                    bank("cash", pkg = null, sortOrder = 3),
                )

            val group =
                vm.state.value.items
                    .first() as AccountListItem.Group
            assertEquals("Vietcombank", group.bankName)
            assertEquals(listOf("a", "b"), group.members.map { it.id })
            assertEquals(
                listOf("cash"),
                vm.state.value.items
                    .drop(1)
                    .flatMap { it.accounts }
                    .map { it.id },
            )
            assertEquals(
                listOf("old"),
                vm.state.value.archived
                    .map { it.id },
            )
        }

    @Test
    fun `account click opens the edit screen`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(bank("a"))

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.AccountClicked("a"))
                assertEquals(AccountsSettingsEffect.OpenAccount("a"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `add click opens the create screen`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.AddClicked)
                assertEquals(AccountsSettingsEffect.OpenAccount(id = null), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `suggestion click opens the edit screen with the partner`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(bank("a"), bank("b"))
            assertEquals(mapOf("a" to "b", "b" to "a"), vm.state.value.suggestions)

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.SuggestionClicked("a"))
                assertEquals(AccountsSettingsEffect.OpenAccount("a", mergeTargetId = "b"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `dismissing a suggestion stores the pair and clears the markers`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(bank("a"), bank("b"))

            vm.onIntent(AccountsSettingsIntent.SuggestionDismissed("b"))
            runCurrent()

            assertEquals(setOf("a" to "b"), dismissals.dismissed.value)
            assertTrue(
                vm.state.value.suggestions
                    .isEmpty(),
            )
        }

    @Test
    fun `groups start collapsed and toggle`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(bank("a"), bank("b"))
            assertTrue(
                vm.state.value.expanded
                    .isEmpty(),
            )

            vm.onIntent(AccountsSettingsIntent.GroupToggled("vcb"))
            assertEquals(setOf("vcb"), vm.state.value.expanded)

            vm.onIntent(AccountsSettingsIntent.GroupToggled("vcb"))
            assertTrue(
                vm.state.value.expanded
                    .isEmpty(),
            )
        }

    @Test
    fun `the archive section starts collapsed and toggles`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(bank("old", isArchived = true))
            assertFalse(vm.state.value.isArchivedExpanded)

            vm.onIntent(AccountsSettingsIntent.ArchivedToggled)

            assertTrue(vm.state.value.isArchivedExpanded)
        }

    @Test
    fun `moving cash between two groups never splits a group`() =
        runTest(mainRule.dispatcher) {
            val vm =
                viewModel(
                    bank("v1", sortOrder = 0),
                    bank("v2", sortOrder = 1),
                    bank("cash", pkg = null, sortOrder = 2),
                    bank("m1", pkg = "mb", sortOrder = 3),
                    bank("m2", pkg = "mb", sortOrder = 4),
                    bank("old", pkg = null, sortOrder = 1, isArchived = true),
                )
            assertEquals(listOf("group:vcb", "cash", "group:mb"), vm.rowIds())

            vm.onIntent(AccountsSettingsIntent.ReorderClicked)
            vm.onIntent(AccountsSettingsIntent.ReorderMoved(from = 1, to = 2))
            vm.onIntent(AccountsSettingsIntent.ReorderDone)
            runCurrent()

            assertNull(vm.state.value.reorder)
            assertEquals(listOf("group:vcb", "group:mb", "cash"), vm.rowIds())
            val order =
                accounts.stored.values
                    .sortedBy { it.sortOrder }
                    .map { it.id }
            // The archived account follows the live ones and no sort order is shared.
            assertEquals(listOf("v1", "v2", "m1", "m2", "cash", "old"), order)
            assertEquals(
                6,
                accounts.stored.values
                    .map { it.sortOrder }
                    .toSet()
                    .size,
            )
        }

    @Test
    fun `reorder mode drafts the order and cancel writes nothing`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(bank("a", pkg = null, sortOrder = 0), bank("b", pkg = null, sortOrder = 1))
            val writes = accounts.writes

            vm.onIntent(AccountsSettingsIntent.ReorderClicked)
            vm.onIntent(AccountsSettingsIntent.ReorderMoved(from = 0, to = 1))
            assertEquals(
                listOf("b", "a"),
                vm.state.value.reorder
                    ?.map { it.key },
            )

            vm.onIntent(AccountsSettingsIntent.ReorderCancelled)
            runCurrent()

            assertNull(vm.state.value.reorder)
            assertEquals(listOf("a", "b"), vm.rowIds())
            assertEquals(writes, accounts.writes)
        }

    @Test
    fun `a move outside the draft is ignored`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(bank("a", pkg = null, sortOrder = 0), bank("b", pkg = null, sortOrder = 1))
            vm.onIntent(AccountsSettingsIntent.ReorderClicked)

            vm.onIntent(AccountsSettingsIntent.ReorderMoved(from = 0, to = 5))

            assertEquals(
                listOf("a", "b"),
                vm.state.value.reorder
                    ?.map { it.key },
            )
        }

    @Test
    fun `a failed reorder write emits ActionFailed`() =
        runTest(mainRule.dispatcher) {
            val vm =
                viewModel(
                    bank("a", pkg = null),
                    bank("b", pkg = null, sortOrder = 1),
                    repo = FailingReorderRepository(accounts),
                )
            vm.onIntent(AccountsSettingsIntent.ReorderClicked)

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.ReorderDone)
                assertEquals(AccountsSettingsEffect.ActionFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

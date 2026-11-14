package io.github.toanhp123.finote.ui.settings.accounts

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.CountMergeMovesUseCase
import io.github.toanhp123.finote.domain.account.usecase.CountTrashableTransactionsUseCase
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.MergeAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.SetAccountIgnoredUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private class RejectingRepository(
        fake: FakeAccountRepository,
        private val failure: Exception? = null,
    ) : AccountRepository by fake {
        override suspend fun merge(
            sourceId: String,
            targetId: String,
            now: Long,
        ) {
            failure?.let { throw it }
        }
    }

    private fun bank(
        id: String,
        pkg: String? = "vcb",
        sortOrder: Int = 0,
        isIgnored: Boolean = false,
    ) = testAccount(id = id, name = id).copy(bankPackageName = pkg, sortOrder = sortOrder, isIgnored = isIgnored)

    private fun viewModel(
        repo: AccountRepository,
        vararg accounts: Account,
        seed: FakeAccountRepository? = repo as? FakeAccountRepository,
    ): AccountsSettingsViewModel {
        seed?.let { fake -> accounts.forEach { fake.stored[it.id] = it } }
        return AccountsSettingsViewModel(
            ObserveAccountsUseCase(repo),
            CountMergeMovesUseCase(repo),
            CountTrashableTransactionsUseCase(repo),
            MergeAccountsUseCase(repo, FakeTransactionRunner(), clock),
            SetAccountIgnoredUseCase(repo, FakeTransactionRunner(), clock),
            isDebuggable = true,
        )
    }

    @Test
    fun `ignored accounts are listed last`() =
        runTest(mainRule.dispatcher) {
            val vm =
                viewModel(
                    FakeAccountRepository(),
                    bank("a", sortOrder = 0, isIgnored = true),
                    bank("b", sortOrder = 2),
                    bank("c", sortOrder = 1),
                )
            runCurrent()

            assertEquals(
                listOf("c", "b", "a"),
                vm.state.value.accounts
                    .map { it.id },
            )
        }

    @Test
    fun `cash account opens no sheet`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(FakeAccountRepository(), bank("cash", pkg = null))
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.AccountClicked("cash"))
            runCurrent()

            assertNull(vm.state.value.selected)
        }

    @Test
    fun `merge targets are live accounts of the same bank only`() =
        runTest(mainRule.dispatcher) {
            val vm =
                viewModel(
                    FakeAccountRepository(),
                    bank("src"),
                    bank("same"),
                    bank("other", pkg = "mb"),
                    bank("cash", pkg = null),
                )
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            assertEquals(
                "src",
                vm.state.value.selected
                    ?.id,
            )
            assertEquals(
                listOf("same"),
                vm.state.value.mergeTargets
                    .map { it.id },
            )
        }

    @Test
    fun `pick target loads the move count, dismiss clears it`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository().apply { liveCounts["src"] = 7 }
            val vm = viewModel(repo, bank("src"), bank("dst"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.MergeTargetPicked("dst"))
            runCurrent()

            assertEquals(
                "src" to "dst",
                vm.state.value.pendingMerge
                    ?.let { it.first.id to it.second.id },
            )
            assertEquals(7, vm.state.value.mergeCount)

            vm.onIntent(AccountsSettingsIntent.DialogDismissed)
            assertNull(vm.state.value.pendingMerge)
        }

    @Test
    fun `confirm merge calls the use case and emits Merged`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository()
            val vm = viewModel(repo, bank("src"), bank("dst"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            vm.onIntent(AccountsSettingsIntent.MergeTargetPicked("dst"))
            runCurrent()

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.MergeConfirmed)
                runCurrent()

                assertEquals(AccountsSettingsEffect.Merged, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(Triple("src", "dst", 5_000L)), repo.merges)
            assertNull(vm.state.value.selected)
            assertNull(vm.state.value.pendingMerge)
        }

    @Test
    fun `transfer rejection emits MergeBlockedByTransfer`() =
        runTest(mainRule.dispatcher) {
            val vm = mergeDialogViewModel(reason = MergeRejection.HAS_TRANSFER)

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.MergeConfirmed)
                runCurrent()

                assertEquals(AccountsSettingsEffect.MergeBlockedByTransfer, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `other failure emits ActionFailed`() =
        runTest(mainRule.dispatcher) {
            val rejected = mergeDialogViewModel(reason = MergeRejection.ACCOUNT_GONE)
            rejected.effects.test {
                rejected.onIntent(AccountsSettingsIntent.MergeConfirmed)
                runCurrent()

                assertEquals(AccountsSettingsEffect.ActionFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val broken = mergeDialogViewModel(failure = StorageException(IllegalStateException()))
            broken.effects.test {
                broken.onIntent(AccountsSettingsIntent.MergeConfirmed)
                runCurrent()

                assertEquals(AccountsSettingsEffect.ActionFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `storage failure emits ActionFailed`() =
        runTest(mainRule.dispatcher) {
            val vm = mergeDialogViewModel(failure = StorageException(ArithmeticException()))

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.MergeConfirmed)
                runCurrent()

                assertEquals(AccountsSettingsEffect.ActionFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `double tap on merge confirm merges once`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository()
            val vm = viewModel(repo, bank("src"), bank("dst"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            vm.onIntent(AccountsSettingsIntent.MergeTargetPicked("dst"))
            runCurrent()

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.MergeConfirmed)
                vm.onIntent(AccountsSettingsIntent.MergeConfirmed)
                runCurrent()

                assertEquals(AccountsSettingsEffect.Merged, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, repo.merges.size)
        }

    @Test
    fun `double tap on ignore confirm ignores once`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository()
            val vm = viewModel(repo, bank("src"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            vm.onIntent(AccountsSettingsIntent.IgnoreClicked)
            runCurrent()

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.IgnoreConfirmed(shouldTrash = false))
                vm.onIntent(AccountsSettingsIntent.IgnoreConfirmed(shouldTrash = false))
                runCurrent()

                assertEquals(AccountsSettingsEffect.Ignored, awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(1, repo.ignores.size)
        }

    @Test
    fun `sheet dismissed while the merge count loads opens no dialog`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(FakeAccountRepository(), bank("src"), bank("dst"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.MergeTargetPicked("dst"))
            vm.onIntent(AccountsSettingsIntent.SheetDismissed)
            runCurrent()

            assertNull(vm.state.value.pendingMerge)
        }

    @Test
    fun `sheet dismissed while the ignore count loads opens no dialog`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(FakeAccountRepository(), bank("src"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.IgnoreClicked)
            vm.onIntent(AccountsSettingsIntent.SheetDismissed)
            runCurrent()

            assertEquals(false, vm.state.value.isIgnoreDialogOpen)
        }

    /**
     * A view model whose merge dialog is open on src -> dst. The rules or the repository decide how the merge ends:
     * [reason] sets up the stored state that makes the use case reject, [failure] makes the repository throw.
     */
    private fun TestScope.mergeDialogViewModel(
        reason: MergeRejection? = null,
        failure: Exception? = null,
    ): AccountsSettingsViewModel {
        val fake = FakeAccountRepository()
        val repo = RejectingRepository(fake, failure)
        val vm = viewModel(repo, bank("src"), bank("dst"), seed = fake)
        runCurrent()
        vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
        vm.onIntent(AccountsSettingsIntent.MergeTargetPicked("dst"))
        runCurrent()
        when (reason) {
            MergeRejection.HAS_TRANSFER -> fake.transferCounts[setOf("src", "dst")] = 1
            MergeRejection.ACCOUNT_GONE -> fake.stored["dst"] = bank("dst").copy(deletedAt = 1L)
            else -> Unit
        }
        return vm
    }

    @Test
    fun `ignore dialog carries the trashable count`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository().apply { trashableCounts["src"] = 3 }
            val vm = viewModel(repo, bank("src"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.IgnoreClicked)
            runCurrent()

            assertEquals(true, vm.state.value.isIgnoreDialogOpen)
            assertEquals(3, vm.state.value.trashableCount)
        }

    @Test
    fun `confirm ignore passes shouldTrash and emits Ignored`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository()
            val vm = viewModel(repo, bank("src"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            vm.onIntent(AccountsSettingsIntent.IgnoreClicked)
            runCurrent()

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.IgnoreConfirmed(shouldTrash = true))
                runCurrent()

                assertEquals(AccountsSettingsEffect.Ignored, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(FakeAccountRepository.IgnoreCall("src", true, true, 5_000L)), repo.ignores)
            assertNull(vm.state.value.selected)
        }

    @Test
    fun `follow again emits FollowingAgain`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository()
            val vm = viewModel(repo, bank("src", isIgnored = true))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.FollowAgainClicked)
                runCurrent()

                assertEquals(AccountsSettingsEffect.FollowingAgain, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(FakeAccountRepository.IgnoreCall("src", false, false, 5_000L)), repo.ignores)
        }
}

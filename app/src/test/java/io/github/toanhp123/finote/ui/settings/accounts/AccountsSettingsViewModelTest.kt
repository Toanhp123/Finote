package io.github.toanhp123.finote.ui.settings.accounts

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.repository.AccountMergeRepository
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.CountMergeMovesUseCase
import io.github.toanhp123.finote.domain.account.usecase.CountTrashableTransactionsUseCase
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountMergeRepository
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.GetMergeDroppedStartUseCase
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

    private class RejectingMergeRepository(
        fake: FakeAccountMergeRepository,
        private val failure: Exception? = null,
    ) : AccountMergeRepository by fake {
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
        type: AccountType = AccountType.BANK,
    ) = testAccount(id = id, name = id).copy(
        bankPackageName = pkg,
        sortOrder = sortOrder,
        isIgnored = isIgnored,
        type = type,
    )

    private fun viewModel(
        repo: AccountRepository,
        vararg accounts: Account,
        mergeRepo: AccountMergeRepository = FakeAccountMergeRepository(),
        seed: FakeAccountRepository? = repo as? FakeAccountRepository,
    ): AccountsSettingsViewModel {
        seed?.let { fake -> accounts.forEach { fake.stored[it.id] = it } }
        return AccountsSettingsViewModel(
            ObserveAccountsUseCase(repo),
            CountMergeMovesUseCase(repo),
            GetMergeDroppedStartUseCase(mergeRepo),
            CountTrashableTransactionsUseCase(repo),
            MergeAccountsUseCase(repo, mergeRepo, FakeTransactionRunner(), clock),
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
    fun `merge targets exclude other types`() =
        runTest(mainRule.dispatcher) {
            val vm =
                viewModel(
                    FakeAccountRepository(),
                    bank("src"),
                    bank("same"),
                    bank("card", type = AccountType.CREDIT_CARD),
                )
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            assertEquals(
                listOf("same"),
                vm.state.value.mergeTargets
                    .map { it.id },
            )
        }

    @Test
    fun `sheet dismissed keeps merge targets until another account opens`() =
        runTest(mainRule.dispatcher) {
            val vm =
                viewModel(
                    FakeAccountRepository(),
                    bank("src"),
                    bank("same"),
                    bank("mb1", pkg = "mb"),
                    bank("mb2", pkg = "mb"),
                )
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.SheetDismissed)

            assertNull(vm.state.value.selected)
            assertEquals(
                listOf("same"),
                vm.state.value.mergeTargets
                    .map { it.id },
            )

            vm.onIntent(AccountsSettingsIntent.AccountClicked("mb1"))
            runCurrent()

            assertEquals(
                listOf("mb2"),
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

            val dialog = vm.state.value.dialog as AccountsDialog.Merge
            assertEquals("src" to "dst", dialog.source.id to dialog.target.id)
            assertEquals(7, dialog.count)

            vm.onIntent(AccountsSettingsIntent.DialogDismissed)
            assertNull(vm.state.value.dialog)
        }

    @Test
    fun `merge dialog carries dropped start`() =
        runTest(mainRule.dispatcher) {
            val mergeRepo = FakeAccountMergeRepository().apply { droppedStarts["src" to "dst"] = 2_000_000L }
            val vm = viewModel(FakeAccountRepository(), bank("src"), bank("dst"), mergeRepo = mergeRepo)
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.MergeTargetPicked("dst"))
            runCurrent()

            assertEquals(2_000_000L, (vm.state.value.dialog as AccountsDialog.Merge).droppedStart)
        }

    @Test
    fun `confirm merge calls the use case and emits Merged`() =
        runTest(mainRule.dispatcher) {
            val mergeRepo = FakeAccountMergeRepository()
            val vm = viewModel(FakeAccountRepository(), bank("src"), bank("dst"), mergeRepo = mergeRepo)
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
            assertEquals(listOf(Triple("src", "dst", 5_000L)), mergeRepo.merges)
            assertNull(vm.state.value.selected)
            assertNull(vm.state.value.dialog)
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
    fun `type rejection emits ActionFailed`() =
        runTest(mainRule.dispatcher) {
            val fake = FakeAccountRepository()
            val mergeFake = FakeAccountMergeRepository()
            val vm = mergeDialogViewModel(reason = MergeRejection.DIFFERENT_TYPE, fake = fake, mergeFake = mergeFake)

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.MergeConfirmed)
                runCurrent()

                assertEquals(AccountsSettingsEffect.ActionFailed, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(mergeFake.merges.isEmpty())
            assertEquals(0, fake.writes)
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
            val mergeRepo = FakeAccountMergeRepository()
            val vm = viewModel(FakeAccountRepository(), bank("src"), bank("dst"), mergeRepo = mergeRepo)
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
            assertEquals(1, mergeRepo.merges.size)
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
                vm.onIntent(AccountsSettingsIntent.IgnoreConfirmed)
                vm.onIntent(AccountsSettingsIntent.IgnoreConfirmed)
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

            assertNull(vm.state.value.dialog)
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

            assertNull(vm.state.value.dialog)
        }

    /**
     * A view model whose merge dialog is open on src -> dst. The rules or the repository decide how the merge ends:
     * [reason] sets up the stored state that makes the use case reject, [failure] makes the repository throw.
     */
    private fun TestScope.mergeDialogViewModel(
        reason: MergeRejection? = null,
        failure: Exception? = null,
        fake: FakeAccountRepository = FakeAccountRepository(),
        mergeFake: FakeAccountMergeRepository = FakeAccountMergeRepository(),
    ): AccountsSettingsViewModel {
        val vm = viewModel(fake, bank("src"), bank("dst"), mergeRepo = RejectingMergeRepository(mergeFake, failure))
        runCurrent()
        vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
        vm.onIntent(AccountsSettingsIntent.MergeTargetPicked("dst"))
        runCurrent()
        when (reason) {
            MergeRejection.HAS_TRANSFER -> mergeFake.transferCounts[setOf("src", "dst")] = 1
            MergeRejection.ACCOUNT_GONE -> fake.stored["dst"] = bank("dst").copy(deletedAt = 1L)
            MergeRejection.DIFFERENT_TYPE -> fake.stored["dst"] = bank("dst", type = AccountType.CREDIT_CARD)
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

            assertEquals(AccountsDialog.Ignore(trashableCount = 3), vm.state.value.dialog)
        }

    @Test
    fun `ticked trash with rows trashes and emits Ignored`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository().apply { trashableCounts["src"] = 3 }
            val vm = viewModel(repo, bank("src"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            vm.onIntent(AccountsSettingsIntent.IgnoreClicked)
            runCurrent()

            vm.effects.test {
                vm.onIntent(AccountsSettingsIntent.TrashToggled(isChecked = true))
                vm.onIntent(AccountsSettingsIntent.IgnoreConfirmed)
                runCurrent()

                assertEquals(AccountsSettingsEffect.Ignored, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(FakeAccountRepository.IgnoreCall("src", true, true, 5_000L)), repo.ignores)
            assertNull(vm.state.value.selected)
        }

    @Test
    fun `ignore dialog reopens unticked`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository().apply { trashableCounts["src"] = 3 }
            val vm = viewModel(repo, bank("src"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            vm.onIntent(AccountsSettingsIntent.IgnoreClicked)
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.TrashToggled(isChecked = true))
            vm.onIntent(AccountsSettingsIntent.DialogDismissed)
            vm.onIntent(AccountsSettingsIntent.IgnoreClicked)
            runCurrent()

            assertEquals(false, (vm.state.value.dialog as AccountsDialog.Ignore).shouldTrash)
        }

    @Test
    fun `no trashable rows never trashes`() =
        runTest(mainRule.dispatcher) {
            val repo = FakeAccountRepository()
            val vm = viewModel(repo, bank("src"))
            runCurrent()
            vm.onIntent(AccountsSettingsIntent.AccountClicked("src"))
            vm.onIntent(AccountsSettingsIntent.IgnoreClicked)
            runCurrent()

            vm.onIntent(AccountsSettingsIntent.TrashToggled(isChecked = true))
            vm.onIntent(AccountsSettingsIntent.IgnoreConfirmed)
            runCurrent()

            assertEquals(false, repo.ignores.single().trash)
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

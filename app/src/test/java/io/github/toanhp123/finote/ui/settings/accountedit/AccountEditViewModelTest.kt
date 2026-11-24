package io.github.toanhp123.finote.ui.settings.accountedit

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.model.SYSTEM_CASH_ACCOUNT_ID
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.account.usecase.AddAccountIdentifierUseCase
import io.github.toanhp123.finote.domain.account.usecase.ArchiveAccountUseCase
import io.github.toanhp123.finote.domain.account.usecase.CountMergeMovesUseCase
import io.github.toanhp123.finote.domain.account.usecase.CountTrashableTransactionsUseCase
import io.github.toanhp123.finote.domain.account.usecase.CreateAccountUseCase
import io.github.toanhp123.finote.domain.account.usecase.DeleteAccountUseCase
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountMergeRepository
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.account.usecase.FakeDriftAcknowledgementRepository
import io.github.toanhp123.finote.domain.account.usecase.GetMergeDroppedStartUseCase
import io.github.toanhp123.finote.domain.account.usecase.MergeAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountIdentifiersUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveBankAppsUseCase
import io.github.toanhp123.finote.domain.account.usecase.RemoveAccountIdentifierUseCase
import io.github.toanhp123.finote.domain.account.usecase.SaveAccountUseCase
import io.github.toanhp123.finote.domain.account.usecase.SetAccountIgnoredUseCase
import io.github.toanhp123.finote.domain.account.usecase.SetPrimaryAccountUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.FakeParserRuleRepository
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.ReconcileBalanceUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.settings.accountedit.logic.NewAccountDraft
import io.github.toanhp123.finote.ui.settings.accountedit.logic.toDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AccountEditViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC)
    private val repo = FakeAccountRepository()
    private val mergeRepo = FakeAccountMergeRepository()
    private val txRepo = FakeTransactionRepository(activeAccounts = setOf("a"))
    private val rules = FakeParserRuleRepository()

    private fun bank(
        id: String,
        type: AccountType = AccountType.BANK,
    ) = testAccount(id = id, name = id, type = type).copy(bankPackageName = "vcb")

    private fun viewModel(
        id: String?,
        mergeTargetId: String? = null,
        vararg accounts: Account,
    ): AccountEditViewModel {
        accounts.forEach { repo.stored[it.id] = it }
        val tx = FakeTransactionRunner()
        return AccountEditViewModel(
            id,
            mergeTargetId,
            ObserveAccountsUseCase(repo),
            ObserveAccountIdentifiersUseCase(repo),
            ObserveBankAppsUseCase(rules),
            CreateAccountUseCase(repo, tx, clock),
            SaveAccountUseCase(repo, tx, clock),
            SetPrimaryAccountUseCase(repo, tx, clock),
            ArchiveAccountUseCase(repo, tx, clock),
            DeleteAccountUseCase(repo, tx, clock),
            SetAccountIgnoredUseCase(repo, tx, clock),
            AddAccountIdentifierUseCase(repo, mergeRepo, tx),
            RemoveAccountIdentifierUseCase(repo, tx),
            MergeAccountsUseCase(repo, mergeRepo, tx, clock),
            CountMergeMovesUseCase(repo),
            GetMergeDroppedStartUseCase(mergeRepo),
            CountTrashableTransactionsUseCase(repo),
            ReconcileBalanceUseCase(txRepo, FakeDriftAcknowledgementRepository(), tx, clock),
            isDebuggable = true,
        )
    }

    /** The first effect that [action] causes. */
    private suspend fun TestScope.effectOf(
        vm: AccountEditViewModel,
        action: () -> Unit,
    ): AccountEditEffect {
        var effect: AccountEditEffect? = null
        vm.effects.test {
            action()
            runCurrent()
            effect = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return checkNotNull(effect)
    }

    @Test
    fun `create mode starts with an empty draft and no stored account`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(null)
            runCurrent()

            assertEquals(NewAccountDraft, vm.state.value.draft)
            assertNull(vm.state.value.stored)
        }

    @Test
    fun `bank apps come from the active parser rules`() =
        runTest(mainRule.dispatcher) {
            rules.stored.value = listOf(testRule(id = "r1", packageName = "vcb", bankName = "VCB"))
            val vm = viewModel(null)
            runCurrent()

            assertEquals(
                listOf("vcb" to "VCB"),
                vm.state.value.bankApps
                    .map { it.packageName to it.bankName },
            )
        }

    @Test
    fun `create saves a new account and closes the screen`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(null)
            runCurrent()
            vm.onIntent(AccountEditIntent.DraftChanged(NewAccountDraft.copy(name = " Wallet ", startBalance = 500L)))

            val effect = effectOf(vm) { vm.onIntent(AccountEditIntent.SaveClicked) }

            assertEquals(AccountEditEffect.Close(null), effect)
            val saved = repo.stored.values.single()
            assertEquals("Wallet", saved.name)
            assertEquals(500L to true, saved.initialBalance to saved.isInitialized)
            assertFalse(vm.state.value.isSaving)
        }

    @Test
    fun `create with a bank app and an empty start stays provisional`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(null)
            runCurrent()
            vm.onIntent(AccountEditIntent.DraftChanged(NewAccountDraft.copy(name = "Card", bankPackageName = "vcb")))

            effectOf(vm) { vm.onIntent(AccountEditIntent.SaveClicked) }

            val saved = repo.stored.values.single()
            assertEquals(0L to false, saved.initialBalance to saved.isInitialized)
        }

    @Test
    fun `create with a blank name emits BlankName and writes nothing`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(null)
            runCurrent()

            val effect = effectOf(vm) { vm.onIntent(AccountEditIntent.SaveClicked) }

            assertEquals(AccountEditEffect.ShowError(AccountError.BlankName), effect)
            assertEquals(0, repo.writes)
            assertFalse(vm.state.value.isSaving)
        }

    @Test
    fun `live update keeps the typed name`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a"))
            runCurrent()
            val typed = checkNotNull(vm.state.value.draft).copy(name = "Abc")
            vm.onIntent(AccountEditIntent.DraftChanged(typed))

            repo.stored["a"] = bank("a").copy(currentBalance = 42L)
            repo.notifyChanged()
            runCurrent()

            assertEquals(
                "Abc",
                vm.state.value.draft
                    ?.name,
            )
            assertEquals(
                42L,
                vm.state.value.stored
                    ?.currentBalance,
            )
        }

    @Test
    fun `an id that never loads closes the screen as GONE`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("missing", null)

            val effect = effectOf(vm) {}

            assertEquals(AccountEditEffect.Close(CloseReason.GONE), effect)
        }

    @Test
    fun `toString of balances and masks stays out of logs`() {
        val merge = AccountEditDialog.Merge(bank("a"), bank("b"), count = 1, droppedStart = 987_654L)
        val reconcile = AccountEditIntent.ReconcileSubmitted(actual = 123_456L)
        val addMask = AccountEditIntent.AddIdentifierSubmitted(IdentifierKind.ACCOUNT, mask = "4321")

        assertFalse(merge.toString().contains("987654"))
        assertFalse(reconcile.toString().contains("123456"))
        assertFalse(addMask.toString().contains("4321"))
    }

    @Test
    fun `account gone closes the screen and save writes nothing`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a"))
            runCurrent()
            vm.onIntent(AccountEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(name = "Abc")))
            val writes = repo.writes

            vm.effects.test {
                repo.stored["a"] = bank("a").copy(deletedAt = 1L)
                repo.notifyChanged()
                runCurrent()
                assertEquals(AccountEditEffect.Close(CloseReason.GONE), awaitItem())

                vm.onIntent(AccountEditIntent.SaveClicked)
                runCurrent()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(writes, repo.writes)
            assertNull(vm.state.value.stored)
        }

    @Test
    fun `save of an unchanged provisional start sends null start`() =
        runTest(mainRule.dispatcher) {
            val provisional = bank("a").copy(initialBalance = 1_000L, isInitialized = false)
            val vm = viewModel("a", null, provisional)
            runCurrent()
            vm.onIntent(AccountEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(name = "New")))

            val effect = effectOf(vm) { vm.onIntent(AccountEditIntent.SaveClicked) }

            assertEquals(AccountEditEffect.Close(null), effect)
            val saved = checkNotNull(repo.stored["a"])
            assertEquals("New", saved.name)
            // A rename alone keeps a provisional account provisional.
            assertEquals(1_000L to false, saved.initialBalance to saved.isInitialized)
        }

    @Test
    fun `a rename after a booking moved the provisional start keeps it provisional`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a").copy(initialBalance = 1_000L, isInitialized = false))
            runCurrent()
            // A booking re-derives the provisional start while the form is open.
            repo.stored["a"] = bank("a").copy(initialBalance = 1_500L, isInitialized = false)
            repo.notifyChanged()
            runCurrent()
            vm.onIntent(AccountEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(name = "New")))

            effectOf(vm) { vm.onIntent(AccountEditIntent.SaveClicked) }

            val saved = checkNotNull(repo.stored["a"])
            assertEquals("New", saved.name)
            assertEquals(1_500L to false, saved.initialBalance to saved.isInitialized)
        }

    @Test
    fun `save of a changed provisional start fixes it`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a").copy(initialBalance = 1_000L, isInitialized = false))
            runCurrent()
            vm.onIntent(AccountEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(startBalance = 2_000L)))

            effectOf(vm) { vm.onIntent(AccountEditIntent.SaveClicked) }

            val saved = checkNotNull(repo.stored["a"])
            assertEquals(2_000L to true, saved.initialBalance to saved.isInitialized)
        }

    @Test
    fun `the start of a fixed account stays read only`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a").copy(initialBalance = 1_000L, isInitialized = true))
            runCurrent()

            vm.onIntent(AccountEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(startBalance = 9L)))

            assertEquals(
                1_000L,
                vm.state.value.draft
                    ?.startBalance,
            )
        }

    @Test
    fun `taken mask opens the merge dialog with the holder`() =
        runTest(mainRule.dispatcher) {
            repo.identifiers += AccountIdentifier("vcb", IdentifierKind.ACCOUNT, "1234", "h")
            mergeRepo.droppedStarts["a" to "h"] = 700L
            val vm = viewModel("a", null, bank("a"), bank("h"))
            runCurrent()

            vm.onIntent(AccountEditIntent.AddIdentifierClicked)
            vm.onIntent(AccountEditIntent.AddIdentifierSubmitted(IdentifierKind.ACCOUNT, "1234"))
            runCurrent()

            val dialog = vm.state.value.dialog as AccountEditDialog.Merge
            assertEquals("a" to "h", dialog.source.id to dialog.target.id)
            assertEquals(700L, dialog.droppedStart)
        }

    @Test
    fun `taken mask saves a dirty draft before it offers the merge`() =
        runTest(mainRule.dispatcher) {
            repo.identifiers += AccountIdentifier("vcb", IdentifierKind.ACCOUNT, "1234", "h")
            val vm = viewModel("a", null, bank("a"), bank("h"))
            runCurrent()
            vm.onIntent(AccountEditIntent.DraftChanged(checkNotNull(vm.state.value.draft).copy(name = "Renamed")))

            vm.onIntent(AccountEditIntent.AddIdentifierSubmitted(IdentifierKind.ACCOUNT, "1234"))
            runCurrent()

            assertEquals("Renamed", repo.stored["a"]?.name)
            assertEquals("Renamed", (vm.state.value.dialog as AccountEditDialog.Merge).source.name)
        }

    @Test
    fun `taken mask of an account that cannot merge is a message`() =
        runTest(mainRule.dispatcher) {
            val holder = bank("h", type = AccountType.CREDIT_CARD)
            repo.identifiers += AccountIdentifier("vcb", IdentifierKind.ACCOUNT, "1234", "h")
            val vm = viewModel("a", null, bank("a"), holder)
            runCurrent()

            val effect =
                effectOf(vm) {
                    vm.onIntent(AccountEditIntent.AddIdentifierSubmitted(IdentifierKind.ACCOUNT, "1234"))
                }

            assertEquals(
                AccountEditEffect.ShowError(AccountError.IdentifierTaken(holder, MergeRejection.DIFFERENT_TYPE)),
                effect,
            )
            assertNull(vm.state.value.dialog)
        }

    @Test
    fun `a free mask is added and the input closes`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a"))
            runCurrent()
            vm.onIntent(AccountEditIntent.AddIdentifierClicked)

            vm.onIntent(AccountEditIntent.AddIdentifierSubmitted(IdentifierKind.ACCOUNT, "0042"))
            runCurrent()

            assertNull(vm.state.value.dialog)
            assertEquals(
                listOf("0042"),
                vm.state.value.identifiers
                    .map { it.mask },
            )
        }

    @Test
    fun `removing the last mask asks first`() =
        runTest(mainRule.dispatcher) {
            val mask = AccountIdentifier("vcb", IdentifierKind.ACCOUNT, "1234", "a")
            repo.identifiers += mask
            val vm = viewModel("a", null, bank("a"))
            runCurrent()

            vm.onIntent(AccountEditIntent.RemoveIdentifierClicked(mask))
            runCurrent()

            assertEquals(AccountEditDialog.RemoveLastIdentifier(mask), vm.state.value.dialog)
            assertEquals(listOf(mask), repo.identifiers)

            vm.onIntent(AccountEditIntent.RemoveIdentifierConfirmed)
            runCurrent()

            assertTrue(repo.identifiers.isEmpty())
            assertNull(vm.state.value.dialog)
        }

    @Test
    fun `removing one of two masks needs no question`() =
        runTest(mainRule.dispatcher) {
            val first = AccountIdentifier("vcb", IdentifierKind.ACCOUNT, "1234", "a")
            repo.identifiers += listOf(first, AccountIdentifier("vcb", IdentifierKind.CARD, "5678", "a"))
            val vm = viewModel("a", null, bank("a"))
            runCurrent()

            vm.onIntent(AccountEditIntent.RemoveIdentifierClicked(first))
            runCurrent()

            assertNull(vm.state.value.dialog)
            assertEquals(listOf("5678"), repo.identifiers.map { it.mask })
        }

    @Test
    fun `mergeTargetId opens the merge dialog on load`() =
        runTest(mainRule.dispatcher) {
            repo.liveCounts["a"] = 4
            val vm = viewModel("a", "b", bank("a"), bank("b"))
            runCurrent()

            val dialog = vm.state.value.dialog as AccountEditDialog.Merge
            assertEquals(Triple("a", "b", 4), Triple(dialog.source.id, dialog.target.id, dialog.count))
        }

    @Test
    fun `merge picker lists live accounts of the same bank and type`() =
        runTest(mainRule.dispatcher) {
            val archived = bank("old").copy(isArchived = true)
            val vm = viewModel("a", null, bank("a"), bank("b"), bank("c", AccountType.CREDIT_CARD), archived)
            runCurrent()

            vm.onIntent(AccountEditIntent.MergeClicked)

            val picker = vm.state.value.dialog as AccountEditDialog.MergeTargetPicker
            assertEquals(listOf("b"), picker.targets.map { it.id })

            vm.onIntent(AccountEditIntent.MergeTargetPicked("b"))
            runCurrent()

            assertEquals("b", (vm.state.value.dialog as AccountEditDialog.Merge).target.id)
        }

    @Test
    fun `merge with no candidate says so and opens nothing`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a"), bank("c", AccountType.CREDIT_CARD))
            runCurrent()

            val effect = effectOf(vm) { vm.onIntent(AccountEditIntent.MergeClicked) }

            assertEquals(AccountEditEffect.ShowNoMergeTarget, effect)
            assertNull(vm.state.value.dialog)
        }

    @Test
    fun `confirmed merge closes once as MERGED`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", "b", bank("a"), bank("b"))
            runCurrent()

            vm.effects.test {
                vm.onIntent(AccountEditIntent.MergeConfirmed)
                vm.onIntent(AccountEditIntent.MergeConfirmed)
                runCurrent()

                assertEquals(AccountEditEffect.Close(CloseReason.MERGED), awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(Triple("a", "b", 5_000L)), mergeRepo.merges)
        }

    @Test
    fun `confirmed delete closes once as DELETED, not as GONE`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a"))
            runCurrent()
            vm.onIntent(AccountEditIntent.DeleteClicked)

            vm.effects.test {
                vm.onIntent(AccountEditIntent.DeleteConfirmed)
                runCurrent()

                assertEquals(AccountEditEffect.Close(CloseReason.DELETED), awaitItem())
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertNotNull(repo.stored["a"]?.deletedAt)
        }

    @Test
    fun `delete of an account with transactions is refused and the screen stays`() =
        runTest(mainRule.dispatcher) {
            repo.liveCounts["a"] = 2
            val vm = viewModel("a", null, bank("a"))
            runCurrent()
            vm.onIntent(AccountEditIntent.DeleteClicked)

            val effect = effectOf(vm) { vm.onIntent(AccountEditIntent.DeleteConfirmed) }

            assertEquals(AccountEditEffect.ShowError(AccountError.HasTransactions), effect)
            assertNull(repo.stored["a"]?.deletedAt)
        }

    @Test
    fun `each account error maps to its own effect`() =
        runTest(mainRule.dispatcher) {
            val cash = testAccount(id = SYSTEM_CASH_ACCOUNT_ID, type = AccountType.CASH)
            val vm = viewModel(SYSTEM_CASH_ACCOUNT_ID, null, cash)
            runCurrent()

            // The only usable account cannot be archived.
            assertEquals(
                AccountEditEffect.ShowError(AccountError.LastUsableAccount),
                effectOf(vm) { vm.onIntent(AccountEditIntent.ArchiveToggled) },
            )
            // The seeded cash account cannot be deleted.
            vm.onIntent(AccountEditIntent.DeleteClicked)
            assertEquals(
                AccountEditEffect.ShowError(AccountError.SystemAccount),
                effectOf(vm) { vm.onIntent(AccountEditIntent.DeleteConfirmed) },
            )
            // Cash has no bank app, so no masked number.
            assertEquals(
                AccountEditEffect.ShowError(AccountError.NoBankApp),
                effectOf(vm) {
                    vm.onIntent(AccountEditIntent.AddIdentifierSubmitted(IdentifierKind.ACCOUNT, "1234"))
                },
            )
        }

    @Test
    fun `mask and star errors map to their own effect`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a").copy(isArchived = true))
            runCurrent()

            assertEquals(
                AccountEditEffect.ShowError(AccountError.Unusable),
                effectOf(vm) { vm.onIntent(AccountEditIntent.StarToggled) },
            )
            assertEquals(
                AccountEditEffect.ShowError(AccountError.Archived),
                effectOf(vm) {
                    vm.onIntent(AccountEditIntent.AddIdentifierSubmitted(IdentifierKind.ACCOUNT, "1234"))
                },
            )
            repo.stored["a"] = bank("a")
            repo.notifyChanged()
            runCurrent()
            assertEquals(
                AccountEditEffect.ShowError(AccountError.InvalidMask),
                effectOf(vm) { vm.onIntent(AccountEditIntent.AddIdentifierSubmitted(IdentifierKind.ACCOUNT, "12")) },
            )
        }

    @Test
    fun `merge rejection emits its own error and the screen stays`() =
        runTest(mainRule.dispatcher) {
            mergeRepo.transferCounts[setOf("a", "b")] = 1
            val vm = viewModel("a", "b", bank("a"), bank("b"))
            runCurrent()

            val effect = effectOf(vm) { vm.onIntent(AccountEditIntent.MergeConfirmed) }

            assertEquals(AccountEditEffect.ShowError(AccountError.MergeRejected(MergeRejection.HAS_TRANSFER)), effect)
            assertTrue(mergeRepo.merges.isEmpty())
        }

    @Test
    fun `star moves to this account`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a"), bank("b").copy(isPrimary = true))
            runCurrent()

            vm.onIntent(AccountEditIntent.StarToggled)
            runCurrent()

            assertTrue(
                vm.state.value.stored
                    ?.isPrimary == true,
            )
            assertFalse(repo.stored.getValue("b").isPrimary)
        }

    @Test
    fun `ignore with the trash box ticked trashes, follow again undoes it`() =
        runTest(mainRule.dispatcher) {
            repo.trashableCounts["a"] = 3
            val vm = viewModel("a", null, bank("a"))
            runCurrent()

            vm.onIntent(AccountEditIntent.IgnoreClicked)
            runCurrent()
            assertEquals(AccountEditDialog.Ignore(trashableCount = 3), vm.state.value.dialog)
            vm.onIntent(AccountEditIntent.TrashToggled(isChecked = true))
            vm.onIntent(AccountEditIntent.IgnoreConfirmed)
            runCurrent()
            vm.onIntent(AccountEditIntent.FollowAgainClicked)
            runCurrent()

            assertEquals(
                listOf(
                    FakeAccountRepository.IgnoreCall("a", ignored = true, trash = true, now = 5_000L),
                    FakeAccountRepository.IgnoreCall("a", ignored = false, trash = false, now = 5_000L),
                ),
                repo.ignores,
            )
            assertNull(vm.state.value.dialog)
        }

    @Test
    fun `reconcile books the correction and closes the input`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a").copy(isInitialized = true))
            runCurrent()
            vm.onIntent(AccountEditIntent.ReconcileClicked)

            vm.onIntent(AccountEditIntent.ReconcileSubmitted(actual = 9_000L))
            runCurrent()

            assertEquals(listOf(9_000L), txRepo.reconcileCalls.map { it.actualBalance })
            assertNull(vm.state.value.dialog)
        }

    @Test
    fun `reconcile of an unusable account emits ReconcileFailed`() =
        runTest(mainRule.dispatcher) {
            // "z" is not among the accounts the transaction repository counts as usable.
            val vm = viewModel("z", null, bank("z"))
            runCurrent()

            val effect = effectOf(vm) { vm.onIntent(AccountEditIntent.ReconcileSubmitted(actual = 9_000L)) }

            assertEquals(AccountEditEffect.ShowReconcileFailed(TransactionError.AccountUnusable), effect)
        }

    @Test
    fun `dismissed dialog clears and the draft is the stored account`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel("a", null, bank("a"))
            runCurrent()
            vm.onIntent(AccountEditIntent.DeleteClicked)

            vm.onIntent(AccountEditIntent.DialogDismissed)

            assertNull(vm.state.value.dialog)
            assertEquals(bank("a").toDraft(), vm.state.value.draft)
        }
}

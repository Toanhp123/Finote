package io.github.toanhp123.finote.ui.settings.accountedit.logic

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.ui.common.theme.color.CategoryPaletteArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountDraftTest {
    private val stored = testAccount(id = "a", name = "Old", initialBalance = 1_000L)

    @Test
    fun `a new draft starts with a color that the picker offers`() {
        assertTrue(NewAccountDraft.color in CategoryPaletteArgb)
        assertNotEquals(0, NewAccountDraft.color)
    }

    @Test
    fun `a draft of the stored account is not dirty, a changed field is`() {
        val draft = stored.toDraft()

        assertFalse(draft.isDirtyAgainst(stored))
        assertTrue(draft.copy(name = "New").isDirtyAgainst(stored))
        assertTrue(draft.copy(startBalance = 2_000L).isDirtyAgainst(stored))
    }

    @Test
    fun `toAccount on a base keeps type, balances and flags and takes the display fields`() {
        val base = stored.copy(currentBalance = 7_000L, isInitialized = true, bankPackageName = "vcb")
        val draft =
            base.toDraft().copy(
                name = "  New  ",
                icon = "home",
                color = 5,
                isExcludedFromNetWorth = true,
                type = AccountType.WALLET,
                bankPackageName = "mb",
                startBalance = 99L,
            )

        val account = draft.toAccount(base, "a")

        assertEquals(base.copy(name = "New", icon = "home", color = 5, isExcludedFromNetWorth = true), account)
    }

    @Test
    fun `toAccount takes the first bank app of a base that has none`() {
        val draft = stored.toDraft().copy(bankPackageName = "mb")

        assertEquals("mb", draft.toAccount(stored, "a").bankPackageName)
    }

    @Test
    fun `toAccount without a base builds a VND account whose balance is the start`() {
        val draft = NewAccountDraft.copy(name = "Wallet", startBalance = 5_000L)

        val account = draft.toAccount(base = null, id = "new")

        assertEquals("new", account.id)
        assertEquals("VND", account.currency)
        assertEquals(5_000L to 5_000L, account.initialBalance to account.currentBalance)
        assertEquals(0L, NewAccountDraft.toAccount(null, "x").initialBalance)
    }

    @Test
    fun `startToSave sends only a changed provisional start`() {
        val provisional = stored.copy(isInitialized = false)
        val fixed = stored.copy(isInitialized = true)

        val loaded = stored.initialBalance

        assertEquals(2_000L, stored.toDraft().copy(startBalance = 2_000L).startToSave(provisional, loaded))
        assertNull(provisional.toDraft().startToSave(provisional, loaded))
        assertNull(stored.toDraft().copy(startBalance = 2_000L).startToSave(fixed, loaded))
        assertNull(stored.toDraft().copy(startBalance = null).startToSave(provisional, loaded))
        // The stored start moved after the draft was filled: an untouched field is still no change.
        val moved = provisional.copy(initialBalance = 9L)
        assertNull(moved.toDraft().copy(startBalance = loaded).startToSave(moved, loaded))
    }

    @Test
    fun `an existing account keeps its type, its bank app and a fixed start`() {
        val fixed = stored.copy(isInitialized = true, bankPackageName = "vcb")
        val previous = fixed.toDraft()

        val changed = previous.copy(type = AccountType.WALLET, bankPackageName = "mb", startBalance = 9L)

        assertEquals(previous, changed.lockedBy(fixed, previous))
    }

    @Test
    fun `a provisional start and a missing bank app stay editable`() {
        val provisional = stored.copy(isInitialized = false, bankPackageName = null)
        val previous = provisional.toDraft()

        val changed = previous.copy(bankPackageName = "mb", startBalance = 9L).lockedBy(provisional, previous)

        assertEquals("mb" to 9L, changed.bankPackageName to changed.startBalance)
    }

    @Test
    fun `a cash type on an existing account does not drop the bank app`() {
        val noBank = stored.copy(bankPackageName = null)
        val previous = noBank.toDraft()

        val changed = previous.copy(type = AccountType.CASH, bankPackageName = "mb").lockedBy(noBank, previous)

        assertEquals("mb" to AccountType.BANK, changed.bankPackageName to changed.type)
        val withBank = stored.copy(bankPackageName = "vcb")
        assertEquals(
            "vcb",
            withBank
                .toDraft()
                .copy(type = AccountType.CASH)
                .lockedBy(withBank, withBank.toDraft())
                .bankPackageName,
        )
    }

    @Test
    fun `a new cash account drops its bank app`() {
        val draft = NewAccountDraft.copy(type = AccountType.CASH, bankPackageName = "vcb")

        assertNull(draft.lockedBy(stored = null, previous = NewAccountDraft).bankPackageName)
    }
}

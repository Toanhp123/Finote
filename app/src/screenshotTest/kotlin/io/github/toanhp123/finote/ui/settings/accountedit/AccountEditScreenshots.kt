package io.github.toanhp123.finote.ui.settings.accountedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.BankApp
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.TallScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accountedit.logic.NewAccountDraft
import io.github.toanhp123.finote.ui.settings.accountedit.logic.toDraft

private val apps =
    listOf(
        BankApp("vcb", "Vietcombank"),
        BankApp("mb", "MB Bank"),
        BankApp("tcb", "Techcombank"),
    )

private fun account(
    id: String,
    name: String,
    balance: Long,
    type: AccountType = AccountType.BANK,
    bank: String? = "vcb",
    isInitialized: Boolean = true,
    isArchived: Boolean = false,
    isPrimary: Boolean = false,
    nameKey: String? = null,
) = Account(
    id = id,
    name = name,
    nameKey = nameKey,
    type = type,
    initialBalance = balance,
    currentBalance = balance,
    currency = "VND",
    icon = "payments",
    color = 0xFF1565C0.toInt(),
    sortOrder = 0,
    isExcludedFromNetWorth = false,
    isArchived = isArchived,
    isInitialized = isInitialized,
    createdAt = 1L,
    updatedAt = 1L,
    deletedAt = null,
    bankPackageName = bank,
    isPrimary = isPrimary,
)

private fun masks(vararg digits: String) =
    digits.mapIndexed { index, mask ->
        AccountIdentifier("vcb", if (index == 0) IdentifierKind.ACCOUNT else IdentifierKind.CARD, mask, "vcb1")
    }

private fun editing(
    stored: Account,
    identifiers: List<AccountIdentifier> = emptyList(),
    dialog: AccountEditDialog? = null,
) = AccountEditState(
    draft = stored.toDraft(),
    stored = stored,
    identifiers = identifiers,
    bankApps = apps,
    dialog = dialog,
)

private val provisional = account("vcb1", "VCB ••1234", 12_450_000L, isInitialized = false)
private val initialized = account("vcb1", "VCB ••1234", 12_450_000L, isPrimary = true)
private val archived = account("vcb1", "VCB ••1234", 12_450_000L, isArchived = true)
private val cash =
    account("sys_cash", "Cash", 1_250_000L, AccountType.CASH, bank = null, isInitialized = false, nameKey = "sys_cash")

@PreviewTest
@TallScreenPreviews
@Composable
private fun AccountEditCreatePreview() = Screen(AccountEditState(draft = NewAccountDraft, bankApps = apps))

@PreviewTest
@TallScreenPreviews
@Composable
private fun AccountEditProvisionalPreview() = Screen(editing(provisional, masks("1234")))

@PreviewTest
@TallScreenPreviews
@Composable
private fun AccountEditInitializedPreview() = Screen(editing(initialized, masks("1234", "5678")))

@PreviewTest
@TallScreenPreviews
@Composable
private fun AccountEditArchivedPreview() = Screen(editing(archived, masks("1234")))

@PreviewTest
@TallScreenPreviews
@Composable
private fun AccountEditSeededCashPreview() = Screen(editing(cash.copy(isPrimary = true)))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountEditAddIdentifierPreview() =
    Screen(editing(initialized, masks("1234"), dialog = AccountEditDialog.AddIdentifier))

@Composable
private fun Screen(state: AccountEditState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            AccountEditScreen(state = state, onIntent = {}, onBack = {}, modifier = Modifier.statusBarsPadding())
        }
    }
}

package io.github.toanhp123.finote.ui.settings.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.TallScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accounts.logic.groupAccounts

private fun account(
    id: String,
    name: String,
    balance: Long,
    sortOrder: Int,
    bank: String? = "vcb",
    isIgnored: Boolean = false,
    isArchived: Boolean = false,
    nameKey: String? = null,
) = Account(
    id = id,
    name = name,
    nameKey = nameKey,
    type = AccountType.BANK,
    initialBalance = balance,
    currentBalance = balance,
    currency = "VND",
    icon = "account_balance",
    color = 0,
    sortOrder = sortOrder,
    isExcludedFromNetWorth = false,
    isArchived = isArchived,
    isInitialized = true,
    createdAt = 1L,
    updatedAt = 1L,
    deletedAt = null,
    bankPackageName = bank,
    isIgnored = isIgnored,
)

private val bankNames = mapOf("vcb" to "Vietcombank", "mb" to "MB Bank", "tcb" to "Techcombank")

private val cash = account("cash", "Cash", 1_250_000L, sortOrder = 0, bank = null, nameKey = "sys_cash")
private val vcb1 = account("vcb1", "VCB ••1234", 12_450_000L, sortOrder = 1)
private val vcb2 = account("vcb2", "VCB ••5678", 3_200_000L, sortOrder = 2)
private val mb1 = account("mb1", "MB ••9012", 400_000L, sortOrder = 3, bank = "mb", isIgnored = true)
private val mb2 = account("mb2", "MB ••3456", 800_000L, sortOrder = 4, bank = "mb")
private val tcb = account("tcb", "TCB ••7777", 5_000_000L, sortOrder = 5, bank = "tcb")
private val old = account("old", "Old savings", 2_000_000L, sortOrder = 6, bank = "tcb", isArchived = true)
private val oldCard = account("card", "Old card", -300_000L, sortOrder = 7, bank = null, isArchived = true)
private val everything = listOf(cash, vcb1, vcb2, mb1, mb2, tcb, old, oldCard)
private val suggested = mapOf("vcb1" to "vcb2", "vcb2" to "vcb1")

private val listed =
    AccountsSettingsState(
        items = groupAccounts(everything, bankNames),
        archived = listOf(old, oldCard),
        suggestions = suggested,
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsListPreview() = Screen(listed)

@PreviewTest
@TallScreenPreviews
@Composable
private fun AccountsGroupExpandedPreview() = Screen(listed.copy(expanded = setOf("vcb", "mb")))

@PreviewTest
@TallScreenPreviews
@Composable
private fun AccountsArchivedPreview() = Screen(listed.copy(isArchivedExpanded = true))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsReorderPreview() = Screen(listed.copy(reorder = listed.items))

@Composable
private fun Screen(state: AccountsSettingsState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            AccountsSettingsScreen(state = state, onIntent = {}, onBack = {}, modifier = Modifier.statusBarsPadding())
        }
    }
}

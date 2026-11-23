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
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private fun account(
    id: String,
    name: String,
    balance: Long,
    bank: String? = "vcb",
    isIgnored: Boolean = false,
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
    sortOrder = 0,
    isExcludedFromNetWorth = false,
    isArchived = false,
    isInitialized = true,
    createdAt = 1L,
    updatedAt = 1L,
    deletedAt = null,
    bankPackageName = bank,
    isIgnored = isIgnored,
)

private val cash = account("cash", "Cash", 1_250_000L, bank = null, nameKey = "sys_cash")
private val vcb1 = account("vcb1", "VCB ••1234", 12_450_000L)
private val vcb2 = account("vcb2", "VCB ••5678", 3_200_000L)
private val mb = account("mb", "MB ••9012", 0L, bank = "mb", isIgnored = true)
private val mb2 = account("mb2", "MB ••3456", 800_000L, bank = "mb")
private val listed = listOf(cash, vcb1, vcb2, mb)

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsListPreview() = Screen(AccountsSettingsState(accounts = listed))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsSheetPreview() =
    Screen(AccountsSettingsState(accounts = listed, selected = vcb1, mergeTargets = listOf(vcb2)))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsSheetNoTargetPreview() = Screen(AccountsSettingsState(accounts = listed, selected = vcb2))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsSheetIgnoredPreview() =
    Screen(AccountsSettingsState(accounts = listed, selected = mb, mergeTargets = listOf(mb2)))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsMergeDialogPreview() =
    Screen(AccountsSettingsState(accounts = listed, dialog = AccountsDialog.Merge(vcb1, vcb2, count = 12)))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsMergeDroppedStartDialogPreview() =
    Screen(
        AccountsSettingsState(
            accounts = listed,
            dialog = AccountsDialog.Merge(vcb1, vcb2, count = 12, droppedStart = 5_000_000L),
        ),
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsIgnoreDialogPreview() =
    Screen(
        AccountsSettingsState(
            accounts = listed,
            dialog = AccountsDialog.Ignore(trashableCount = 8, shouldTrash = true),
        ),
    )

@Composable
private fun Screen(state: AccountsSettingsState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            AccountsSettingsScreen(state = state, onIntent = {}, onBack = {}, modifier = Modifier.statusBarsPadding())
        }
    }
}

package io.github.toanhp123.finote.ui.settings.accounts

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.overlay.DialogCard
import io.github.toanhp123.finote.ui.common.component.overlay.SheetSurface
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val PHONE = "spec:width=360dp,height=640dp"
private const val SCRIM_ALPHA = 0.6f

@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", device = PHONE, fontScale = 2f)
private annotation class ScreenPreviews

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
private fun AccountsListPreview() = Backdrop(alignment = null) {}

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsSheetPreview() =
    SheetFrame(AccountsSettingsState(accounts = listed, selected = vcb1, mergeTargets = listOf(vcb2)))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsSheetNoTargetPreview() = SheetFrame(AccountsSettingsState(accounts = listed, selected = vcb2))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsSheetIgnoredPreview() =
    SheetFrame(AccountsSettingsState(accounts = listed, selected = mb, mergeTargets = listOf(mb2)))

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsMergeDialogPreview() =
    DialogFrame(
        title = stringResource(R.string.accounts_merge_dialog_title, vcb1.name, vcb2.name),
        text = pluralStringResource(R.plurals.accounts_merge_dialog_text, 12, 12),
        confirm = stringResource(R.string.accounts_merge_confirm),
    ) { MergeBalances(vcb1, vcb2) }

@PreviewTest
@ScreenPreviews
@Composable
private fun AccountsIgnoreDialogPreview() =
    DialogFrame(
        title = stringResource(R.string.accounts_ignore_dialog_title),
        text = stringResource(R.string.accounts_ignore_dialog_text),
        confirm = stringResource(R.string.accounts_ignore_confirm),
    ) { TrashOption(count = 8, isChecked = true, onCheckedChange = {}) }

/** layoutlib has no second window: the sheet is drawn as its content over the scrim. */
@Composable
private fun SheetFrame(state: AccountsSettingsState) {
    Backdrop(Alignment.BottomCenter) {
        SheetSurface(contentPadding = PaddingValues(FinoteTheme.spacing.lg)) {
            AccountSheetContent(state, onIntent = {})
        }
    }
}

/** Same for the dialog: the card is drawn over the scrim, with the buttons the real dialog has. */
@Composable
private fun DialogFrame(
    title: String,
    text: String,
    confirm: String,
    content: @Composable () -> Unit,
) {
    Backdrop(Alignment.Center) {
        DialogCard(
            title = title,
            text = text,
            dismiss = {
                FinoteButton(stringResource(R.string.action_cancel), onClick = {}, style = FinoteButtonStyle.Ghost)
            },
            confirm = { FinoteButton(confirm, onClick = {}, style = FinoteButtonStyle.Danger) },
            modifier = Modifier.padding(FinoteTheme.spacing.xl),
            content = { content() },
        )
    }
}

@Composable
private fun Backdrop(
    alignment: Alignment?,
    overlay: @Composable () -> Unit,
) {
    FinoteTheme(dynamicColor = false) {
        Box {
            Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
                AccountsSettingsScreen(state = AccountsSettingsState(accounts = listed), onIntent = {}, onBack = {})
            }
            if (alignment != null) {
                Box(
                    modifier = Modifier.fillMaxSize().background(FinoteTheme.scheme.scrim.copy(alpha = SCRIM_ALPHA)),
                    contentAlignment = alignment,
                ) { overlay() }
            }
        }
    }
}

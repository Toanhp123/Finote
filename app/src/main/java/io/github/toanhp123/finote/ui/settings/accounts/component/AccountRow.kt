package io.github.toanhp123.finote.ui.settings.accounts.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsIntent

/**
 * One account. An ignored or archived one is muted. With a [partner] the row carries the merge suggestion: the user
 * can review the merge or say the two are not the same account.
 */
@Composable
fun AccountRow(
    account: Account,
    onIntent: (AccountsSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
    partner: Account? = null,
) {
    val isMuted = account.isIgnored || account.isArchived
    Column(modifier = modifier) {
        FinoteListRow(
            title = displayName(account.name, account.nameKey),
            modifier = if (isMuted) Modifier.alpha(FinoteTheme.colors.mutedAlpha) else Modifier,
            subtitle = if (account.isIgnored) stringResource(R.string.accounts_ignored_label) else null,
            trailing = { MoneyText(amount = account.currentBalance, kind = MoneyKind.Neutral) },
            onClick = { onIntent(AccountsSettingsIntent.AccountClicked(account.id)) },
        )
        if (partner != null) {
            SuggestionStrip(
                partnerName = displayName(partner.name, partner.nameKey),
                onReview = { onIntent(AccountsSettingsIntent.SuggestionClicked(account.id)) },
                onDismiss = { onIntent(AccountsSettingsIntent.SuggestionDismissed(account.id)) },
            )
        }
    }
}

@Composable
private fun SuggestionStrip(
    partnerName: String,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.accounts_suggestion_text, partnerName),
            modifier = Modifier.weight(1f),
            color = FinoteTheme.scheme.primary,
            style = FinoteTheme.typography.material.bodySmall,
        )
        FinoteButton(
            text = stringResource(R.string.accounts_suggestion_review),
            onClick = onReview,
            style = FinoteButtonStyle.Ghost,
        )
        FinoteIconButton(
            iconRes = R.drawable.ic_close,
            contentDescription = stringResource(R.string.accounts_suggestion_dismiss),
            onClick = onDismiss,
        )
    }
}

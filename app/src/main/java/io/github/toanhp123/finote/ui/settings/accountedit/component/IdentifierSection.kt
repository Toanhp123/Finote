package io.github.toanhp123.finote.ui.settings.accountedit.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteSection
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditIntent
import io.github.toanhp123.finote.ui.settings.accountedit.logic.labelRes

/**
 * The masked numbers of a stored non-cash account. An account without a bank app cannot take one yet. An archived
 * account lists its numbers but offers no change.
 */
@Composable
fun IdentifierSection(
    stored: Account,
    identifiers: List<AccountIdentifier>,
    onIntent: (AccountEditIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteSection(title = stringResource(R.string.account_edit_identifiers), modifier = modifier) {
        if (stored.bankPackageName == null) {
            Hint(R.string.account_edit_identifiers_need_bank)
            return@FinoteSection
        }
        if (identifiers.isEmpty()) Hint(R.string.account_edit_identifiers_empty)
        identifiers.forEach { identifier ->
            val title =
                stringResource(
                    R.string.account_edit_identifier_row,
                    stringResource(identifier.kind.labelRes()),
                    identifier.mask,
                )
            FinoteListRow(
                title = title,
                trailing =
                    if (stored.isArchived) {
                        null
                    } else {
                        {
                            FinoteIconButton(
                                iconRes = R.drawable.ic_close,
                                contentDescription = stringResource(R.string.account_edit_identifier_remove_cd, title),
                                onClick = { onIntent(AccountEditIntent.RemoveIdentifierClicked(identifier)) },
                            )
                        }
                    },
            )
        }
        if (!stored.isArchived) {
            FinoteListRow(
                title = stringResource(R.string.account_edit_identifier_add),
                leading = {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                        tint = FinoteTheme.scheme.primary,
                    )
                },
                onClick = { onIntent(AccountEditIntent.AddIdentifierClicked) },
            )
        }
    }
}

@Composable
private fun Hint(
    @StringRes textRes: Int,
) {
    Text(
        text = stringResource(textRes),
        color = FinoteTheme.scheme.onSurfaceVariant,
        style = FinoteTheme.typography.material.bodyMedium,
    )
}

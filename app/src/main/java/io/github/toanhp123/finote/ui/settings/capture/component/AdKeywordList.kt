package io.github.toanhp123.finote.ui.settings.capture.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteSection
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.logic.BankSummary

/**
 * The ad keywords the user taught, one group per bank, each with a delete button. A package with no supported bank
 * left shows its package name. [onDelete] gets the keyword id; nothing is asked first, because deleting is safe: the
 * hidden items come back to the inbox.
 */
@Composable
fun AdKeywordList(
    keywords: Map<String, List<UserIgnoreKeyword>>,
    banks: List<BankSummary>,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteSection(stringResource(R.string.capture_group_ad_keywords), modifier) {
        Column {
            keywords.forEach { (packageName, group) ->
                Text(
                    text = banks.firstOrNull { it.packageName == packageName }?.bankName ?: packageName,
                    modifier = Modifier.padding(vertical = FinoteTheme.spacing.sm).semantics { heading() },
                    color = FinoteTheme.scheme.onSurfaceVariant,
                    style = FinoteTheme.typography.material.labelMedium,
                )
                group.forEach { keyword ->
                    FinoteListRow(
                        title = keyword.keyword,
                        trailing = {
                            FinoteIconButton(
                                iconRes = R.drawable.ic_delete,
                                contentDescription = stringResource(R.string.capture_ad_keyword_delete),
                                onClick = { onDelete(keyword.id) },
                            )
                        },
                    )
                }
            }
        }
    }
}

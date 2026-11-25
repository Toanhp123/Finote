package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.error.KeywordRuleError
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.cashentry.CashEntrySaveError
import io.github.toanhp123.finote.ui.cashentry.logic.RuleOfferState
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val NAME_MAX_LENGTH = 40

/**
 * "Apply to similar?" after a save: the keyword and short name of a rule for the picked category, then Skip and
 * Create (Update when a user rule has this keyword). [saveError] is a storage failure of the last rule write.
 */
@Composable
fun RuleOfferPage(
    offer: RuleOfferState,
    saveError: CashEntrySaveError?,
    onIntent: (CashEntryIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keywordError =
        when (offer.error) {
            null -> if (saveError != null) R.string.cash_entry_error_save else null
            KeywordRuleError.TooShort -> R.string.cash_entry_rule_error_too_short
            KeywordRuleError.Duplicate -> R.string.cash_entry_rule_error_duplicate
            KeywordRuleError.CategoryGone -> R.string.cash_entry_rule_error_category_gone
            KeywordRuleError.NotFound -> R.string.cash_entry_rule_error_not_found
        }
    val saveLabel =
        if (offer.existingRuleId == null) R.string.cash_entry_rule_create else R.string.cash_entry_rule_update
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        Text(
            text = stringResource(R.string.cash_entry_rule_title),
            modifier = Modifier.semantics { heading() },
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleLarge,
        )
        Text(
            text = stringResource(R.string.cash_entry_rule_text),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.bodyMedium,
        )
        FinoteTextField(
            value = offer.keyword,
            onValueChange = { onIntent(CashEntryIntent.RuleKeywordChanged(it)) },
            label = stringResource(R.string.cash_entry_rule_keyword),
            modifier = Modifier.fillMaxWidth(),
            errorText = keywordError?.let { stringResource(it) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        FinoteTextField(
            value = offer.merchantName,
            onValueChange = { onIntent(CashEntryIntent.RuleNameChanged(it)) },
            label = stringResource(R.string.cash_entry_rule_name),
            modifier = Modifier.fillMaxWidth(),
            maxLength = NAME_MAX_LENGTH,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm, Alignment.End),
        ) {
            FinoteButton(
                text = stringResource(R.string.cash_entry_rule_skip),
                onClick = { onIntent(CashEntryIntent.RuleSkipped) },
                style = FinoteButtonStyle.Ghost,
            )
            FinoteButton(
                text = stringResource(saveLabel),
                onClick = { onIntent(CashEntryIntent.RuleSaveClicked) },
                isLoading = offer.isSaving,
            )
        }
    }
}

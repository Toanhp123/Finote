package io.github.toanhp123.finote.ui.settings.keywordruleedit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.BulkApplyDialog
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.keywordruleedit.component.DeleteRuleDialog
import io.github.toanhp123.finote.ui.settings.keywordruleedit.component.RuleCategoryPicker
import io.github.toanhp123.finote.ui.settings.keywordruleedit.logic.KeywordRuleDraft
import io.github.toanhp123.finote.ui.settings.keywordruleedit.logic.messageRes
import kotlinx.coroutines.launch

private const val NAME_MAX_LENGTH = 40

/** [onClose] runs when the screen should leave: after a save or a delete, when the rule is gone, or on Back. */
@Composable
fun KeywordRuleEditRoute(
    viewModel: KeywordRuleEditViewModel,
    snackbarState: FinoteSnackbarState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.category_snack_failed)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            KeywordRuleEditEffect.Close -> onClose()
            KeywordRuleEditEffect.ActionFailed -> scope.launch { snackbarState.show(failed) }
        }
    }
    KeywordRuleEditScreen(state, viewModel::onIntent, onClose, modifier)
}

@Composable
fun KeywordRuleEditScreen(
    state: KeywordRuleEditState,
    onIntent: (KeywordRuleEditIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    // The overlays are drawn after the body in the same Box, so an inline overlay (preview mode) covers the top bar.
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            val isCreate = state.stored == null && draft != null
            FinoteTopBar(
                title =
                    stringResource(
                        if (isCreate) R.string.keyword_rule_edit_title_new else R.string.keyword_rule_edit_title_edit,
                    ),
                onBack = onBack,
                actions = {
                    if (state.stored != null) {
                        FinoteIconButton(
                            iconRes = R.drawable.ic_delete,
                            contentDescription = stringResource(R.string.keyword_rule_edit_delete),
                            onClick = { onIntent(KeywordRuleEditIntent.DeleteClicked) },
                        )
                    }
                },
            )
            if (draft != null) {
                Fields(state, draft, onIntent)
                RuleCategoryPicker(
                    categories = state.categories,
                    selectedId = draft.categoryId,
                    onPick = { onIntent(KeywordRuleEditIntent.DraftChanged(draft.copy(categoryId = it))) },
                    modifier = Modifier.weight(1f).padding(horizontal = FinoteTheme.spacing.lg),
                )
                FinoteButton(
                    text = stringResource(R.string.cash_entry_save),
                    onClick = { onIntent(KeywordRuleEditIntent.SaveClicked) },
                    modifier = Modifier.fillMaxWidth().padding(FinoteTheme.spacing.lg),
                    enabled = state.isSaveEnabled,
                    isLoading = state.isSaving,
                )
            }
        }
        DeleteRuleDialog(
            isVisible = state.isDeleteOpen,
            keyword = state.stored?.keyword.orEmpty(),
            onConfirm = { onIntent(KeywordRuleEditIntent.DeleteConfirmed) },
            onDismiss = { onIntent(KeywordRuleEditIntent.DeleteDismissed) },
        )
        BulkApplyDialog(
            count = state.bulkApplyCount,
            onConfirm = { onIntent(KeywordRuleEditIntent.BulkApplyConfirmed) },
            onDismiss = { onIntent(KeywordRuleEditIntent.BulkApplyDismissed) },
        )
    }
}

@Composable
private fun Fields(
    state: KeywordRuleEditState,
    draft: KeywordRuleDraft,
    onIntent: (KeywordRuleEditIntent) -> Unit,
) {
    Column(
        modifier = Modifier.padding(start = FinoteTheme.spacing.lg, end = FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
    ) {
        FinoteTextField(
            value = draft.keyword,
            onValueChange = { onIntent(KeywordRuleEditIntent.DraftChanged(draft.copy(keyword = it))) },
            label = stringResource(R.string.cash_entry_rule_keyword),
            modifier = Modifier.fillMaxWidth(),
            errorText = state.error?.let { stringResource(it.messageRes()) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        FinoteTextField(
            value = draft.merchantName,
            onValueChange = { onIntent(KeywordRuleEditIntent.DraftChanged(draft.copy(merchantName = it))) },
            label = stringResource(R.string.cash_entry_rule_name),
            modifier = Modifier.fillMaxWidth(),
            maxLength = NAME_MAX_LENGTH,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
    }
}

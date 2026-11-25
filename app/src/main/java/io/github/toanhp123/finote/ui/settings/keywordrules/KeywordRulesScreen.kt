package io.github.toanhp123.finote.ui.settings.keywordrules

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.display.FinoteSection
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.navigation.EditKeywordRule
import io.github.toanhp123.finote.ui.settings.keywordrules.component.KeywordRuleRow
import io.github.toanhp123.finote.ui.settings.keywordrules.logic.KeywordRuleItem
import kotlinx.coroutines.launch

@Composable
fun KeywordRulesRoute(
    viewModel: KeywordRulesViewModel,
    snackbarState: FinoteSnackbarState,
    onOpenRule: (EditKeywordRule) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.category_snack_failed)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is KeywordRulesEffect.OpenEditRule -> onOpenRule(effect.key)
            KeywordRulesEffect.ActionFailed -> scope.launch { snackbarState.show(failed) }
        }
    }
    KeywordRulesScreen(state, viewModel::onIntent, onClose, modifier)
}

@Composable
fun KeywordRulesScreen(
    state: KeywordRulesState,
    onIntent: (KeywordRulesIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinoteTheme.spacing
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(
            title = stringResource(R.string.settings_keyword_rules_title),
            onBack = onBack,
            actions = {
                FinoteIconButton(
                    iconRes = R.drawable.ic_add,
                    contentDescription = stringResource(R.string.keyword_rules_add),
                    onClick = { onIntent(KeywordRulesIntent.AddClicked) },
                )
            },
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.lg),
        ) {
            if (state.user.isEmpty()) {
                FinoteEmptyState(
                    iconRes = R.drawable.ic_bolt,
                    title = stringResource(R.string.keyword_rules_empty_title),
                    message = stringResource(R.string.keyword_rules_empty_message),
                )
            } else {
                RuleSection(R.string.keyword_rules_section_user, state.user, onIntent)
            }
            if (state.system.isNotEmpty()) RuleSection(R.string.keyword_rules_section_system, state.system, onIntent)
        }
    }
}

@Composable
private fun RuleSection(
    @StringRes titleRes: Int,
    items: List<KeywordRuleItem>,
    onIntent: (KeywordRulesIntent) -> Unit,
) {
    FinoteSection(title = stringResource(titleRes)) {
        Column {
            items.forEach { KeywordRuleRow(it, onIntent) }
        }
    }
}

@FinotePreviews
@Composable
private fun KeywordRulesScreenPreview() {
    val food = previewCategory()
    FinotePreviewSurface {
        KeywordRulesScreen(
            state =
                KeywordRulesState(
                    user = listOf(KeywordRuleItem(previewRule("u", "grab", "Grab", isUserDefined = true), food)),
                    system = listOf(KeywordRuleItem(previewRule("s", "highlands", null, isUserDefined = false), food)),
                ),
            onIntent = {},
            onBack = {},
        )
    }
}

private fun previewRule(
    id: String,
    keyword: String,
    merchantName: String?,
    isUserDefined: Boolean,
) = KeywordRule(
    id = id,
    keyword = keyword,
    normalizedKeyword = keyword,
    matchType = KeywordMatchType.CONTAINS,
    merchantName = merchantName,
    categoryId = "food",
    priority = 0,
    isUserDefined = isUserDefined,
    isActive = true,
    version = 0,
    createdAt = 0L,
    updatedAt = 0L,
)

private fun previewCategory() =
    Category(
        id = "food",
        name = "Food",
        nameKey = null,
        icon = "restaurant",
        color = PreviewRedArgb,
        type = CategoryType.EXPENSE,
        parentId = null,
        isSystemDefault = false,
        isHidden = false,
        sortOrder = 0,
        createdAt = 0L,
        updatedAt = 0L,
        deletedAt = null,
    )

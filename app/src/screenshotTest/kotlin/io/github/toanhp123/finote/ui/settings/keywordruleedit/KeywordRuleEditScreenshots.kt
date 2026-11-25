package io.github.toanhp123.finote.ui.settings.keywordruleedit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.category.error.KeywordRuleError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.keywordruleedit.logic.KeywordRuleDraft

private fun category(
    id: String,
    name: String,
    icon: String,
    color: Int,
    parentId: String? = null,
) = Category(
    id = id,
    name = name,
    nameKey = null,
    icon = icon,
    color = color,
    type = CategoryType.EXPENSE,
    parentId = parentId,
    isSystemDefault = false,
    isHidden = false,
    sortOrder = 0,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)

private val categories =
    listOf(
        category("food", "Food & drink", "restaurant", 0xFFE53935.toInt()),
        category("coffee", "Coffee", "restaurant", 0xFFE53935.toInt(), parentId = "food"),
        category("ride", "Transport", "directions_car", 0xFF1E88E5.toInt()),
        category("shop", "Shopping", "shopping_bag", 0xFF43A047.toInt()),
    )

private val stored =
    KeywordRule(
        id = "u1",
        keyword = "bun bo",
        normalizedKeyword = "bun bo",
        matchType = KeywordMatchType.CONTAINS,
        merchantName = "Bún bò Huế",
        categoryId = "food",
        priority = 0,
        isUserDefined = true,
        isActive = true,
        version = 0,
        createdAt = 0L,
        updatedAt = 0L,
    )

private val blank = KeywordRuleEditState(draft = KeywordRuleDraft(), categories = categories)

private val editing =
    KeywordRuleEditState(
        draft = KeywordRuleDraft("bun bo", "Bún bò Huế", "food"),
        stored = stored,
        categories = categories,
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun KeywordRuleCreatePreview() = Screen(blank)

@PreviewTest
@ScreenPreviews
@Composable
private fun KeywordRuleEditPreview() = Screen(editing)

@PreviewTest
@ScreenPreviews
@Composable
private fun KeywordRuleErrorPreview() =
    Screen(
        editing.copy(
            draft = KeywordRuleDraft("b", "", "food"),
            error = KeywordRuleError.TooShort,
        ),
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun KeywordRuleDeletePreview() = Screen(editing.copy(isDeleteOpen = true))

@PreviewTest
@ScreenPreviews
@Composable
private fun KeywordRuleBulkApplyPreview() = Screen(editing.copy(bulkApplyCount = 12))

@Composable
private fun Screen(state: KeywordRuleEditState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            KeywordRuleEditScreen(state = state, onIntent = {}, onBack = {}, modifier = Modifier.statusBarsPadding())
        }
    }
}

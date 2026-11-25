package io.github.toanhp123.finote.ui.settings.keywordrules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.TallScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.keywordrules.logic.KeywordRuleItem

private val food = category("food", "Food & drink", "restaurant", 0xFFE53935.toInt())
private val ride = category("ride", "Transport", "directions_car", 0xFF1E88E5.toInt())

private fun category(
    id: String,
    name: String,
    icon: String,
    color: Int,
) = Category(
    id = id,
    name = name,
    nameKey = null,
    icon = icon,
    color = color,
    type = CategoryType.EXPENSE,
    parentId = null,
    isSystemDefault = false,
    isHidden = false,
    sortOrder = 0,
    createdAt = 0L,
    updatedAt = 0L,
    deletedAt = null,
)

private fun item(
    id: String,
    keyword: String,
    category: Category?,
    merchantName: String? = null,
    isUserDefined: Boolean = true,
    isActive: Boolean = true,
) = KeywordRuleItem(
    rule =
        KeywordRule(
            id = id,
            keyword = keyword,
            normalizedKeyword = keyword,
            matchType = KeywordMatchType.CONTAINS,
            merchantName = merchantName,
            categoryId = category?.id.orEmpty(),
            priority = 0,
            isUserDefined = isUserDefined,
            isActive = isActive,
            version = 0,
            createdAt = 0L,
            updatedAt = 0L,
        ),
    category = category,
)

private val system =
    listOf(
        item("s1", "highlands", food, "Highlands", isUserDefined = false),
        item("s2", "grab", ride, "Grab", isUserDefined = false, isActive = false),
        item("s3", "the coffee house", food, "The Coffee House", isUserDefined = false),
    )

private val listed =
    KeywordRulesState(
        user =
            listOf(
                item("u1", "bun bo", food, "Bún bò Huế"),
                item("u2", "taxi", ride),
                item("u3", "old rule", null),
            ),
        system = system,
    )

@PreviewTest
@TallScreenPreviews
@Composable
private fun KeywordRulesListPreview() = Screen(listed)

@PreviewTest
@ScreenPreviews
@Composable
private fun KeywordRulesEmptyPreview() = Screen(KeywordRulesState(system = system))

@Composable
private fun Screen(state: KeywordRulesState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background)) {
            KeywordRulesScreen(state = state, onIntent = {}, onBack = {}, modifier = Modifier.statusBarsPadding())
        }
    }
}

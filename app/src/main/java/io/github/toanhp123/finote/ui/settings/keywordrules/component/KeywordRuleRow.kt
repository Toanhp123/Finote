package io.github.toanhp123.finote.ui.settings.keywordrules.component

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteSwitch
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.keywordrules.KeywordRulesIntent
import io.github.toanhp123.finote.ui.settings.keywordrules.logic.KeywordRuleItem

/**
 * The keyword, then the category name and the short name under it. A user rule opens its edit screen. A system rule
 * has only a switch: the row owns the tap, so the switch is just the picture.
 */
@Composable
fun KeywordRuleRow(
    item: KeywordRuleItem,
    onIntent: (KeywordRulesIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rule = item.rule
    val category = item.category
    val subtitle =
        listOfNotNull(category?.let { displayName(it.name, it.nameKey) }, rule.merchantName)
            .joinToString(" · ")
            .ifEmpty { null }
    val leading: (@Composable () -> Unit)? =
        category?.let {
            { CategoryIcon(iconRes(it.icon), it.color, contentDescription = null) }
        }
    if (rule.isUserDefined) {
        FinoteListRow(
            title = rule.keyword,
            modifier = modifier,
            subtitle = subtitle,
            leading = leading,
            trailing = {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    modifier = Modifier.size(FinoteTheme.sizes.iconMd),
                    tint = FinoteTheme.scheme.onSurfaceVariant,
                )
            },
            onClick = { onIntent(KeywordRulesIntent.RuleClicked(rule.id)) },
        )
    } else {
        FinoteListRow(
            title = rule.keyword,
            modifier =
                modifier.toggleable(
                    value = rule.isActive,
                    role = Role.Switch,
                    onValueChange = { onIntent(KeywordRulesIntent.SystemRuleToggled(rule.id, it)) },
                ),
            subtitle = subtitle,
            leading = leading,
            trailing = { FinoteSwitch(checked = rule.isActive, onCheckedChange = null) },
        )
    }
}

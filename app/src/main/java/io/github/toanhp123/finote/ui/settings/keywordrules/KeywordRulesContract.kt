package io.github.toanhp123.finote.ui.settings.keywordrules

import io.github.toanhp123.finote.ui.navigation.EditKeywordRule
import io.github.toanhp123.finote.ui.settings.keywordrules.logic.KeywordRuleItem

/** [user] rules come newest first, [system] rules by keyword. The screen draws the user rules above the system ones. */
data class KeywordRulesState(
    val user: List<KeywordRuleItem> = emptyList(),
    val system: List<KeywordRuleItem> = emptyList(),
)

sealed interface KeywordRulesIntent {
    data object AddClicked : KeywordRulesIntent

    /** A user rule was tapped: open it. A system rule has no edit screen, only a switch. */
    data class RuleClicked(
        val id: String,
    ) : KeywordRulesIntent

    data class SystemRuleToggled(
        val id: String,
        val isActive: Boolean,
    ) : KeywordRulesIntent
}

sealed interface KeywordRulesEffect {
    data class OpenEditRule(
        val key: EditKeywordRule,
    ) : KeywordRulesEffect

    /** A write failed; the route shows a snackbar. */
    data object ActionFailed : KeywordRulesEffect
}

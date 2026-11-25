package io.github.toanhp123.finote.domain.category.error

/** Why a keyword rule change was refused. Decided in the domain, before anything is written. */
sealed interface KeywordRuleError {
    /** The keyword has fewer than 2 characters once normalized. */
    data object TooShort : KeywordRuleError

    /** Another user rule has the same normalized keyword and a category of the same type. */
    data object Duplicate : KeywordRuleError

    /** The category is missing, deleted or hidden. */
    data object CategoryGone : KeywordRuleError

    /** An update of an id that is not a user rule: unknown, or a system rule. */
    data object NotFound : KeywordRuleError
}

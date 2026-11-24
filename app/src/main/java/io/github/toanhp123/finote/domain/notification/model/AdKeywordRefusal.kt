package io.github.toanhp123.finote.domain.notification.model

/** Why a phrase may not become an ad keyword. A keyword that hides a real transaction is the failure to avoid. */
enum class AdKeywordRefusal {
    /** Shorter than three characters once normalized. */
    TOO_SHORT,

    /** The phrase is not in the notification the user picked. */
    NOT_IN_SELECTED,

    /** The phrase is the bank's own name, which every notification of the package carries. */
    BANK_NAME,

    /** The phrase is in a stored log of the package that is, or may be, real money. */
    IN_REAL_TRANSACTION,

    /** The phrase is in another unparsed inbox item that looks like money. */
    IN_OTHER_MONEY_ITEM,

    /** The package already has this keyword. */
    DUPLICATE,
}

/** What the "this is an ad" sheet shows live: why the phrase is refused, or how many other items it would hide. */
data class AdKeywordPreview(
    val refusal: AdKeywordRefusal?,
    val otherHiddenCount: Int,
)

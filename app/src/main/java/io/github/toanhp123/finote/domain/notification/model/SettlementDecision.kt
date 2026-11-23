package io.github.toanhp123.finote.domain.notification.model

/** What to do with one or two pending logs once their wait is over. */
sealed interface SettlementDecision {
    /** Book one transaction. [bankSide] carries amount, type and balance; [walletSide] is the paired wallet log. */
    data class Book(
        val bankSide: NotificationLog,
        val walletSide: NotificationLog?,
        /** The planner's view of the account; the writer resolves the account again inside its transaction. */
        val account: AccountTarget,
        val merchant: String?,
        val note: String?,
        /** The rule of [bankSide]; the writer resolves the account again with it. */
        val rule: ParserRule?,
    ) : SettlementDecision {
        // Merchant and note stay out of logs and crash reports.
        override fun toString() = "Book(bankSide=$bankSide, walletSide=$walletSide, account=$account)"
    }

    data class Fail(
        val log: NotificationLog,
        val partner: NotificationLog?,
        val reason: String,
    ) : SettlementDecision

    /** The account is ignored: drop [log] and its paired wallet [partner] without a transaction. */
    data class Ignore(
        val log: NotificationLog,
        val partner: NotificationLog?,
    ) : SettlementDecision

    /** [log] is the same notification posted again; [original] is a log that is settled, booked or failed. */
    data class Repost(
        val log: NotificationLog,
        val original: NotificationLog,
    ) : SettlementDecision
}

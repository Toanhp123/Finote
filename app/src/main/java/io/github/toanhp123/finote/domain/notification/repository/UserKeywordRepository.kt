package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import kotlinx.coroutines.flow.Flow

interface UserKeywordRepository {
    /** Every taught keyword, by package then oldest first; re-emits when one is added or removed. */
    fun observeAll(): Flow<List<UserIgnoreKeyword>>

    suspend fun getLog(id: String): NotificationLog?

    /** Every stored log of [packageName], oldest first. */
    suspend fun getLogs(packageName: String): List<NotificationLog>

    /** The keywords taught for [packageName], oldest first. */
    suspend fun getKeywords(packageName: String): List<UserIgnoreKeyword>

    /** The bank name of the package's rules, or blank when it has none. */
    suspend fun getBankName(packageName: String): String

    /**
     * In one write: inserts [keyword], then turns the logs of [logIds] that are still `FAILED` / `no_rule_matched` into
     * `IGNORED_SPAM` with reason `user_keyword:<normalized>`. A log that changed meanwhile is left alone. Throws when
     * the package already has the keyword.
     */
    suspend fun add(
        keyword: UserIgnoreKeyword,
        logIds: List<String>,
    )

    /**
     * In one write: deletes the keyword and turns the package's logs it hid back into `FAILED` / `no_rule_matched`.
     * Returns the deleted keyword, or null when it was already gone.
     */
    suspend fun remove(keywordId: String): UserIgnoreKeyword?
}

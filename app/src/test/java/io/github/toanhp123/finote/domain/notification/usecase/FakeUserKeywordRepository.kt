package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.domain.notification.repository.UserKeywordRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Copies from the real repository: the keyword list, `add` and `remove`. Logs are set by tests. */
class FakeUserKeywordRepository : UserKeywordRepository {
    val keywords = MutableStateFlow<List<UserIgnoreKeyword>>(emptyList())
    val removedIds = mutableListOf<String>()
    val logs = mutableListOf<NotificationLog>()
    val added = mutableListOf<Pair<UserIgnoreKeyword, List<String>>>()

    override fun observeAll(): Flow<List<UserIgnoreKeyword>> = keywords

    override suspend fun remove(keywordId: String): UserIgnoreKeyword? {
        val found = keywords.value.firstOrNull { it.id == keywordId } ?: return null
        removedIds += keywordId
        keywords.value = keywords.value - found
        return found
    }

    override suspend fun getLog(id: String): NotificationLog? = logs.firstOrNull { it.id == id }

    override suspend fun getLogs(packageName: String): List<NotificationLog> = logs

    override suspend fun getKeywords(packageName: String): List<UserIgnoreKeyword> = keywords.value

    override suspend fun getBankName(packageName: String): String = "Test Bank"

    override suspend fun add(
        keyword: UserIgnoreKeyword,
        logIds: List<String>,
    ) {
        added += keyword to logIds
        keywords.value = keywords.value + keyword
    }
}

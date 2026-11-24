package io.github.toanhp123.finote.data.notification.repository

import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.notification.mapper.toEntity
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.domain.notification.repository.UserKeywordRepository
import io.github.toanhp123.finote.domain.notification.service.USER_KEYWORD_REASON_PREFIX
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Under the 999 variable limit of SQLite on the oldest supported Android versions.
private const val ID_CHUNK = 500

class UserKeywordRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
    ) : UserKeywordRepository {
        private val keywords = db.userIgnoreKeywordDao()
        private val logs = db.notificationLogDao()
        private val rules = db.parserRuleDao()

        override fun observeAll(): Flow<List<UserIgnoreKeyword>> =
            keywords.observeAll().map { list -> list.map { it.toDomain() } }.mapStorageErrors()

        override suspend fun getLog(id: String): NotificationLog? = storage { logs.getById(id)?.toDomain() }

        override suspend fun getLogs(packageName: String): List<NotificationLog> =
            storage { logs.getByPackage(packageName).map { it.toDomain() } }

        override suspend fun getKeywords(packageName: String): List<UserIgnoreKeyword> =
            storage { keywords.getByPackage(packageName).map { it.toDomain() } }

        override suspend fun getBankName(packageName: String): String =
            storage {
                rules
                    .getByPackage(packageName)
                    .firstOrNull()
                    ?.bankName
                    .orEmpty()
            }

        override suspend fun add(
            keyword: UserIgnoreKeyword,
            logIds: List<String>,
        ) {
            db.storageWrite {
                keywords.insert(keyword.toEntity())
                val reason = USER_KEYWORD_REASON_PREFIX + keyword.normalizedKeyword
                logIds.chunked(ID_CHUNK).forEach { logs.hideWithKeyword(it, reason) }
            }
        }

        override suspend fun remove(keywordId: String): UserIgnoreKeyword? =
            db.storageWrite {
                val entity = keywords.getById(keywordId) ?: return@storageWrite null
                keywords.delete(keywordId)
                logs.restoreKeyword(entity.packageName, USER_KEYWORD_REASON_PREFIX + entity.normalizedKeyword)
                entity.toDomain()
            }
    }

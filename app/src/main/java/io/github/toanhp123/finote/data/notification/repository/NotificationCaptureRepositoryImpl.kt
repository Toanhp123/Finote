package io.github.toanhp123.finote.data.notification.repository

import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.notification.mapper.toEntity
import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class NotificationCaptureRepositoryImpl
    @Inject
    constructor(
        db: FinoteDatabase,
    ) : NotificationCaptureRepository {
        private val rules = db.parserRuleDao()
        private val logs = db.notificationLogDao()
        private val userKeywords = db.userIgnoreKeywordDao()

        override suspend fun getRules(packageName: String): List<ParserRule> =
            storage { rules.getByPackage(packageName).mapNotNull { it.toDomain() } }

        override suspend fun insertLog(log: NotificationLog): Boolean = storage { logs.insert(log.toEntity()) != -1L }

        override suspend fun getUserKeywords(packageName: String): List<String> =
            storage { userKeywords.getByPackage(packageName).map { it.normalizedKeyword } }

        override suspend fun getReparseLogs(packages: Set<String>): List<NotificationLog> =
            storage {
                if (packages.isEmpty()) {
                    emptyList()
                } else {
                    logs
                        .getReparseCandidates(
                            packages,
                        ).map { it.toReparseLog() }
                }
            }

        override suspend fun updateFailedOutcome(
            id: String,
            outcome: Classification,
        ): Boolean {
            val fields = outcome.parsed?.fields
            return storage {
                logs.updateFailedOutcome(
                    id = id,
                    isSecret = outcome.isSecret,
                    status = outcome.status,
                    failureReason = outcome.failureReason,
                    matchedRuleId = outcome.parsed?.ruleId,
                    parsedAmount = fields?.amount,
                    parsedType = fields?.type?.name,
                    parsedAccountMask = fields?.accountMask,
                    parsedPostBalance = fields?.postBalance,
                    parsedMerchant = fields?.merchant,
                    parsedNote = fields?.note,
                ) == 1
            }
        }

        override fun observeRecentLogs(limit: Int): Flow<List<NotificationLog>> =
            logs.observeRecent(limit).map { list -> list.map { it.toDomain() } }.mapStorageErrors()

        // A re-parse candidate has no match, so there is nothing parsed to restore.
        private fun NotificationLogEntity.toReparseLog() =
            NotificationLog(
                id = id,
                packageName = packageName,
                channelId = channelId,
                title = title,
                content = content,
                contentHash = contentHash,
                postTime = postTime,
                receivedAt = receivedAt,
                status = status,
                failureReason = failureReason,
                notificationKey = notificationKey,
            )
    }

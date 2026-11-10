package io.github.toanhp123.finote.data.notification.repository

import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.notification.mapper.toEntity
import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import javax.inject.Inject

class NotificationCaptureRepositoryImpl
    @Inject
    constructor(
        db: FinoteDatabase,
    ) : NotificationCaptureRepository {
        private val rules = db.parserRuleDao()
        private val logs = db.notificationLogDao()

        override suspend fun getRules(packageName: String): List<ParserRule> =
            rules.getByPackage(packageName).mapNotNull { it.toDomain() }

        override suspend fun insertLog(log: NotificationLog): Boolean = logs.insert(log.toEntity()) != -1L

        override suspend fun getFailedLogs(packages: Set<String>): List<NotificationLog> =
            if (packages.isEmpty()) emptyList() else logs.getFailed(packages).map { it.toFailedLog() }

        override suspend fun updateFailedOutcome(
            id: String,
            outcome: Classification,
        ): Boolean {
            val fields = outcome.parsed?.fields
            return logs.updateFailedOutcome(
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

        // A FAILED row has no match, so there is nothing parsed to restore.
        private fun NotificationLogEntity.toFailedLog() =
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
            )
    }

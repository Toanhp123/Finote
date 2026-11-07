package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.data.FinoteDatabase
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
            rules.getByPackage(packageName).map { it.toDomain() }

        override suspend fun insertLog(log: NotificationLog): Boolean = logs.insert(log.toEntity()) != -1L
    }

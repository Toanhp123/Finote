package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.common.DefaultDispatcher
import io.github.toanhp123.finote.domain.notification.SECRET_KEYWORDS
import io.github.toanhp123.finote.domain.notification.classifyNotification
import io.github.toanhp123.finote.domain.notification.contentHash
import io.github.toanhp123.finote.domain.notification.model.CaptureResult
import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class CaptureNotificationUseCase
    @Inject
    constructor(
        private val repository: NotificationCaptureRepository,
        private val clock: Clock,
        @param:DefaultDispatcher private val parseDispatcher: CoroutineDispatcher,
    ) {
        /**
         * Filters the notification by the rules of its package and stores it: when it passes, `PENDING` with the
         * parsed fields or `FAILED` when no rule matches; an `IGNORED_SPAM` row when it is blocked (never parsed).
         * Only a secret ([SECRET_KEYWORDS]) drops the text; a channel or rule keyword
         * block keeps it, so a false positive can be recovered. The hash is taken from the original text
         * first, so two different blocked notifications at the same `postTime` stay two rows.
         */
        suspend operator fun invoke(raw: RawNotification): CaptureResult {
            val rules = repository.getRules(raw.packageName)
            if (rules.isEmpty()) return CaptureResult.NotTracked
            val hash = contentHash(raw.packageName, raw.postTime, raw.title, raw.content)
            // shortcut: ICU regex cannot be interrupted, so a pathological rule blocks one background thread;
            // add an on-device limit if users can type raw regex.
            val result = withContext(parseDispatcher) { classifyNotification(raw, rules) }
            val log =
                newLog(
                    raw = raw,
                    hash = hash,
                    title = if (result.isSecret) "" else raw.title,
                    content = if (result.isSecret) "" else raw.content,
                    result = result,
                )
            return when {
                !repository.insertLog(log) -> {
                    CaptureResult.Duplicate
                }

                result.status == NotificationStatus.IGNORED_SPAM -> {
                    CaptureResult.Ignored(
                        result.failureReason.orEmpty(),
                    )
                }

                result.parsed != null -> {
                    CaptureResult.Stored
                }

                else -> {
                    CaptureResult.Failed
                }
            }
        }

        private fun newLog(
            raw: RawNotification,
            hash: String,
            title: String,
            content: String,
            result: Classification,
        ) = NotificationLog(
            id = UUID.randomUUID().toString(),
            packageName = raw.packageName,
            channelId = raw.channelId,
            title = title,
            content = content,
            contentHash = hash,
            postTime = raw.postTime,
            receivedAt = clock.millis(),
            status = result.status,
            failureReason = result.failureReason,
            matchedRuleId = result.parsed?.ruleId,
            parsed = result.parsed?.fields,
        )
    }

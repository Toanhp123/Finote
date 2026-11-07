package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.SECRET_KEYWORDS
import io.github.toanhp123.finote.domain.notification.contentHash
import io.github.toanhp123.finote.domain.notification.firstMatchingKeyword
import io.github.toanhp123.finote.domain.notification.model.CaptureResult
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class CaptureNotificationUseCase
    @Inject
    constructor(
        private val repository: NotificationCaptureRepository,
        private val clock: Clock,
    ) {
        /**
         * Filters the notification by the rules of its package and stores it: `PENDING` when it passes, an
         * `IGNORED_SPAM` row when it is blocked. Only a secret ([SECRET_KEYWORDS]) drops the text; a channel or rule
         * keyword block keeps it, so a false positive can be recovered. The hash is taken from the original text
         * first, so two different blocked notifications at the same `postTime` stay two rows.
         */
        suspend operator fun invoke(raw: RawNotification): CaptureResult {
            val rules = repository.getRules(raw.packageName)
            if (rules.isEmpty()) return CaptureResult.NotTracked
            val hash = contentHash(raw.packageName, raw.postTime, raw.title, raw.content)
            val text = "${raw.title}\n${raw.content}"
            val secret = firstMatchingKeyword(text, SECRET_KEYWORDS)
            val reason = if (secret != null) "secret:$secret" else ignoreReason(raw, rules, text)
            val status = if (reason == null) NotificationStatus.PENDING else NotificationStatus.IGNORED_SPAM
            val isSecret = secret != null
            val log =
                newLog(
                    raw = raw,
                    hash = hash,
                    title = if (isSecret) "" else raw.title,
                    content = if (isSecret) "" else raw.content,
                    status = status,
                    failureReason = reason,
                )
            return when {
                !repository.insertLog(log) -> CaptureResult.Duplicate
                reason == null -> CaptureResult.Stored
                else -> CaptureResult.Ignored(reason)
            }
        }

        private fun ignoreReason(
            raw: RawNotification,
            rules: List<ParserRule>,
            text: String,
        ): String? {
            // One rule without an allowlist accepts every channel, so the filter needs all rules to have one.
            if (rules.all { it.channelIdAllowlist != null }) {
                val allowed = rules.flatMap { it.channelIdAllowlist.orEmpty() }
                if (raw.channelId !in allowed) return "channel:${raw.channelId}"
            }
            val keywords = rules.flatMapTo(LinkedHashSet()) { it.ignoreKeywords }.toList()
            return firstMatchingKeyword(text, keywords)?.let { "keyword:$it" }
        }

        private fun newLog(
            raw: RawNotification,
            hash: String,
            title: String,
            content: String,
            status: NotificationStatus,
            failureReason: String?,
        ) = NotificationLog(
            id = UUID.randomUUID().toString(),
            packageName = raw.packageName,
            channelId = raw.channelId,
            title = title,
            content = content,
            contentHash = hash,
            postTime = raw.postTime,
            receivedAt = clock.millis(),
            status = status,
            failureReason = failureReason,
        )
    }

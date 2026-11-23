package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.notification.error.RuleFeedError
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.domain.notification.port.ParserRuleFeed
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import io.github.toanhp123.finote.domain.notification.repository.RuleUpdateSettingsRepository
import kotlinx.coroutines.CancellationException
import java.time.Clock
import javax.inject.Inject

class UpdateParserRulesUseCase
    @Inject
    constructor(
        private val feed: ParserRuleFeed,
        private val rules: ParserRuleRepository,
        private val reparse: ReparseFailedLogsUseCase,
        private val settings: RuleUpdateSettingsRepository,
        private val clock: Clock,
    ) {
        /**
         * Fetches the feed, merges its rules, then re-parses the `FAILED` logs of every package in the feed (also when
         * nothing was written, so a run cut short after the merge is finished by the next one).
         * The outcome is saved as the last check, whatever it is.
         */
        suspend operator fun invoke(): RuleUpdateResult {
            val result = update()
            settings.saveLastCheck(RuleUpdateCheck(clock.millis(), result))
            return result
        }

        private suspend fun update(): RuleUpdateResult =
            try {
                when (val fetched = feed.fetch()) {
                    is Outcome.Ok -> merge(fetched.value)
                    is Outcome.Err -> fetched.error.toResult()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                RuleUpdateResult.Error
            }

        private fun RuleFeedError.toResult() =
            if (this == RuleFeedError.NETWORK) RuleUpdateResult.Network else RuleUpdateResult.Invalid

        private suspend fun merge(feedRules: List<ParserRule>): RuleUpdateResult {
            val written = rules.applyRules(feedRules)
            reparse(feedRules.mapTo(HashSet()) { it.packageName })
            return if (written > 0) RuleUpdateResult.Updated(written) else RuleUpdateResult.UpToDate
        }
    }

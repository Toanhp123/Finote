package io.github.toanhp123.finote.data.notification.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.assertStorageFailure
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A SQLite failure in any notification repository call reaches callers as a StorageException. */
@RunWith(AndroidJUnit4::class)
class NotificationRepositoryStorageErrorTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)
    private val log = notificationLogEntity().toDomain()

    @Test
    fun `capture write maps SQLException to StorageException`() =
        runTest {
            val repo = NotificationCaptureRepositoryImpl(dbRule.db)
            dbRule.execSql("DROP TABLE notification_logs")

            assertStorageFailure { repo.insertLog(log) }
        }

    @Test
    fun `settlement write maps SQLException to StorageException`() =
        runTest {
            val repo = NotificationSettlementRepositoryImpl(dbRule.db, clock)
            dbRule.execSql("DROP TABLE notification_logs")

            assertStorageFailure { repo.fail(SettlementDecision.Fail(log, partner = null, reason = "x")) }
        }

    @Test
    fun `parser rule write maps SQLException to StorageException`() =
        runTest {
            val repo = ParserRuleRepositoryImpl(dbRule.db, clock)
            dbRule.execSql("DROP TABLE parser_rules")
            val rule = requireNotNull(parserRuleEntity().toDomain())

            assertStorageFailure { repo.applyRules(listOf(rule)) }
        }

    @Test
    fun `parser rule observe maps SQLException to StorageException`() =
        runTest {
            val repo = ParserRuleRepositoryImpl(dbRule.db, clock)
            dbRule.execSql("DROP TABLE parser_rules")

            assertStorageFailure { repo.observeRules().first() }
        }
}

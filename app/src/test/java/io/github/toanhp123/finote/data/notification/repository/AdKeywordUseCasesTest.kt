package io.github.toanhp123.finote.data.notification.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.local.entity.UserIgnoreKeywordEntity
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.notification.model.AdKeywordRefusal
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.usecase.DeleteAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.FakeSettleScheduler
import io.github.toanhp123.finote.domain.notification.usecase.LearnAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveAdKeywordsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.PreviewAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ReparseFailedLogsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val PKG = "com.test.bank"
private const val PHRASE = "Mời bạn ly cà phê đầu tuần"
private const val REASON = "user_keyword:moi ban ly ca phe dau tuan"

private fun log(
    id: String,
    status: NotificationStatus = NotificationStatus.FAILED,
    reason: String? = "no_rule_matched",
    title: String = PHRASE,
    content: String = "Nhận ưu đãi $id",
): NotificationLogEntity =
    notificationLogEntity(id = id, contentHash = "hash-$id", status = status).copy(
        failureReason = reason,
        title = title,
        content = content,
    )

/** The three write use cases and the preview, on a real in-memory database. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AdKeywordUseCasesTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val scheduler = FakeSettleScheduler()
    private lateinit var repo: UserKeywordRepositoryImpl
    private lateinit var learn: LearnAdKeywordUseCase
    private lateinit var delete: DeleteAdKeywordUseCase
    private lateinit var preview: PreviewAdKeywordUseCase

    @Before
    fun setUp() {
        repo = UserKeywordRepositoryImpl(dbRule.db)
        val tx = RoomTransactionRunner(dbRule.db)
        val clock = Clock.fixed(Instant.parse("2026-11-23T00:00:00Z"), ZoneOffset.UTC)
        val reparse =
            ReparseFailedLogsUseCase(
                NotificationCaptureRepositoryImpl(dbRule.db),
                UnconfinedTestDispatcher(),
                scheduler,
            )
        learn = LearnAdKeywordUseCase(repo, tx, clock)
        delete = DeleteAdKeywordUseCase(repo, tx, reparse)
        preview = PreviewAdKeywordUseCase(repo)
    }

    private suspend fun seed(vararg logs: NotificationLogEntity) =
        logs.forEach {
            dbRule.db.notificationLogDao().insert(it)
        }

    private suspend fun state(id: String) =
        dbRule.db
            .notificationLogDao()
            .getById(id)!!
            .let { it.status to it.failureReason }

    @Test
    fun `learn hides the selected and the other matching promos and nothing else`() =
        runTest {
            dbRule.db.parserRuleDao().insert(parserRuleEntity(packageName = PKG))
            seed(
                log("sel"),
                log("promo", content = "Cuối tuần"),
                log("unrelated", title = "Tin khác", content = "Cập nhật ứng dụng"),
            )

            val outcome = learn("sel", PHRASE)

            assertTrue(outcome is Outcome.Ok)
            assertEquals(NotificationStatus.IGNORED_SPAM to REASON, state("sel"))
            assertEquals(NotificationStatus.IGNORED_SPAM to REASON, state("promo"))
            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("unrelated"))
            val taught = repo.getKeywords(PKG).single()
            assertEquals((outcome as Outcome.Ok).value, taught.id)
            assertEquals(PHRASE, taught.keyword)
            assertEquals("moi ban ly ca phe dau tuan", taught.normalizedKeyword)
        }

    @Test
    fun `a refusal writes nothing`() =
        runTest {
            dbRule.db.parserRuleDao().insert(parserRuleEntity(packageName = PKG))
            seed(
                log("sel"),
                log("real", NotificationStatus.CONFIRMED, null, "Thanh toan", "Thanh toan 150,000d. $PHRASE"),
            )

            val outcome = learn("sel", PHRASE)

            assertEquals(Outcome.Err(AdKeywordRefusal.IN_REAL_TRANSACTION), outcome)
            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("sel"))
            assertTrue(repo.getKeywords(PKG).isEmpty())
        }

    @Test
    fun `teaching the same keyword twice is DUPLICATE`() =
        runTest {
            seed(log("sel"), log("sel2", content = "Khác"))
            learn("sel", PHRASE)

            assertEquals(Outcome.Err(AdKeywordRefusal.DUPLICATE), learn("sel2", PHRASE.uppercase()))
            assertEquals(1, repo.getKeywords(PKG).size)
        }

    @Test
    fun `an unknown log is refused`() =
        runTest {
            assertEquals(Outcome.Err(AdKeywordRefusal.NOT_IN_SELECTED), learn("gone", PHRASE))
            assertEquals(AdKeywordRefusal.NOT_IN_SELECTED, preview("gone", PHRASE).refusal)
        }

    @Test
    fun `preview gives the refusal or the count of other items`() =
        runTest {
            dbRule.db.parserRuleDao().insert(parserRuleEntity(packageName = PKG))
            seed(log("sel"), log("sel-copy", content = "Nhận ưu đãi sel"), log("promo", content = "Cuối tuần"))

            val ok = preview("sel", PHRASE)
            val short = preview("sel", "ab")

            assertEquals(null, ok.refusal)
            assertEquals(1, ok.otherHiddenCount)
            assertEquals(AdKeywordRefusal.TOO_SHORT, short.refusal)
            assertEquals(0, short.otherHiddenCount)
            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("promo"))
        }

    @Test
    fun `delete restores the hidden logs, re-parses them and leaves the rest hidden`() =
        runTest {
            dbRule.db.parserRuleDao().insert(
                parserRuleEntity(packageName = PKG, regexPattern = "(?<sign>[+-])(?<amount>[\\d,]+)"),
            )
            val dao = dbRule.db.userIgnoreKeywordDao()
            dao.insert(UserIgnoreKeywordEntity("k1", PKG, PHRASE, "moi ban ly ca phe dau tuan", 1L))
            dao.insert(UserIgnoreKeywordEntity("k2", PKG, "Khac", "khac", 2L))
            seed(
                log("promo", NotificationStatus.IGNORED_SPAM, REASON),
                log("readable", NotificationStatus.IGNORED_SPAM, REASON, content = "SD TK -50,000 tai quan"),
                log("kept", NotificationStatus.IGNORED_SPAM, "user_keyword:khac", content = "Khac"),
            )

            assertTrue(delete("k1"))

            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("promo"))
            assertEquals(
                NotificationStatus.PENDING,
                dbRule.db
                    .notificationLogDao()
                    .getById("readable")!!
                    .status,
            )
            assertEquals(NotificationStatus.IGNORED_SPAM to "user_keyword:khac", state("kept"))
            assertEquals(listOf("k2"), repo.getKeywords(PKG).map { it.id })
            assertEquals(1, scheduler.afterCaptureCount)
        }

    @Test
    fun `delete of a keyword that is gone does nothing`() =
        runTest {
            assertFalse(delete("gone"))
            assertEquals(0, scheduler.afterCaptureCount)
        }

    @Test
    fun `observe groups the keywords by package`() =
        runTest {
            val dao = dbRule.db.userIgnoreKeywordDao()
            dao.insert(UserIgnoreKeywordEntity("k1", PKG, "Ad one", "ad one", 1L))
            dao.insert(UserIgnoreKeywordEntity("k2", "com.other", "Ad two", "ad two", 2L))
            dao.insert(UserIgnoreKeywordEntity("k3", PKG, "Ad three", "ad three", 3L))

            val grouped = ObserveAdKeywordsUseCase(repo)().first()

            assertEquals(setOf(PKG, "com.other"), grouped.keys)
            assertEquals(listOf("k1", "k3"), grouped.getValue(PKG).map { it.id })
        }
}

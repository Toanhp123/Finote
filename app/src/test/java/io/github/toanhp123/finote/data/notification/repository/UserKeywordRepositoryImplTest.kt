package io.github.toanhp123.finote.data.notification.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.assertStorageFailure
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.local.entity.UserIgnoreKeywordEntity
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PKG = "com.test.bank"
private const val NORMALIZED = "ly ca phe dau tuan"
private const val REASON = "user_keyword:$NORMALIZED"

private fun log(
    id: String,
    status: NotificationStatus = NotificationStatus.FAILED,
    reason: String? = "no_rule_matched",
    packageName: String = PKG,
) = notificationLogEntity(id = id, contentHash = "hash-$id", status = status).copy(
    packageName = packageName,
    failureReason = reason,
    title = "Mời bạn $id",
    content = "Tặng ly cà phê đầu tuần",
)

private fun keyword(
    id: String = "k1",
    normalized: String = NORMALIZED,
) = UserIgnoreKeyword(id, PKG, normalized, normalized, 5_000L)

@RunWith(AndroidJUnit4::class)
class UserKeywordRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private lateinit var repo: UserKeywordRepositoryImpl

    @Before
    fun setUp() {
        repo = UserKeywordRepositoryImpl(dbRule.db)
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
    fun `learn hides the selected and matching promos`() =
        runTest {
            seed(log("sel"), log("promo"), log("other-pkg", packageName = "com.other"))

            repo.add(keyword(), listOf("sel", "promo"))

            assertEquals(NotificationStatus.IGNORED_SPAM to REASON, state("sel"))
            assertEquals(NotificationStatus.IGNORED_SPAM to REASON, state("promo"))
            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("other-pkg"))
            assertEquals(listOf("k1"), repo.getKeywords(PKG).map { it.id })
        }

    @Test
    fun `delete restores them to the inbox`() =
        runTest {
            seed(log("sel"), log("promo"))
            repo.add(keyword(), listOf("sel", "promo"))

            val removed = repo.remove("k1")

            assertEquals("k1", removed?.id)
            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("sel"))
            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("promo"))
            assertEquals(emptyList<UserIgnoreKeyword>(), repo.getKeywords(PKG))
            assertNull(repo.remove("k1"))
        }

    @Test
    fun `delete restores only the logs its own keyword hid`() =
        runTest {
            seed(
                log("mine", NotificationStatus.IGNORED_SPAM, REASON),
                log("theirs", NotificationStatus.IGNORED_SPAM, "user_keyword:khac"),
                log("other-pkg", NotificationStatus.IGNORED_SPAM, REASON, packageName = "com.other"),
                log("spam", NotificationStatus.IGNORED_SPAM, "keyword:otp"),
            )
            dbRule.db.userIgnoreKeywordDao().insert(UserIgnoreKeywordEntity("k1", PKG, NORMALIZED, NORMALIZED, 5_000L))

            repo.remove("k1")

            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("mine"))
            assertEquals(NotificationStatus.IGNORED_SPAM to "user_keyword:khac", state("theirs"))
            assertEquals(NotificationStatus.IGNORED_SPAM to REASON, state("other-pkg"))
            assertEquals(NotificationStatus.IGNORED_SPAM to "keyword:otp", state("spam"))
        }

    @Test
    fun `learn after the log changed hides nothing else`() =
        runTest {
            seed(
                log("sel"),
                log("booked", NotificationStatus.CONFIRMED, null),
                log("failed", NotificationStatus.FAILED, "settle_error"),
            )

            repo.add(keyword(), listOf("sel", "booked", "failed"))

            assertEquals(NotificationStatus.IGNORED_SPAM to REASON, state("sel"))
            assertEquals(NotificationStatus.CONFIRMED to null, state("booked"))
            assertEquals(NotificationStatus.FAILED to "settle_error", state("failed"))
            assertEquals(1, repo.getKeywords(PKG).size)
        }

    @Test
    fun `learn hides more logs than one query holds`() =
        runTest {
            val logs = (1..1_200).map { log("l$it") }
            seed(*logs.toTypedArray())

            repo.add(keyword(), logs.map { it.id })

            assertEquals(NotificationStatus.IGNORED_SPAM to REASON, state("l1"))
            assertEquals(NotificationStatus.IGNORED_SPAM to REASON, state("l1200"))
        }

    @Test
    fun `a duplicate keyword throws and hides nothing`() =
        runTest {
            seed(log("sel"))
            repo.add(keyword("k1"), emptyList())

            assertStorageFailure { repo.add(keyword("k2"), listOf("sel")) }

            assertEquals(NotificationStatus.FAILED to "no_rule_matched", state("sel"))
            assertEquals(listOf("k1"), repo.getKeywords(PKG).map { it.id })
        }

    @Test
    fun `reads the package logs and the bank name`() =
        runTest {
            dbRule.db.parserRuleDao().insert(parserRuleEntity(packageName = PKG))
            seed(log("b"), log("a"), log("other-pkg", packageName = "com.other"))

            assertEquals(setOf("a", "b"), repo.getLogs(PKG).map { it.id }.toSet())
            assertEquals("a", repo.getLog("a")?.id)
            assertNull(repo.getLog("none"))
            assertEquals("Test bank", repo.getBankName(PKG))
            assertEquals("", repo.getBankName("com.other"))
        }

    @Test
    fun `observeAll re-emits when a keyword is added and removed`() =
        runTest {
            repo.observeAll().test {
                assertEquals(emptyList<UserIgnoreKeyword>(), awaitItem())

                repo.add(keyword(), emptyList())
                assertEquals(listOf("k1"), awaitItem().map { it.id })

                repo.remove("k1")
                assertEquals(emptyList<UserIgnoreKeyword>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

package io.github.toanhp123.finote.data.notification.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.data.account.local.entity.AccountIdentifierEntity
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock

private const val PKG = "com.test.bank"

@Suppress("LongParameterList") // why: one defaulted argument per log column keeps each test call short
private fun log(
    id: String,
    status: NotificationStatus,
    reason: String? = null,
    postTime: Long = 1_000L,
    key: String? = null,
    content: String = "Thanh toan 150,000d tai Cafe $id",
    parsedAmount: Long? = null,
): NotificationLogEntity =
    notificationLogEntity(id = id, contentHash = "hash-$id", status = status).copy(
        failureReason = reason,
        postTime = postTime,
        notificationKey = key,
        content = content,
        parsedAmount = parsedAmount,
    )

@RunWith(AndroidJUnit4::class)
class FailedInboxRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private lateinit var repo: FailedInboxRepositoryImpl

    // why: Room emits from its own executor, which a test scheduler rejects; Unconfined has no scheduler.
    @Suppress("InjectDispatcher")
    @Before
    fun setUp() {
        repo = FailedInboxRepositoryImpl(dbRule.db, Clock.systemUTC(), Dispatchers.Unconfined)
    }

    private suspend fun seedRule(isActive: Boolean = true) =
        dbRule.db.parserRuleDao().insert(parserRuleEntity(packageName = PKG, isActive = isActive))

    @Test
    fun `observeInbox re-emits when a log turns FAILED`() =
        runTest {
            seedRule()
            dbRule.db.notificationLogDao().insert(log("a", NotificationStatus.PENDING))

            repo.observeInbox().test {
                assertTrue(awaitItem().isEmpty())

                dbRule.db.notificationLogDao().markSettled(listOf("a"), NotificationStatus.FAILED, null, "settle_error")

                assertEquals(listOf("a"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a package whose rules are all inactive gives no items`() =
        runTest {
            seedRule(isActive = false)
            dbRule.db.notificationLogDao().insert(log("a", NotificationStatus.FAILED, "settle_error"))

            repo.observeInbox().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a booked log of the same key within the window hides only the logs it covers`() =
        runTest {
            seedRule()
            val dao = dbRule.db.notificationLogDao()
            dao.insert(log("near", NotificationStatus.FAILED, "no_rule_matched", postTime = 1_000_000L, key = "k"))
            dao.insert(log("far", NotificationStatus.FAILED, "no_rule_matched", postTime = 10_000L, key = "k"))
            dao.insert(
                log("booked", NotificationStatus.CONFIRMED, postTime = 1_300_000L, key = "k", parsedAmount = 150_000L),
            )

            repo.observeInbox().test {
                assertEquals(listOf("far"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a booked log of the same key and another amount does not hide the log`() =
        runTest {
            seedRule()
            val dao = dbRule.db.notificationLogDao()
            dao.insert(log("unparsed", NotificationStatus.FAILED, "no_rule_matched", postTime = 1_000_000L, key = "k"))
            dao.insert(
                log("booked", NotificationStatus.CONFIRMED, postTime = 1_100_000L, key = "k", parsedAmount = 99_000L),
            )

            repo.observeInbox().test {
                val item = awaitItem().single()
                assertEquals("unparsed", item.id)
                assertFalse(item.isHidden)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a booked log of the same key and an unreadable amount does not drop the log`() =
        runTest {
            seedRule()
            val dao = dbRule.db.notificationLogDao()
            val text = "Hello there"
            dao.insert(log("unparsed", NotificationStatus.FAILED, "no_rule_matched", 1_000_000L, "k", text))
            dao.insert(
                log("booked", NotificationStatus.CONFIRMED, postTime = 1_100_000L, key = "k", parsedAmount = 150_000L),
            )

            repo.observeInbox().test {
                assertEquals(listOf("unparsed"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getItem returns the item of an id and null for any other`() =
        runTest {
            seedRule()
            dbRule.db.notificationLogDao().insert(log("a", NotificationStatus.FAILED, "settle_error"))
            dbRule.db.notificationLogDao().insert(log("b", NotificationStatus.CONFIRMED))

            assertEquals("a", repo.getItem("a")?.id)
            assertNull(repo.getItem("b"))
            assertNull(repo.getItem("missing"))
        }

    @Test
    fun `getIdentifiers returns only the identifiers of the package`() =
        runTest {
            val ids = dbRule.db.accountIdentifierDao()
            dbRule.db.accountDao().insert(account(id = "x", bankPackageName = PKG))
            dbRule.db.accountDao().insert(account(id = "y", bankPackageName = "other"))
            ids.insert(AccountIdentifierEntity(PKG, IdentifierKind.CARD, "1111", "x"))
            ids.insert(AccountIdentifierEntity("other", IdentifierKind.ACCOUNT, "2222", "y"))

            assertEquals(listOf("x"), repo.getIdentifiers(PKG).map { it.accountId })
        }
}

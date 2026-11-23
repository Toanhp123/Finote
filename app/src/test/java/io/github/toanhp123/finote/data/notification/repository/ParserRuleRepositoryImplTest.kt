package io.github.toanhp123.finote.data.notification.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ParserRuleRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-20T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var repo: ParserRuleRepositoryImpl

    @Before
    fun setUp() {
        repo = ParserRuleRepositoryImpl(dbRule.db, clock)
    }

    private fun feedRule(
        id: String,
        version: Int,
        packageName: String = "com.test.bank",
    ): ParserRule =
        requireNotNull(parserRuleEntity(id = id, packageName = packageName).copy(version = version).toDomain())

    private suspend fun stored(id: String) =
        dbRule.db
            .parserRuleDao()
            .getAll()
            .single { it.id == id }

    @Test
    fun `applyRules writes missing and newer system rules`() =
        runTest {
            dbRule.db.parserRuleDao().insert(parserRuleEntity(id = "old").copy(version = 1))

            val written = repo.applyRules(listOf(feedRule("old", 2), feedRule("fresh", 1)))

            assertEquals(2, written)
            assertEquals(2, stored("old").version)
            assertEquals(clock.millis(), stored("old").updatedAt)
            assertEquals(1, stored("fresh").version)
        }

    @Test
    fun `applyRules keeps a user rule even when the feed version is higher`() =
        runTest {
            dbRule.db.parserRuleDao().insert(
                parserRuleEntity(id = "mine", regexPattern = "mine").copy(isUserCustom = true, version = 1),
            )

            val written = repo.applyRules(listOf(feedRule("mine", 9)))

            assertEquals(0, written)
            assertEquals("mine", stored("mine").regexPattern)
            assertEquals(1, stored("mine").version)
        }

    @Test
    fun `applyRules does not lower a version`() =
        runTest {
            dbRule.db.parserRuleDao().insert(parserRuleEntity(id = "r").copy(version = 5))

            assertEquals(0, repo.applyRules(listOf(feedRule("r", 3))))
            assertEquals(5, stored("r").version)
        }

    @Test
    fun `new package rules are written`() =
        runTest {
            assertEquals(1, repo.applyRules(listOf(feedRule("x", 1, packageName = "com.new.bank"))))

            assertEquals(
                listOf("x"),
                dbRule.db
                    .parserRuleDao()
                    .getByPackage("com.new.bank")
                    .map { it.id },
            )
        }

    @Test
    fun `applyRules returns 0 when nothing is newer`() =
        runTest {
            dbRule.db.parserRuleDao().insert(parserRuleEntity(id = "r").copy(version = 2))

            assertEquals(0, repo.applyRules(listOf(feedRule("r", 2))))
        }

    @Test
    fun `observeRules emits after applyRules`() =
        runTest {
            val emitted = async { repo.observeRules().first { it.isNotEmpty() } }

            repo.applyRules(listOf(feedRule("r", 1)))

            assertEquals(listOf("r"), emitted.await().map { it.id })
        }
}

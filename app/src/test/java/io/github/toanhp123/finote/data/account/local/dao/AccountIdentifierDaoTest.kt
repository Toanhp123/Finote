package io.github.toanhp123.finote.data.account.local.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.account.local.entity.AccountIdentifierEntity
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountIdentifierDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.accountIdentifierDao()

    private fun key(
        mask: String,
        accountId: String = "a",
        kind: IdentifierKind = IdentifierKind.ACCOUNT,
    ) = AccountIdentifierEntity("pkg", kind, mask, accountId)

    @Before
    fun setUp() =
        runTest {
            listOf("a", "b").forEach { dbRule.db.accountDao().insert(account(id = it, bankPackageName = "pkg")) }
        }

    @Test
    fun `primary key rejects a second owner`() =
        runTest {
            dao.insert(key("1234"))

            val failure = runCatching { dao.insert(key("1234", accountId = "b")) }.exceptionOrNull()

            assertTrue("$failure", failure is android.database.SQLException)
            assertEquals("a", dao.get("pkg", IdentifierKind.ACCOUNT, "1234")?.accountId)
            // Kind is part of the key: a card with the same digits is a different identifier.
            dao.insert(key("1234", accountId = "b", kind = IdentifierKind.CARD))
            assertEquals(2, dao.getByPackage("pkg").size)
        }

    @Test
    fun `repoint moves one key`() =
        runTest {
            dao.insert(key("1111"))
            dao.insert(key("2222"))

            assertEquals(1, dao.repoint("pkg", IdentifierKind.ACCOUNT, "1111", "b"))

            assertEquals(mapOf("1111" to "b", "2222" to "a"), dao.getAll().associate { it.mask to it.accountId })
        }

    @Test
    fun `moveAll moves every key of an account`() =
        runTest {
            dao.insert(key("1111"))
            dao.insert(key("2222"))
            dao.insert(key("3333", accountId = "b"))

            assertEquals(2, dao.moveAll(from = "a", to = "b"))

            assertEquals(setOf("b"), dao.getAll().map { it.accountId }.toSet())
        }

    @Test
    fun `toString hides the mask`() {
        val text = AccountIdentifierEntity("pkg", IdentifierKind.CARD, "M-SECRET", "a").toString()

        assertFalse(text, text.contains("SECRET"))
    }
}

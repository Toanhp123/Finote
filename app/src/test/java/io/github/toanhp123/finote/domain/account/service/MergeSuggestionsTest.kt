package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.testAccount
import org.junit.Assert.assertEquals
import org.junit.Test

class MergeSuggestionsTest {
    private fun bankAccount(
        id: String,
        createdAt: Long = 1L,
        pkg: String = "pkg.bank",
    ): Account = testAccount(id = id).copy(bankPackageName = pkg, createdAt = createdAt)

    private fun suggest(
        accounts: List<Account>,
        identified: Set<String> = emptySet(),
        dismissed: Set<Pair<String, String>> = emptySet(),
        hasTransfer: (String, String) -> Boolean = { _, _ -> false },
    ) = mergeSuggestions(accounts, identified, dismissed, hasTransfer)

    @Test
    fun `same bank and type with one unidentified account is suggested both ways`() {
        val result = suggest(listOf(bankAccount("a"), bankAccount("b")), identified = setOf("a"))

        assertEquals(mapOf("a" to "b", "b" to "a"), result)
    }

    @Test
    fun `two identified accounts are not suggested`() {
        val result = suggest(listOf(bankAccount("a"), bankAccount("b")), identified = setOf("a", "b"))

        assertEquals(emptyMap<String, String>(), result)
    }

    @Test
    fun `dismissed pair is not suggested from either side`() {
        val accounts = listOf(bankAccount("b"), bankAccount("a"))

        assertEquals(emptyMap<String, String>(), suggest(accounts, dismissed = setOf("a" to "b")))
    }

    @Test
    fun `transfer between blocks the suggestion`() {
        val asked = mutableListOf<Pair<String, String>>()
        val result =
            suggest(listOf(bankAccount("b"), bankAccount("a"))) { x, y ->
                asked += x to y
                true
            }

        assertEquals(emptyMap<String, String>(), result)
        assertEquals(listOf("a" to "b"), asked)
    }

    @Test
    fun `partner with identifiers wins over an older one without`() {
        val accounts =
            listOf(
                bankAccount("x", createdAt = 9L),
                bankAccount("old", createdAt = 1L),
                bankAccount("keyed", createdAt = 5L),
            )

        val result = suggest(accounts, identified = setOf("keyed"))

        assertEquals("keyed", result["x"])
        assertEquals("keyed", result["old"])
        assertEquals("old", result["keyed"])
    }

    @Test
    fun `oldest then lowest id breaks a tie`() {
        val accounts =
            listOf(
                bankAccount("x", createdAt = 9L),
                bankAccount("c", createdAt = 2L),
                bankAccount("b", createdAt = 2L),
                bankAccount("a", createdAt = 3L),
            )

        assertEquals("b", suggest(accounts)["x"])
    }

    @Test
    fun `archived, deleted, other bank and other type never pair`() {
        val accounts =
            listOf(
                bankAccount("a"),
                bankAccount("archived").copy(isArchived = true),
                bankAccount("gone").copy(deletedAt = 5L),
                bankAccount("other", pkg = "pkg.other"),
                bankAccount("typed").copy(type = AccountType.SAVINGS),
                testAccount(id = "unbanked"),
            )

        assertEquals(emptyMap<String, String>(), suggest(accounts))
    }

    @Test
    fun `ids of deleted owners in the identified set do not matter`() {
        val result = suggest(listOf(bankAccount("a"), bankAccount("b")), identified = setOf("deleted-owner"))

        assertEquals(mapOf("a" to "b", "b" to "a"), result)
    }
}

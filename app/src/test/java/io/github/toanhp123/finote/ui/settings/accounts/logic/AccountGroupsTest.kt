package io.github.toanhp123.finote.ui.settings.accounts.logic

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.testAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountGroupsTest {
    // why: one named default per account field the grouping tests vary.
    @Suppress("LongParameterList")
    private fun account(
        id: String,
        pkg: String? = "vcb",
        sortOrder: Int = 0,
        balance: Long = 0L,
        isIgnored: Boolean = false,
        isArchived: Boolean = false,
        deletedAt: Long? = null,
    ): Account =
        testAccount(id = id, name = id, initialBalance = balance).copy(
            bankPackageName = pkg,
            sortOrder = sortOrder,
            isIgnored = isIgnored,
            isArchived = isArchived,
            deletedAt = deletedAt,
        )

    private val names = mapOf("vcb" to "Vietcombank")

    @Test
    fun `same bank accounts form one group with the sum of non-ignored members`() {
        val items =
            groupAccounts(
                listOf(
                    account("a", balance = 100L, sortOrder = 0),
                    account("b", balance = 20L, sortOrder = 1),
                    account("c", balance = 5L, sortOrder = 2, isIgnored = true),
                ),
                names,
            )

        val group = items.single() as AccountListItem.Group
        assertEquals("vcb", group.packageName)
        assertEquals("Vietcombank", group.bankName)
        assertEquals(listOf("a", "b", "c"), group.members.map { it.id })
        assertEquals(120L, group.total)
    }

    @Test
    fun `a group falls back to the package name when the bank has no name`() {
        val items = groupAccounts(listOf(account("a", pkg = "x.y"), account("b", pkg = "x.y")), names)

        assertEquals("x.y", (items.single() as AccountListItem.Group).bankName)
    }

    @Test
    fun `a bank with one account is a single row`() {
        val items = groupAccounts(listOf(account("a"), account("cash", pkg = null)), names)

        assertTrue(items.all { it is AccountListItem.Single })
        assertEquals(listOf("a", "cash"), items.map { (it as AccountListItem.Single).account.id })
    }

    @Test
    fun `a bank with one live account and one archived is a single row`() {
        val items = groupAccounts(listOf(account("a"), account("old", isArchived = true)), names)

        assertEquals(listOf(AccountListItem.Single(account("a"))), items)
    }

    @Test
    fun `archived and deleted accounts are left out of the items`() {
        val items =
            groupAccounts(
                listOf(account("a", pkg = "mb"), account("old", isArchived = true), account("gone", deletedAt = 1L)),
                names,
            )

        assertEquals(listOf("a"), items.flatMap { it.accounts }.map { it.id })
    }

    @Test
    fun `items follow the lowest member sortOrder`() {
        val items =
            groupAccounts(
                listOf(
                    account("cash", pkg = null, sortOrder = 1),
                    account("v2", sortOrder = 5),
                    account("v1", sortOrder = 0),
                    account("mb", pkg = "mb", sortOrder = 3),
                ),
                names,
            )

        assertEquals(listOf("vcb", "cash", "mb"), items.map { it.key.removePrefix(GROUP_KEY_PREFIX) })
        assertEquals(listOf("v1", "v2"), items.first().accounts.map { it.id })
    }

    @Test
    fun `reorderedIds keeps members together in their order`() {
        val items =
            groupAccounts(
                listOf(
                    account("v1", sortOrder = 0),
                    account("cash", pkg = null, sortOrder = 1),
                    account("v2", sortOrder = 2),
                ),
                names,
            )

        assertEquals(listOf("v1", "v2", "cash"), reorderedIds(items))
    }
}

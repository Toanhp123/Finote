package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.testAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountRulesTest {
    private fun bank(
        id: String,
        pkg: String? = "vcb",
        deletedAt: Long? = null,
    ) = testAccount(id = id).copy(bankPackageName = pkg, deletedAt = deletedAt)

    // checkNew

    @Test
    fun `new valid account is allowed, negative initial balance too`() {
        assertNull(AccountRules.checkNew(testAccount()))
        assertNull(AccountRules.checkNew(testAccount(initialBalance = -300_000L)))
    }

    @Test
    fun `new blank name is BlankName`() {
        assertEquals(AccountError.BlankName, AccountRules.checkNew(testAccount(name = "")))
        assertEquals(AccountError.BlankName, AccountRules.checkNew(testAccount(name = "   ")))
    }

    @Test
    fun `new non VND currency is UnsupportedCurrency`() =
        assertEquals(AccountError.UnsupportedCurrency, AccountRules.checkNew(testAccount(currency = "USD")))

    @Test
    fun `new blank name wins over a bad currency`() =
        assertEquals(AccountError.BlankName, AccountRules.checkNew(testAccount(name = " ", currency = "USD")))

    // checkDelete

    @Test
    fun `delete with no live transactions is allowed`() = assertNull(AccountRules.checkDelete(0))

    @Test
    fun `delete with live transactions is HasTransactions`() =
        assertEquals(AccountError.HasTransactions, AccountRules.checkDelete(1))

    // checkMerge

    private fun checkMerge(
        sourceId: String = "a",
        targetId: String = "b",
        source: Account? = bank("a"),
        target: Account? = bank("b"),
        transfersBetween: Int = 0,
    ) = AccountRules.checkMerge(sourceId, targetId, source, target, transfersBetween)

    @Test
    fun `merge of two live accounts of one bank without transfers is allowed`() = assertNull(checkMerge())

    @Test
    fun `merge into itself is SAME_ACCOUNT`() =
        assertEquals(MergeRejection.SAME_ACCOUNT, checkMerge(sourceId = "a", targetId = "a", target = bank("a")))

    @Test
    fun `merge with a missing or deleted account is ACCOUNT_GONE`() {
        assertEquals(MergeRejection.ACCOUNT_GONE, checkMerge(source = bank("a", deletedAt = 5L)))
        assertEquals(MergeRejection.ACCOUNT_GONE, checkMerge(target = bank("b", deletedAt = 5L)))
        assertEquals(MergeRejection.ACCOUNT_GONE, checkMerge(target = null))
        assertEquals(MergeRejection.ACCOUNT_GONE, checkMerge(source = null))
    }

    @Test
    fun `merge across banks or without a bank key is DIFFERENT_BANK`() {
        assertEquals(MergeRejection.DIFFERENT_BANK, checkMerge(target = bank("b", pkg = "other")))
        assertEquals(MergeRejection.DIFFERENT_BANK, checkMerge(target = bank("b", pkg = null)))
        assertEquals(MergeRejection.DIFFERENT_BANK, checkMerge(source = bank("a", pkg = null)))
        assertEquals(
            MergeRejection.DIFFERENT_BANK,
            checkMerge(source = bank("a", pkg = null), target = bank("b", pkg = null)),
        )
    }

    @Test
    fun `merge with a transfer between the two is HAS_TRANSFER`() =
        assertEquals(MergeRejection.HAS_TRANSFER, checkMerge(transfersBetween = 1))

    @Test
    fun `merge rejection order is same, gone, bank, transfer`() {
        val gone = bank("a", pkg = "x", deletedAt = 5L)

        assertEquals(MergeRejection.SAME_ACCOUNT, checkMerge("a", "a", gone, null, 1))
        assertEquals(
            MergeRejection.ACCOUNT_GONE,
            checkMerge(source = gone, target = bank("b", pkg = "y"), transfersBetween = 1),
        )
        assertEquals(
            MergeRejection.DIFFERENT_BANK,
            checkMerge(target = bank("b", pkg = "y"), transfersBetween = 1),
        )
    }
}

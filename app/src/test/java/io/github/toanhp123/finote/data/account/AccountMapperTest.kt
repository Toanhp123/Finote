package io.github.toanhp123.finote.data.account

import io.github.toanhp123.finote.data.account
import io.github.toanhp123.finote.domain.account.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountMapperTest {
    @Test
    fun `round-trips every field`() {
        val entity =
            account(id = "acc-9", name = "Visa", type = AccountType.CREDIT_CARD, sortOrder = 3).copy(
                initialBalance = -300_000L,
                currentBalance = -450_000L,
                currency = "VND",
                icon = "credit_card",
                color = 0xFF1565C0.toInt(),
                isExcludedFromNetWorth = true,
                isArchived = true,
                isInitialized = false,
                createdAt = 11L,
                updatedAt = 22L,
                deletedAt = 5L,
            )
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `bank key round trips`() {
        val entity = account(bankPackageName = "com.VCB", accountNumberMask = "1234")
        val account = entity.toDomain()
        assertEquals("com.VCB", account.bankPackageName)
        assertEquals("1234", account.accountNumberMask)
        assertEquals(entity, account.toEntity())
    }

    @Test
    fun `merge and ignore fields round trip`() {
        val entity = account().copy(mergedIntoId = "b", isIgnored = true)
        val account = entity.toDomain()
        assertEquals("b", account.mergedIntoId)
        assertEquals(true, account.isIgnored)
        assertEquals(entity, account.toEntity())
    }

    @Test
    fun `nameKey survives round trip`() {
        val entity = account().copy(nameKey = "sys_cash")
        assertEquals("sys_cash", entity.toDomain().toEntity().nameKey)
    }
}

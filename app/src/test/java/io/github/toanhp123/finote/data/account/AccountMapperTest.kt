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
}

package io.github.toanhp123.finote.data.transaction

import io.github.toanhp123.finote.data.transaction
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionMapperTest {
    @Test
    fun `transfer round-trips through the domain model`() {
        val entity = transaction(toAccountId = "acc-2", categoryId = "cat-1", type = TransactionType.TRANSFER)
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `adjustment with null merchant round-trips`() {
        val entity =
            transaction(type = TransactionType.ADJUSTMENT)
                .copy(adjustmentDirection = AdjustmentDirection.OUT, merchantName = null, deletedAt = 9_000L)
        assertEquals(entity, entity.toDomain().toEntity())
    }

    @Test
    fun `domain carries every extended field`() {
        val domain = transaction(categoryId = "cat-1").toDomain()
        assertEquals("cat-1", domain.categoryId)
        assertEquals("Test merchant", domain.merchantName)
        assertEquals("Test note", domain.note)
        assertTrue(domain.isExcludedFromAnalytics)
        assertEquals(1_700_000_000_000L, domain.createdAt)
        assertEquals(1_700_000_000_000L, domain.updatedAt)
    }
}

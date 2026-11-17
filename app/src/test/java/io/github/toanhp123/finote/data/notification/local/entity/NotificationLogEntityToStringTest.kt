package io.github.toanhp123.finote.data.notification.local.entity

import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NotificationLogEntityToStringTest {
    @Test
    fun `entity hides text, amounts, balance and mask`() {
        val entity =
            NotificationLogEntity(
                id = "log1",
                packageName = "pkg",
                channelId = "CH-SECRET",
                title = "T-SECRET",
                content = "C-SECRET",
                contentHash = "H-SECRET",
                postTime = 1L,
                receivedAt = 2L,
                matchedRuleId = "R-SECRET",
                parsedAmount = 987_654L,
                parsedType = "EXPENSE",
                parsedAccountMask = "M-SECRET",
                parsedPostBalance = 123_456L,
                parsedMerchant = "MER-SECRET",
                parsedNote = "N-SECRET",
                status = NotificationStatus.PENDING,
                failureReason = "F-SECRET",
                linkedTransactionId = "TX-SECRET",
            )

        assertEquals("NotificationLogEntity(id=log1, packageName=pkg, status=PENDING)", entity.toString())
        listOf("SECRET", "987654", "123456").forEach {
            assertFalse("leaks $it", entity.toString().contains(it))
        }
    }
}

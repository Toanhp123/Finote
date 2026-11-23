package io.github.toanhp123.finote.domain.account.model

import io.github.toanhp123.finote.domain.account.testAccount
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountUsableTest {
    @Test
    fun `live account is usable`() {
        assertTrue(testAccount().isUsable)
    }

    @Test
    fun `deleted, archived or ignored account is not usable`() {
        assertFalse(testAccount().copy(deletedAt = 1).isUsable)
        assertFalse(testAccount().copy(isArchived = true).isUsable)
        assertFalse(testAccount().copy(isIgnored = true).isUsable)
    }
}

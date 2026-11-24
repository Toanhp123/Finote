package io.github.toanhp123.finote.ui.settings.accountedit.logic

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountEditMessagesTest {
    @Test
    fun `every merge rejection has its own message`() {
        val messages = MergeRejection.entries.map { it.messageRes() }

        assertEquals(MergeRejection.entries.size, messages.distinct().size)
    }

    @Test
    fun `a merge rejection inside an error shows the rejection message`() {
        assertEquals(
            MergeRejection.HAS_TRANSFER.messageRes(),
            AccountError.MergeRejected(MergeRejection.HAS_TRANSFER).messageRes(),
        )
    }
}

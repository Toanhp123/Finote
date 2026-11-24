package io.github.toanhp123.finote.data.notification.local.entity

import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import org.junit.Assert.assertEquals
import org.junit.Test

class UserIgnoreKeywordToStringTest {
    @Test
    fun `entity and model hide the keyword`() {
        assertEquals(
            "UserIgnoreKeywordEntity(id=k1, packageName=pkg)",
            UserIgnoreKeywordEntity("k1", "pkg", "KW-SECRET", "NORM-SECRET", 1L).toString(),
        )
        assertEquals(
            "UserIgnoreKeyword(id=k1, packageName=pkg)",
            UserIgnoreKeyword("k1", "pkg", "KW-SECRET", "NORM-SECRET", 1L).toString(),
        )
    }
}

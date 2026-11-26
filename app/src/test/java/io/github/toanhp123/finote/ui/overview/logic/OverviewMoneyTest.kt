package io.github.toanhp123.finote.ui.overview.logic

import io.github.toanhp123.finote.domain.report.model.BucketTotal
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverviewMoneyTest {
    private fun bucket(
        amount: Long,
        id: String = "c",
    ) = BucketTotal(ReportFilter.ByCategory(id), amount)

    @Test
    fun `positive total leaves negative buckets out`() {
        val buckets = listOf(bucket(100_000), bucket(50_000), bucket(-30_000), bucket(0))

        assertEquals(150_000L, positiveTotal(buckets))
    }

    @Test
    fun `share rounds half up`() {
        assertEquals(13, share(1, 8)) // 12.5
        assertEquals(11, share(1, 9)) // 11.1
        assertEquals(100, share(500, 500))
    }

    @Test
    fun `no share passes 100 with a negative bucket present`() {
        val buckets = listOf(bucket(100_000), bucket(50_000), bucket(-30_000))
        val total = positiveTotal(buckets)

        val shares = buckets.mapNotNull { share(it.amount, total) }

        assertEquals(listOf(67, 33), shares)
        assertTrue(shares.all { it <= 100 })
    }

    @Test
    fun `a zero or negative bucket has no share`() {
        assertNull(share(-30_000, 150_000))
        assertNull(share(0, 150_000))
    }

    @Test
    fun `no positive total means no share`() {
        assertNull(share(10, 0))
        assertNull(share(10, -5))
    }
}

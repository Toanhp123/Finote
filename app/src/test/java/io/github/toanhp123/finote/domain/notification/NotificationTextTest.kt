package io.github.toanhp123.finote.domain.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {
    @Test
    fun `normalize drops accents, d stroke and case`() {
        assertEquals("ma xac thuc dang", normalizeForMatch("Mã XÁC THỰC Đăng"))
    }

    @Test
    fun `normalize handles decomposed input`() {
        assertEquals("ma", normalizeForMatch("Mã"))
    }

    @Test
    fun `keyword inside a word does not match`() {
        assertNull(firstMatchingKeyword("Thanh toan tai Kichi Hotpot", listOf("OTP")))
        assertNull(firstMatchingKeyword("Chuyen tien cho Luu Dai Trang", listOf("ưu đãi")))
    }

    @Test
    fun `keyword as a whole word matches and returns the original keyword`() {
        assertEquals("OTP", firstMatchingKeyword("Mã OTP: 123456", listOf("OTP")))
        assertEquals("OTP", firstMatchingKeyword("Smart OTP thanh cong", listOf("OTP")))
        assertEquals("ưu đãi", firstMatchingKeyword("Uu dai dac quyen", listOf("ưu đãi")))
    }

    @Test
    fun `keyword may be glued to digits but not to letters`() {
        assertEquals("OTP", firstMatchingKeyword("Ma OTP123456", listOf("OTP")))
        assertEquals("SmartOTP", firstMatchingKeyword("Ma SmartOTP: 123456", listOf("SmartOTP")))
        assertNull(firstMatchingKeyword("Thanh toan OTPshop", listOf("OTP")))
        assertNull(firstMatchingKeyword("Thanh toan tai Kichi Hotpot", listOf("OTP", "SmartOTP")))
    }

    @Test
    fun `keyword list order wins over text order`() {
        assertEquals("chúc mừng", firstMatchingKeyword("Ưu đãi và chúc mừng", listOf("chúc mừng", "ưu đãi")))
    }

    @Test
    fun `no keywords never match`() {
        assertNull(firstMatchingKeyword("x", emptyList()))
        assertNull(firstMatchingKeyword("x", listOf(" ")))
    }

    @Test
    fun `hash is stable lowercase sha256 hex`() {
        val hash = contentHash("com.VCB", 1L, "t", "c")
        assertEquals(hash, contentHash("com.VCB", 1L, "t", "c"))
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hash changes with each field and keeps field borders`() {
        val base = contentHash("com.VCB", 1L, "t", "c")
        assertNotEquals(base, contentHash("com.VCB", 2L, "t", "c"))
        assertNotEquals(base, contentHash("com.VCB", 1L, "t2", "c"))
        assertNotEquals(base, contentHash("com.VCB", 1L, "t", "c2"))
        assertNotEquals(contentHash("p", 1L, "a", "b"), contentHash("p", 1L, "ab", ""))
    }

    @Test
    fun `blank big text falls through to text`() {
        assertEquals("SD TK -50,000", notificationContent("  \n ", null, "SD TK -50,000"))
    }

    @Test
    fun `text lines skip blank lines`() {
        assertEquals("l1\nl2", notificationContent(null, arrayOf("l1", " ", "l2"), "x"))
    }

    @Test
    fun `big text wins and is trimmed`() {
        assertEquals("big", notificationContent("  big \n", arrayOf("l1"), "x"))
    }

    @Test
    fun `all blank gives null`() {
        assertNull(notificationContent(" ", arrayOf(" "), ""))
        assertNull(notificationContent(null, null, null))
    }
}

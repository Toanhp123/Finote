package io.github.toanhp123.finote.architecture

import org.junit.Assert.assertEquals
import org.junit.Test

class NameWordsTest {
    @Test
    fun `camel case splits into lower case words`() {
        assertEquals(listOf("total", "amount"), "totalAmount".nameWords())
    }

    @Test
    fun `screaming snake case splits on underscore`() {
        assertEquals(listOf("max", "fee"), "MAX_FEE".nameWords())
    }

    @Test
    fun `a digit starts a new word`() {
        assertEquals(listOf("post", "balance", "2"), "postBalance2".nameWords())
    }

    @Test
    fun `an upper case run ends before a capitalized word`() {
        assertEquals(listOf("vnd", "amount"), "VNDAmount".nameWords())
        assertEquals(listOf("parse", "url"), "parseURL".nameWords())
    }

    @Test
    fun `a digit between words is its own word`() {
        assertEquals(listOf("fee", "2", "amount"), "fee2Amount".nameWords())
    }

    @Test
    fun `empty and single character names`() {
        assertEquals(emptyList<String>(), "".nameWords())
        assertEquals(emptyList<String>(), "_".nameWords())
        assertEquals(listOf("a"), "A".nameWords())
    }

    @Test
    fun `a word that only contains a money word stays whole`() {
        assertEquals(listOf("feedback"), "feedback".nameWords())
    }
}

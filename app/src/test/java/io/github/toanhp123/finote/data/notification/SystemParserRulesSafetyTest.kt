package io.github.toanhp123.finote.data.notification

import org.junit.Assert.assertTrue
import org.junit.Test

class SystemParserRulesSafetyTest {
    private val length = 4_000
    private val budgetMs = 50L

    private val adversarialInputs =
        listOf(
            "1".repeat(length),
            "1,".repeat(length / 2),
            "1.".repeat(length / 2),
            " ".repeat(length),
            "SD TK 0011 " + "a".repeat(length - 11),
        )

    @Test
    fun `every rule finishes adversarial inputs within 50 ms`() {
        val slow = mutableListOf<String>()
        for (rule in SystemParserRules) {
            val regex = Regex(rule.regexPattern)
            regex.find("warm up") // Compile and class-load once, so the timing below is the match only.
            adversarialInputs.forEachIndexed { index, input ->
                for ((isPrefixed, text) in listOf(false to input, true to "title\n$input")) {
                    val start = System.nanoTime()
                    regex.find(text)
                    val ms = (System.nanoTime() - start) / 1_000_000
                    if (ms >= budgetMs) slow += "${rule.id} input#$index prefixed=$isPrefixed: $ms ms"
                }
            }
        }
        assertTrue("Rules over $budgetMs ms: $slow", slow.isEmpty())
    }
}

package com.example.leitner

/** Pure Leitner-box logic (no Android dependencies). */
object Leitner {
    const val MAX_BOX = 5
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    // Box 1: every day, Box 2: 2 days, Box 3: 4 days, Box 4: 8 days, Box 5: 16 days (mastered)
    private val INTERVAL_DAYS = intArrayOf(1, 2, 4, 8, 16)

    fun intervalDays(box: Int): Int = INTERVAL_DAYS[(box - 1).coerceIn(0, MAX_BOX - 1)]

    /**
     * Correct answer -> next box (stays in box 5 once mastered).
     * Wrong answer   -> back to box 1.
     * The card becomes due again after the interval of its NEW box.
     */
    fun review(card: Card, correct: Boolean, now: Long): Card {
        val newBox = if (correct) minOf(card.box + 1, MAX_BOX) else 1
        return card.copy(box = newBox, dueAt = now + intervalDays(newBox) * DAY_MS)
    }

    // Letters (any language) with optional inner apostrophes, e.g. don't / it’s
    private val WORD_REGEX = Regex("""\p{L}+(?:['’]\p{L}+)*""")

    /** Extracts unique, lower-cased words from a block of text (punctuation and duplicates removed). */
    fun extractWords(text: String): List<String> =
        WORD_REGEX.findAll(text)
            .map { it.value.lowercase() }
            .filter { it.length >= 2 }
            .distinct()
            .toList()
}

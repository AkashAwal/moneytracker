package com.akash.moneytracker.domain

/** Free-form labels on a transaction ("goa", "work"), stored as one comma-separated lowercase string. */
object Tags {
    const val MAX_TAGS = 5
    private const val MAX_LENGTH = 20
    private val separators = Regex("[\\s,#]+")

    /** "Goa trip, #Work" -> ["goa", "trip", "work"]. Keeps letters, digits, '-' and '_'. */
    fun parse(input: String): List<String> =
        input.split(separators)
            .map { raw -> raw.lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(MAX_LENGTH) }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(MAX_TAGS)

    fun encode(tags: List<String>): String = tags.joinToString(",")

    fun decode(stored: String): List<String> = if (stored.isBlank()) emptyList() else stored.split(",")

    /** "#goa #work" for showing under a transaction. */
    fun display(stored: String): String = decode(stored).joinToString(" ") { "#$it" }

    /** Text to put back in the edit field. */
    fun toInput(stored: String): String = decode(stored).joinToString(" ") { "#$it" }
}

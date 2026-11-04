package com.akash.moneytracker.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Rupee formatting and parsing. All amounts are Long paise (1 rupee = 100 paise). */
object Money {
    /** Parses user input like "1,250.50" into paise. Returns null for blank or invalid input. */
    fun parseToPaise(input: String): Long? {
        val cleaned = input.replace(",", "").trim()
        if (cleaned.isEmpty()) return null
        return try {
            BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (_: NumberFormatException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    /** "₹1,23,456" with Indian digit grouping; paise are shown only when non-zero. */
    fun format(paise: Long, withSign: Boolean = false): String {
        val negative = paise < 0
        val abs = kotlin.math.abs(paise)
        val rupees = abs / 100
        val rem = (abs % 100).toInt()
        val body = group(rupees.toString()) + if (rem != 0) "." + rem.toString().padStart(2, '0') else ""
        val sign = when {
            negative -> "-"
            withSign && paise > 0 -> "+"
            else -> ""
        }
        return "$sign₹$body"
    }

    /** Plain editable text for an amount field: "1250" or "1250.5". */
    fun toInput(paise: Long): String {
        val rupees = paise / 100
        val rem = (paise % 100).toInt()
        return when {
            rem == 0 -> rupees.toString()
            rem % 10 == 0 -> "$rupees.${rem / 10}"
            else -> "$rupees.${rem.toString().padStart(2, '0')}"
        }
    }

    /** Keeps digits and a single decimal point with at most two decimals, for amount text fields. */
    fun sanitize(input: String): String {
        val sb = StringBuilder()
        var seenDot = false
        var decimals = 0
        for (c in input) {
            when {
                c.isDigit() -> {
                    if (seenDot) {
                        if (decimals >= 2) continue
                        decimals++
                    }
                    if (sb.length < 12) sb.append(c)
                }
                c == '.' && !seenDot -> {
                    seenDot = true
                    sb.append(if (sb.isEmpty()) "0." else ".")
                }
            }
        }
        return sb.toString()
    }

    private fun group(digits: String): String {
        if (digits.length <= 3) return digits
        val head = digits.dropLast(3)
        val tail = digits.takeLast(3)
        return head.reversed().chunked(2).joinToString(",").reversed() + "," + tail
    }
}

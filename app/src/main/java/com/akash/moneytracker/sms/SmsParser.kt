package com.akash.moneytracker.sms

import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.Money

data class ParsedSms(val amountPaise: Long, val type: TxType, val title: String)

/**
 * Best-effort reader for Indian bank and UPI texts ("Rs.250 spent on card at SWIGGY", "INR 1,500 debited ... to VPA x@upi").
 * It errs on the side of ignoring a message: nothing is added without the user confirming it, but a stream of
 * false suggestions would be worse than missing an odd format.
 */
object SmsParser {
    private const val MAX_PAISE = 10_00_00_000L * 100

    private val amount = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    private val notATransaction = Regex(
        """(?i)\botp\b|one[- ]time|do not share|will be (?:debited|credited)|is due|due on|minimum amount|statement|""" +
            """reminder|autopay (?:scheduled|due)|eligible|pre-?approved|apply now""",
    )
    private val debit = Regex("""(?i)\b(?:debited|spent|paid|purchase|withdrawn|sent|payment of|txn of)\b""")
    private val credit = Regex("""(?i)\b(?:credited|received|deposited|refund(?:ed)?)\b""")

    private const val NAME = """[A-Za-z0-9@&._' -]{2,30}?"""
    private const val END = """(?=\s+on\b|\s+via\b|\s+ref\b|\s+upi\b|\s+txn\b|\s+using\b|\s+for\b|\s+avl\b|\s+bal\b|[.,;(]|$)"""
    private val spentAt = Regex("""(?i)\bat\s+($NAME)$END""")
    private val sentTo = Regex("""(?i)\bto\s+(?:vpa\s+)?($NAME)$END""")
    private val receivedFrom = Regex("""(?i)\b(?:from|by)\s+($NAME)$END""")

    fun parse(body: String): ParsedSms? {
        // A text is attacker-controlled: drop control and text-direction characters before reading anything from it.
        val text = body.filter { c -> !hidden(c) }.replace(Regex("\\s+"), " ").trim()
        if (notATransaction.containsMatchIn(text)) return null

        val type = when {
            debit.containsMatchIn(text) -> TxType.EXPENSE
            credit.containsMatchIn(text) -> TxType.INCOME
            else -> return null
        }
        val paise = amount.find(text)?.groupValues?.get(1)?.let(Money::parseToPaise) ?: return null
        // Anything above ₹10 crore is far more likely a spoofed or garbled text than a payment.
        if (paise <= 0 || paise > MAX_PAISE) return null

        val title = when (type) {
            TxType.EXPENSE -> spentAt.find(text) ?: sentTo.find(text)
            TxType.INCOME -> receivedFrom.find(text)
        }?.groupValues?.get(1)?.let(::clean)

        return ParsedSms(paise, type, title ?: if (type == TxType.EXPENSE) "Bank payment" else "Bank credit")
    }

    /** Control characters and text-direction overrides: invisible, and able to make text display as something it isn't. */
    private fun hidden(c: Char): Boolean =
        c.isISOControl() && c != '\n' && c != '\r' && c != '\t' ||
            c.code in 0x202A..0x202E || c.code in 0x2066..0x2069 || c.code == 0x200E || c.code == 0x200F

    /** Drops account fragments like "A/c XX1234" and tidies the name; returns null if nothing useful is left. */
    private fun clean(raw: String): String? {
        val name = raw.filter { c -> !hidden(c) }.trim().trim('-', '.', ' ')
        if (name.isEmpty() || Regex("""(?i)^(?:your |the )?(?:a/c|acct|account|card|xx|x+\d).*""").matches(name)) return null
        return name.take(30)
    }
}

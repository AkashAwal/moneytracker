package com.akash.moneytracker

import com.akash.moneytracker.data.Backup
import com.akash.moneytracker.data.BackupCrypto
import com.akash.moneytracker.data.BackupLimits
import com.akash.moneytracker.data.BudgetEntity
import com.akash.moneytracker.data.DataSnapshot
import com.akash.moneytracker.data.InvalidBackupException
import com.akash.moneytracker.data.RecurringEntity
import com.akash.moneytracker.data.Settings
import com.akash.moneytracker.data.TransactionEntity
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.data.WrongBackupPasswordException
import com.akash.moneytracker.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.util.Base64

/** Backups, exports and texts are untrusted input; these check that hostile versions are refused or neutralised. */
class BackupEncryptionTest {
    private val password = "correct horse battery".toCharArray()
    private val secret = """{"app":"MoneyTracker","note":"salary 85000 at ACME"}"""

    // The minimum iteration count keeps the tests fast; production uses 600,000.
    private fun seal(text: String = secret, pw: CharArray = password) = BackupCrypto.encrypt(text, pw, iterations = 100_000)

    private fun assertWrongPassword(block: () -> Unit) {
        try {
            block()
            fail("expected a wrong-password failure")
        } catch (_: WrongBackupPasswordException) {
        }
    }

    private fun assertMalformed(block: () -> Unit) {
        try {
            block()
            fail("expected the file to be rejected as malformed")
        } catch (_: InvalidBackupException) {
        }
    }

    @Test fun `round trips with the right password`() {
        assertEquals(secret, BackupCrypto.decrypt(seal(), password))
    }

    @Test fun `the file reveals nothing about the contents`() {
        val sealed = seal()
        assertTrue(BackupCrypto.isEncrypted(sealed))
        assertFalse(sealed.contains("ACME") || sealed.contains("salary") || sealed.contains("MoneyTracker"))
        assertFalse(String(Base64.getDecoder().decode(sealed.removePrefix(BackupCrypto.PREFIX)), Charsets.ISO_8859_1).contains("ACME"))
    }

    @Test fun `a wrong password is refused`() {
        assertWrongPassword { BackupCrypto.decrypt(seal(), "not the password".toCharArray()) }
    }

    @Test fun `the same data encrypts differently every time`() {
        assertNotEquals(seal(), seal()) // fresh salt and nonce
    }

    @Test fun `any modified byte is detected`() {
        val sealed = seal()
        val bytes = Base64.getDecoder().decode(sealed.removePrefix(BackupCrypto.PREFIX))
        // Flip one bit in the ciphertext, in the nonce and in the salt (inside the authenticated header).
        for (index in listOf(bytes.size - 1, 30, 10)) {
            val tampered = bytes.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() }
            val text = BackupCrypto.PREFIX + Base64.getEncoder().encodeToString(tampered)
            assertWrongPassword { BackupCrypto.decrypt(text, password) }
        }
    }

    @Test fun `a weakened iteration count is refused`() {
        val bytes = Base64.getDecoder().decode(seal().removePrefix(BackupCrypto.PREFIX)).copyOf()
        // Rewrite the iteration count (bytes 4..7) to 1, which an attacker would use to make guessing cheap.
        bytes[4] = 0; bytes[5] = 0; bytes[6] = 0; bytes[7] = 1
        assertMalformed { BackupCrypto.decrypt(BackupCrypto.PREFIX + Base64.getEncoder().encodeToString(bytes), password) }
    }

    @Test fun `an absurd iteration count can't be used to freeze the phone`() {
        val bytes = Base64.getDecoder().decode(seal().removePrefix(BackupCrypto.PREFIX)).copyOf()
        bytes[4] = 0x7F; bytes[5] = -1; bytes[6] = -1; bytes[7] = -1 // about two billion
        assertMalformed { BackupCrypto.decrypt(BackupCrypto.PREFIX + Base64.getEncoder().encodeToString(bytes), password) }
    }

    @Test fun `garbage and too-short files are rejected as malformed`() {
        assertMalformed { BackupCrypto.decrypt(BackupCrypto.PREFIX + "AAAA", password) }
        assertMalformed { BackupCrypto.decrypt(BackupCrypto.PREFIX + "!!!not base64!!!", password) }
    }

    @Test fun `a file cut short fails the integrity check`() {
        // Truncation leaves a well-formed header but the authentication tag no longer matches.
        assertWrongPassword { BackupCrypto.decrypt(seal().dropLast(40), password) }
    }

    @Test fun `plain backups aren't mistaken for encrypted ones`() {
        assertFalse(BackupCrypto.isEncrypted("""{"app":"MoneyTracker"}"""))
    }
}

class HostileBackupTest {
    private val snapshot = DataSnapshot(
        categories = listOf(food, shopping, salary),
        transactions = listOf(TransactionEntity(1, 56_050, TxType.EXPENSE, food.id, "Pizza", 20_761, 1_200, 1_000L, null, "goa")),
        budgets = listOf(BudgetEntity(food.id, rupees(600))),
        recurring = listOf(RecurringEntity(7, 99_900, TxType.EXPENSE, shopping.id, "Gym", 10, 540, 202_611)),
        settings = Settings(1_000_000, 200_000, 202_611, "Akash"),
    )
    private val valid = Backup.toJson(snapshot)

    private fun assertRejected(text: String, why: String) {
        try {
            Backup.fromJson(text)
            fail("should have been rejected: $why")
        } catch (_: InvalidBackupException) {
        }
    }

    @Test fun `a normal backup is accepted`() {
        assertEquals(snapshot, Backup.fromJson(valid))
    }

    @Test fun `oversized input is refused before parsing`() {
        assertRejected("x".repeat(BackupLimits.MAX_BYTES + 1), "too large")
    }

    @Test fun `impossible amounts are refused`() {
        assertRejected(valid.replace("\"amountPaise\": 56050", "\"amountPaise\": -5"), "negative amount")
        assertRejected(valid.replace("\"amountPaise\": 56050", "\"amountPaise\": 0"), "zero amount")
        assertRejected(valid.replace("\"amountPaise\": 56050", "\"amountPaise\": ${Long.MAX_VALUE}"), "overflowing amount")
    }

    @Test fun `impossible dates and times are refused`() {
        assertRejected(valid.replace("\"epochDay\": 20761", "\"epochDay\": 9999999999"), "far-future day")
        assertRejected(valid.replace("\"minuteOfDay\": 1200", "\"minuteOfDay\": 5000"), "time of day")
        assertRejected(valid.replace("\"dayOfMonth\": 10", "\"dayOfMonth\": 40"), "day of month")
        assertRejected(valid.replace("\"goalMonth\": 202611", "\"goalMonth\": 202699"), "goal month")
    }

    @Test fun `duplicate ids are refused`() {
        val twice = snapshot.copy(transactions = snapshot.transactions + snapshot.transactions)
        assertRejected(Backup.toJson(twice), "duplicate transaction ids")
    }

    @Test fun `references and types must be consistent`() {
        val wrongType = snapshot.copy(transactions = listOf(snapshot.transactions.first().copy(type = TxType.INCOME)))
        assertRejected(Backup.toJson(wrongType), "expense filed under an expense category as income")
        val incomeBudget = snapshot.copy(budgets = listOf(BudgetEntity(salary.id, rupees(1))))
        assertRejected(Backup.toJson(incomeBudget), "budget on an income category")
        val dangling = snapshot.copy(categories = listOf(salary))
        assertRejected(Backup.toJson(dangling), "missing category")
    }

    @Test fun `tags can't carry markup or other symbols`() {
        assertRejected(valid.replace("\"tags\": \"goa\"", "\"tags\": \"goa,<script>\""), "markup in tags")
        assertRejected(valid.replace("\"tags\": \"goa\"", "\"tags\": \"${"a,".repeat(100)}\""), "too many tags")
    }

    @Test fun `control and direction-override characters are stripped from text`() {
        val sneaky = valid.replace("\"note\": \"Pizza\"", "\"note\": \"Pi\\u202Ezza\\u0007 \\u200Fok\"")
        val note = Backup.fromJson(sneaky).transactions.single().note
        assertEquals("Pizza ok", note)
    }

    @Test fun `not json at all is refused`() {
        assertRejected("<html>hello</html>", "html")
        assertRejected("", "empty")
    }
}

class ExportInjectionTest {
    private fun csvFor(note: String, tags: String = "") =
        Backup.toCsv(listOf(row(1, food, 100, LocalDate.of(2026, 11, 4), note, tags = tags)))

    @Test fun `spreadsheet formulas in a note are defused`() {
        for (hostile in listOf("=1+1", "+cmd|' /C calc'!A0", "-2+3", "@SUM(A1:A9)", "=HYPERLINK(\"http://evil\",\"click\")")) {
            val line = csvFor(hostile).lines()[1]
            assertTrue("formula not defused for $hostile: $line", line.contains("'" + hostile.take(3)))
            assertFalse("starts a cell with a formula character: $line", Regex("(^|,)\"?[=+\\-@]").containsMatchIn(line.substringAfter(",expense,")))
        }
    }

    @Test fun `ordinary text and amounts are untouched`() {
        val line = csvFor("Lunch").lines()[1]
        assertTrue(line.contains(",Lunch,"))
        assertTrue(line.endsWith(",100.00")) // the amount column is never prefixed
    }
}

class SmsSpoofingTest {
    @Test fun `an absurd amount is treated as spoofed`() {
        assertNull(SmsParser.parse("Rs 99999999999 debited from A/c XX1234"))
        assertNull(SmsParser.parse("Rs 1,00,00,00,000 credited to your account"))
    }

    @Test fun `text direction tricks are removed from a merchant name`() {
        val parsed = SmsParser.parse("Rs 100 spent at \u202EEVIL\u0007SHOP on 01-11-26")!!
        assertFalse(parsed.title.any { it.isISOControl() || it.code == 0x202E })
        assertEquals("EVILSHOP", parsed.title)
    }
}

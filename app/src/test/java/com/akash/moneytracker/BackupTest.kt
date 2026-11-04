package com.akash.moneytracker

import com.akash.moneytracker.data.Backup
import com.akash.moneytracker.data.BudgetEntity
import com.akash.moneytracker.data.DataSnapshot
import com.akash.moneytracker.data.InvalidBackupException
import com.akash.moneytracker.data.RecurringEntity
import com.akash.moneytracker.data.Settings
import com.akash.moneytracker.data.TransactionEntity
import com.akash.moneytracker.data.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val snapshot = DataSnapshot(
        categories = listOf(food, shopping, salary),
        transactions = listOf(
            TransactionEntity(1, 56_050, TxType.EXPENSE, food.id, "Pizza, \"large\" 🍕", 20_761, 1_200, 1_000L, null, "goa,work"),
            TransactionEntity(2, 99_900, TxType.EXPENSE, shopping.id, "", 20_736, 540, 2_000L, 7L),
        ),
        budgets = listOf(BudgetEntity(food.id, rupees(600))),
        recurring = listOf(RecurringEntity(7, 99_900, TxType.EXPENSE, shopping.id, "Gym", 10, 540, 202_611)),
        settings = Settings(1_000_000, 200_000, 202_611, "Akash"),
    )

    private fun assertRejected(text: String) {
        try {
            Backup.fromJson(text)
            fail("expected the backup to be rejected")
        } catch (_: InvalidBackupException) {
        }
    }

    @Test fun `a backup restores to exactly what was saved`() {
        assertEquals(snapshot, Backup.fromJson(Backup.toJson(snapshot)))
    }

    @Test fun `a name that was never set stays unset`() {
        val unset = snapshot.copy(settings = snapshot.settings.copy(name = null))
        assertEquals(null, Backup.fromJson(Backup.toJson(unset)).settings.name)
    }

    @Test fun `rejects files that aren't backups`() {
        assertRejected("not json at all")
        assertRejected("{}")
        assertRejected("""{"app":"SomethingElse","version":1}""")
    }

    @Test fun `rejects backups from a newer version`() {
        assertRejected(Backup.toJson(snapshot).replace("\"version\": 1", "\"version\": 99"))
    }

    @Test fun `rejects transactions that point at a missing category`() {
        val broken = snapshot.copy(categories = listOf(salary)) // food and shopping vanished
        assertRejected(Backup.toJson(broken))
    }

    @Test fun `csv has one escaped row per transaction`() {
        val csv = Backup.toCsv(listOf(row(1, food, 560, LocalDate.of(2026, 11, 4), "Lunch, \"team\"")))
        val lines = csv.trim().lines()
        assertEquals("Date,Time,Type,Category,Note,Tags,Amount (INR)", lines[0])
        assertTrue(lines[1].startsWith("2026-11-04,"))
        assertTrue(lines[1].contains(",expense,Food,\"Lunch, \"\"team\"\"\","))
        assertTrue(lines[1].endsWith(",560.00"))
    }
}

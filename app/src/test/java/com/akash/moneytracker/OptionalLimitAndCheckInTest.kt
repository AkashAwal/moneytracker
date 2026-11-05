package com.akash.moneytracker

import com.akash.moneytracker.data.Backup
import com.akash.moneytracker.data.DataSnapshot
import com.akash.moneytracker.data.InvalidBackupException
import com.akash.moneytracker.data.Settings
import com.akash.moneytracker.domain.StreakCalc
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class CheckInStreakTest {
    private val today = LocalDate.of(2026, 11, 15)
    private fun day(offset: Long) = today.plusDays(offset).toEpochDay()

    // Logged by hand on the 13th and 14th; nothing yet on the 15th.
    private val rows = listOf(row(1, food, 50, today.minusDays(1)), row(2, food, 40, today.minusDays(2)))

    @Test fun `a day with nothing logged is not marked as logged`() {
        val streaks = StreakCalc.compute(rows, today)
        assertFalse(streaks.loggedToday)
        assertEquals(2, streaks.loggingStreak) // still alive: the day isn't over
    }

    @Test fun `spending nothing today extends the streak`() {
        val streaks = StreakCalc.compute(rows, today, setOf(day(0)))
        assertTrue(streaks.loggedToday)
        assertEquals(3, streaks.loggingStreak)
    }

    @Test fun `a quiet day in the middle no longer breaks the streak`() {
        // Logged on the 11th and the 14th; the 12th and 13th were quiet and marked as such.
        val gappy = listOf(row(1, food, 50, today.minusDays(4)), row(2, food, 40, today.minusDays(1)))
        assertEquals(1, StreakCalc.compute(gappy, today).loggingStreak)
        assertEquals(5, StreakCalc.compute(gappy, today, setOf(day(-3), day(-2), day(0))).loggingStreak)
    }

    @Test fun `an unmarked quiet day still breaks it`() {
        val gappy = listOf(row(1, food, 50, today.minusDays(4)), row(2, food, 40, today.minusDays(1)))
        assertEquals(1, StreakCalc.compute(gappy, today, setOf(day(-3))).loggingStreak) // the 13th is still a gap
    }

    @Test fun `marking a day does not change the no-spend count`() {
        assertEquals(
            StreakCalc.compute(rows, today).noSpendDays,
            StreakCalc.compute(rows, today, setOf(day(0), day(-3))).noSpendDays,
        )
    }
}

class OptionalDailyLimitTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val plan = Planning(emptyMap(), emptyList(), listOf(food))
    private val withGoal = Settings(
        expectedIncomePaise = rupees(30_000),
        savingsGoalPaise = rupees(5_000),
        goalMonth = 202_611,
        name = "Akash",
    )
    private val rows = listOf(row(1, food, 300, today))

    @Test fun `the daily limit is shown while it is switched on`() {
        val home = computeHome(withGoal, rows, today, plan)
        assertNotNull(home.budget)
        assertNotNull(home.forecast)
    }

    @Test fun `removing it takes the limit and the forecast off the screen but keeps the goal`() {
        val hidden = withGoal.copy(dailyLimitHidden = true)
        val home = computeHome(hidden, rows, today, plan)
        assertNull(home.budget)
        assertNull(home.forecast)
        assertEquals(rupees(5_000), home.settings.savingsGoalPaise) // bringing it back needs no re-entry
    }

    @Test fun `removing it does not affect spending totals or streaks`() {
        val shown = computeHome(withGoal, rows, today, plan)
        val hidden = computeHome(withGoal.copy(dailyLimitHidden = true), rows, today, plan)
        assertEquals(shown.spent, hidden.spent)
        assertEquals(shown.streaks, hidden.streaks)
    }

    @Test fun `a check-in in settings reaches the home streak`() {
        val home = computeHome(withGoal.copy(checkInDays = setOf(today.toEpochDay())), emptyList(), today, plan)
        assertTrue(home.streaks.loggedToday)
        assertEquals(1, home.streaks.loggingStreak)
    }
}

class OptionalSettingsBackupTest {
    private val snapshot = DataSnapshot(
        categories = listOf(food),
        transactions = emptyList(),
        budgets = emptyList(),
        recurring = emptyList(),
        settings = Settings(
            expectedIncomePaise = rupees(30_000),
            savingsGoalPaise = rupees(5_000),
            goalMonth = 202_611,
            name = "Akash",
            dailyLimitHidden = true,
            checkInDays = setOf(20_800L, 20_801L),
        ),
    )

    @Test fun `the hidden limit and no-spend days survive a backup`() {
        assertEquals(snapshot, Backup.fromJson(Backup.toJson(snapshot)))
    }

    @Test fun `backups made before these existed still restore, with the limit shown`() {
        val old = JSONObject(Backup.toJson(snapshot))
        old.getJSONObject("settings").remove("dailyLimitHidden")
        old.getJSONObject("settings").remove("checkInDays")
        val restored = Backup.fromJson(old.toString()).settings
        assertFalse(restored.dailyLimitHidden)
        assertTrue(restored.checkInDays.isEmpty())
    }

    @Test fun `a backup with an impossible no-spend day is refused`() {
        val hostile = JSONObject(Backup.toJson(snapshot))
        hostile.getJSONObject("settings").getJSONArray("checkInDays").put(9_000_000_000L)
        try {
            Backup.fromJson(hostile.toString())
            fail("expected the backup to be rejected")
        } catch (_: InvalidBackupException) {
        }
    }
}

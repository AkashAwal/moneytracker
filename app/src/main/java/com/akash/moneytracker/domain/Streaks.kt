package com.akash.moneytracker.domain

import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import java.time.LocalDate
import java.time.YearMonth

data class Streaks(
    /** Days in a row, ending today or yesterday, on which something was logged by hand. */
    val loggingStreak: Int,
    /** Finished days this month with no day-to-day spending at all. */
    val noSpendDays: Int,
)

object StreakCalc {
    fun compute(all: List<TransactionWithCategory>, today: LocalDate): Streaks {
        // Recurring bills log themselves, so they don't count as the user logging or spending.
        val manual = all.filter { it.tx.recurringId == null }
        val loggedDays = manual.map { it.tx.epochDay }.toSet()
        val spendDays = manual.filter { it.tx.type == TxType.EXPENSE }.map { it.tx.epochDay }.toSet()

        // The streak survives until the end of today, so start from yesterday if nothing is logged yet.
        var day = if (today.toEpochDay() in loggedDays) today.toEpochDay() else today.toEpochDay() - 1
        var streak = 0
        while (day in loggedDays) {
            streak++
            day--
        }

        val first = YearMonth.from(today).atDay(1).toEpochDay()
        val noSpend = (first until today.toEpochDay()).count { it !in spendDays }
        return Streaks(streak, noSpend)
    }
}

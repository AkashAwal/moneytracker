package com.akash.moneytracker.domain

import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import java.time.LocalDate
import java.time.YearMonth

data class Streaks(
    /** Days in a row, ending today or yesterday, on which something was logged by hand or marked "spent nothing". */
    val loggingStreak: Int,
    /** Finished days this month with no day-to-day spending at all. */
    val noSpendDays: Int,
    /** True once today has a logged transaction or a "spent nothing" check-in. */
    val loggedToday: Boolean,
)

object StreakCalc {
    /**
     * [checkInDays] are days the user said they had nothing to log. Without them a quiet day would break the
     * streak, since there is no transaction to count.
     */
    fun compute(all: List<TransactionWithCategory>, today: LocalDate, checkInDays: Set<Long> = emptySet()): Streaks {
        // Recurring bills log themselves, so they don't count as the user logging or spending.
        val manual = all.filter { it.tx.recurringId == null }
        val loggedDays = manual.map { it.tx.epochDay }.toSet() + checkInDays
        val spendDays = manual.filter { it.tx.type == TxType.EXPENSE }.map { it.tx.epochDay }.toSet()

        val todayDay = today.toEpochDay()
        val loggedToday = todayDay in loggedDays

        // The streak survives until the end of today, so start from yesterday if nothing is logged yet.
        var day = if (loggedToday) todayDay else todayDay - 1
        var streak = 0
        while (day in loggedDays) {
            streak++
            day--
        }

        val first = YearMonth.from(today).atDay(1).toEpochDay()
        val noSpend = (first until todayDay).count { it !in spendDays }
        return Streaks(streak, noSpend, loggedToday)
    }
}

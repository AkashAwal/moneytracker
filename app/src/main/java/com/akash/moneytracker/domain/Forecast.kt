package com.akash.moneytracker.domain

import java.time.LocalDate
import java.time.YearMonth

/** Where the month is heading if day-to-day spending carries on at the pace so far. */
data class Forecast(
    val projectedSpend: Long,
    val projectedSavings: Long,
    val goal: Long,
) {
    val onTrack: Boolean get() = projectedSavings >= goal
    val shortfall: Long get() = (goal - projectedSavings).coerceAtLeast(0)

    companion object {
        /** Needs a few days of data before the pace means anything. */
        private const val MIN_DAYS = 3

        /**
         * @param dailySpentSoFar day-to-day spending this month (recurring bills excluded)
         * @param fixedThisMonth recurring bills for the whole month, paid or still due
         */
        fun compute(income: Long, goal: Long, dailySpentSoFar: Long, fixedThisMonth: Long, today: LocalDate): Forecast? {
            if (today.dayOfMonth < MIN_DAYS) return null
            val daysInMonth = YearMonth.from(today).lengthOfMonth()
            val projectedDaily = dailySpentSoFar * daysInMonth / today.dayOfMonth
            val projectedSpend = projectedDaily + fixedThisMonth
            return Forecast(projectedSpend, income - projectedSpend, goal)
        }
    }
}

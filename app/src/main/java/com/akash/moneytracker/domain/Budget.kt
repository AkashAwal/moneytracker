package com.akash.moneytracker.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * How much can be spent today and still hit the monthly savings goal.
 *
 * The allowance is recomputed from what is left each day, so an overspend yesterday
 * automatically tightens today's limit and an underspend loosens it.
 */
data class DailyBudget(
    /** Total that can be spent this month: income minus the savings goal. */
    val monthlySpendable: Long,
    /** What can be spent today (never negative). */
    val todayAllowance: Long,
    val spentToday: Long,
    val spentThisMonth: Long,
    val daysLeft: Int,
    /** True if spending so far already makes the goal unreachable. */
    val goalAtRisk: Boolean,
    /** Recurring bills still due this month that were set aside before working out the limit. */
    val upcomingFixed: Long,
    /** What tomorrow's limit becomes after today's spending; null on the last day of the month. */
    val tomorrowAllowance: Long?,
) {
    val leftToday: Long get() = todayAllowance - spentToday
    val overToday: Boolean get() = spentToday > todayAllowance

    companion object {
        /**
         * @param income the larger of expected income and income actually logged this month
         * @param goal amount to save by month end
         * @param spentThisMonth day-to-day expenses this month including today (recurring bills excluded)
         * @param spentToday day-to-day expenses logged today
         * @param upcomingFixed recurring bills for this month, paid or still due, set aside up front
         */
        fun compute(
            income: Long,
            goal: Long,
            spentThisMonth: Long,
            spentToday: Long,
            today: LocalDate,
            upcomingFixed: Long = 0,
        ): DailyBudget {
            val daysInMonth = YearMonth.from(today).lengthOfMonth()
            val daysLeft = daysInMonth - today.dayOfMonth + 1 // today counts
            val spendable = income - goal - upcomingFixed
            val spentBeforeToday = spentThisMonth - spentToday
            val remainingBeforeToday = spendable - spentBeforeToday
            val allowance = (remainingBeforeToday / daysLeft).coerceAtLeast(0)
            return DailyBudget(
                monthlySpendable = spendable,
                todayAllowance = allowance,
                spentToday = spentToday,
                spentThisMonth = spentThisMonth,
                daysLeft = daysLeft,
                goalAtRisk = spendable - spentThisMonth < 0,
                upcomingFixed = upcomingFixed,
                tomorrowAllowance = if (daysLeft > 1) {
                    ((spendable - spentThisMonth) / (daysLeft - 1)).coerceAtLeast(0)
                } else null,
            )
        }
    }
}

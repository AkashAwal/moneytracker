package com.akash.moneytracker.domain

import com.akash.moneytracker.data.RecurringEntity
import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import java.time.LocalDate
import java.time.YearMonth

object Recurring {
    /** The day a rule fires in [month]; short months use their last day. */
    fun dueDate(rule: RecurringEntity, month: YearMonth): LocalDate =
        month.atDay(minOf(rule.dayOfMonth, month.lengthOfMonth()))

    fun monthOf(key: Int): YearMonth = YearMonth.of(key / 100, key % 100)

    fun keyOf(month: YearMonth): Int = month.year * 100 + month.monthValue

    /**
     * Where a brand-new rule starts counting from. If this month's due date has already passed
     * we don't backfill it (the user can log it by hand); otherwise it fires when the day arrives.
     */
    fun initialLastGenerated(dayOfMonth: Int, today: LocalDate): Int {
        val month = YearMonth.from(today)
        val due = month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
        return keyOf(if (due.isAfter(today)) month.minusMonths(1) else month)
    }

    /** Recurring expenses still to come this month. */
    fun upcomingExpenses(rules: List<RecurringEntity>, today: LocalDate): Long {
        val month = YearMonth.from(today)
        return rules
            .filter { it.type == TxType.EXPENSE && dueDate(it, month).isAfter(today) }
            .sumOf { it.amountPaise }
    }

    /** This month's expenses that came from recurring rules (already paid bills). */
    fun paidExpenses(monthTx: List<TransactionWithCategory>): Long =
        monthTx.filter { it.tx.type == TxType.EXPENSE && it.tx.recurringId != null }.sumOf { it.tx.amountPaise }

    /** "Monthly on the 5th" */
    fun describe(rule: RecurringEntity): String {
        val d = rule.dayOfMonth
        val suffix = when {
            d % 100 in 11..13 -> "th"
            d % 10 == 1 -> "st"
            d % 10 == 2 -> "nd"
            d % 10 == 3 -> "rd"
            else -> "th"
        }
        return "Monthly on the $d$suffix"
    }
}

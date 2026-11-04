package com.akash.moneytracker.domain

import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import java.time.LocalDate

/** The last seven days of spending against the seven before. */
data class WeeklySummary(
    val spent: Long,
    val previousSpent: Long,
    val topCategory: String?,
    val topCategoryTotal: Long,
) {
    /** Percent change from the previous week, or null when there's nothing to compare with. */
    val changePercent: Int?
        get() = if (previousSpent > 0) ((spent - previousSpent) * 100.0 / previousSpent).toInt() else null

    /** Worth a notification only if something was actually spent. */
    val isEmpty: Boolean get() = spent == 0L

    fun message(): String {
        val change = when (val c = changePercent) {
            null -> ""
            0 -> ", the same as the week before"
            else -> if (c < 0) ", ${-c}% less than the week before" else ", $c% more than the week before"
        }
        val top = if (topCategory != null) " Most of it went on $topCategory (${Money.format(topCategoryTotal)})." else ""
        return "You spent ${Money.format(spent)} in the last 7 days$change.$top"
    }

    companion object {
        fun compute(all: List<TransactionWithCategory>, today: LocalDate): WeeklySummary {
            val end = today.toEpochDay()
            val thisWeek = (end - 6)..end
            val lastWeek = (end - 13)..(end - 7)
            val expenses = all.filter { it.tx.type == TxType.EXPENSE }
            val current = expenses.filter { it.tx.epochDay in thisWeek }
            val top = current.groupBy { it.category.name }
                .mapValues { (_, rows) -> rows.sumOf { it.tx.amountPaise } }
                .maxByOrNull { it.value }
            return WeeklySummary(
                spent = current.sumOf { it.tx.amountPaise },
                previousSpent = expenses.filter { it.tx.epochDay in lastWeek }.sumOf { it.tx.amountPaise },
                topCategory = top?.key,
                topCategoryTotal = top?.value ?: 0L,
            )
        }
    }
}

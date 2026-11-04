package com.akash.moneytracker.domain

import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class CategorySlice(val name: String, val emoji: String, val color: Long, val total: Long)

data class TagStat(val tag: String, val total: Long, val count: Int)

data class ItemStat(val title: String, val emoji: String, val total: Long, val count: Int)

/** Spending over a stretch of the month or a weekday, averaged per day so short stretches compare fairly. */
data class PeriodStat(val label: String, val total: Long, val avgPerDay: Long)

data class MonthInsights(
    val month: YearMonth,
    val spent: Long,
    val income: Long,
    val prevSpent: Long,
    val slices: List<CategorySlice>,
    /** Day-to-day spending per day (recurring bills excluded). Index 0 is day 1; days after today are 0. */
    val daily: List<Long>,
    /** How many days of the month have happened (all of them for past months). */
    val daysCounted: Int,
    val weeks: List<PeriodStat>,
    val weekdays: List<PeriodStat>,
    val items: List<ItemStat>,
    /** Spending per tag; a transaction with two tags counts toward both. */
    val tags: List<TagStat>,
) {
    /** Patterns are noise until a few days of the month have passed. */
    val hasPatterns: Boolean get() = spent > 0 && daysCounted >= 3

    companion object {
        private const val OTHERS_COLOR = 0xFF8E8E93
        private const val MAX_SLICES = 6

        fun compute(all: List<TransactionWithCategory>, month: YearMonth, today: LocalDate): MonthInsights {
            val range = month.epochRange()
            val prevRange = month.minusMonths(1).epochRange()
            val expenses = all.filter { it.tx.type == TxType.EXPENSE }
            val inMonth = expenses.filter { it.tx.epochDay in range }

            val length = month.lengthOfMonth()
            val daysCounted = when {
                month == YearMonth.from(today) -> today.dayOfMonth
                month.isBefore(YearMonth.from(today)) -> length
                else -> 0
            }

            val daily = LongArray(length)
            // Day-to-day patterns leave out recurring bills: rent landing on a Thursday would otherwise make
            // "Thursday" your priciest day. Totals and the category split still include them.
            inMonth.filter { it.tx.recurringId == null }
                .forEach { daily[(it.tx.epochDay - range.first).toInt()] += it.tx.amountPaise }

            return MonthInsights(
                month = month,
                spent = inMonth.sumOf { it.tx.amountPaise },
                income = all.filter { it.tx.type == TxType.INCOME && it.tx.epochDay in range }.sumOf { it.tx.amountPaise },
                prevSpent = expenses.filter { it.tx.epochDay in prevRange }.sumOf { it.tx.amountPaise },
                slices = slices(inMonth),
                daily = daily.toList(),
                daysCounted = daysCounted,
                weeks = weeks(daily, length, daysCounted),
                weekdays = weekdays(daily, month, daysCounted),
                items = items(inMonth),
                tags = tags(inMonth),
            )
        }

        private fun tags(inMonth: List<TransactionWithCategory>): List<TagStat> =
            inMonth
                .flatMap { row -> Tags.decode(row.tx.tags).map { it to row.tx.amountPaise } }
                .groupBy({ it.first }, { it.second })
                .map { (tag, amounts) -> TagStat(tag, amounts.sum(), amounts.size) }
                .sortedByDescending { it.total }
                .take(6)

        private fun slices(inMonth: List<TransactionWithCategory>): List<CategorySlice> {
            val sorted = inMonth
                .groupBy { it.category.id }
                .map { (_, rows) ->
                    val c = rows.first().category
                    CategorySlice(c.name, c.emoji, c.color, rows.sumOf { it.tx.amountPaise })
                }
                .sortedByDescending { it.total }
            if (sorted.size <= MAX_SLICES + 1) return sorted
            val rest = sorted.drop(MAX_SLICES)
            return sorted.take(MAX_SLICES) + CategorySlice("Others", "•••", OTHERS_COLOR, rest.sumOf { it.total })
        }

        /** Averages are shown in whole rupees. */
        private fun roundToRupee(paise: Long): Long = (paise + 50) / 100 * 100

        /** Days 1–7, 8–14, 15–21, 22–28, 29–end; weeks that haven't started yet are left out. */
        private fun weeks(daily: LongArray, length: Int, daysCounted: Int): List<PeriodStat> =
            (0..4).mapNotNull { w ->
                val start = w * 7 + 1
                val end = minOf(start + 6, length)
                if (start > length) return@mapNotNull null
                val counted = minOf(end, daysCounted) - start + 1
                if (counted <= 0) return@mapNotNull null
                val total = (start..minOf(end, daysCounted)).sumOf { daily[it - 1] }
                PeriodStat("Days $start–$end", total, roundToRupee(total / counted))
            }

        private fun weekdays(daily: LongArray, month: YearMonth, daysCounted: Int): List<PeriodStat> =
            DayOfWeek.entries.mapNotNull { dow ->
                val days = (1..daysCounted).filter { month.atDay(it).dayOfWeek == dow }
                if (days.isEmpty()) return@mapNotNull null
                val total = days.sumOf { daily[it - 1] }
                PeriodStat(
                    dow.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                    total,
                    roundToRupee(total / days.size),
                )
            }

        /** Groups by what the user typed in the note ("Pizza"); entries without a note group by category. */
        private fun items(inMonth: List<TransactionWithCategory>): List<ItemStat> =
            inMonth
                .groupBy { row -> row.tx.note.trim().lowercase().ifEmpty { "category:${row.category.id}" } }
                .map { (_, rows) ->
                    val first = rows.first()
                    val note = rows.firstNotNullOfOrNull { it.tx.note.trim().ifEmpty { null } }
                    ItemStat(
                        title = note ?: first.category.name,
                        emoji = first.category.emoji,
                        total = rows.sumOf { it.tx.amountPaise },
                        count = rows.size,
                    )
                }
                .sortedByDescending { it.total }
                .take(5)
    }
}

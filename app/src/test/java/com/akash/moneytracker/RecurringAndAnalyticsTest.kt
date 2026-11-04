package com.akash.moneytracker

import com.akash.moneytracker.data.RecurringEntity
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.ActivityFilter
import com.akash.moneytracker.domain.MonthInsights
import com.akash.moneytracker.domain.Recurring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringTest {
    private fun rule(day: Int, amount: Long = 1_000, type: TxType = TxType.EXPENSE) = RecurringEntity(
        id = 1, amountPaise = rupees(amount), type = type, categoryId = 1, dayOfMonth = day, lastGeneratedMonth = 202610,
    )

    @Test fun `short months use their last day`() {
        assertEquals(LocalDate.of(2027, 2, 28), Recurring.dueDate(rule(31), YearMonth.of(2027, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), Recurring.dueDate(rule(31), YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2026, 11, 30), Recurring.dueDate(rule(31), YearMonth.of(2026, 11)))
        assertEquals(LocalDate.of(2026, 11, 5), Recurring.dueDate(rule(5), YearMonth.of(2026, 11)))
    }

    @Test fun `a new rule doesn't backfill a day that already passed`() {
        assertEquals(202610, Recurring.initialLastGenerated(5, LocalDate.of(2026, 11, 4))) // due later this month
        assertEquals(202611, Recurring.initialLastGenerated(5, LocalDate.of(2026, 11, 5))) // due today -> logged now
        assertEquals(202611, Recurring.initialLastGenerated(5, LocalDate.of(2026, 11, 20))) // already passed
        assertEquals(202512, Recurring.initialLastGenerated(10, LocalDate.of(2026, 1, 3))) // year rollover
    }

    @Test fun `only expenses still to come are set aside`() {
        val today = LocalDate.of(2026, 11, 4)
        val rules = listOf(rule(6, 5_000), rule(28, 50_000, TxType.INCOME), rule(3, 999))
        assertEquals(rupees(5_000), Recurring.upcomingExpenses(rules, today))
    }

    @Test fun `paid recurring expenses are those created by a rule`() {
        val d = LocalDate.of(2026, 11, 2)
        val rows = listOf(row(1, food, 199, d, recurringId = 7), row(2, food, 500, d), row(3, salary, 9_000, d, recurringId = 8))
        assertEquals(rupees(199), Recurring.paidExpenses(rows))
    }

    @Test fun `describes the schedule with ordinals`() {
        listOf(1 to "1st", 2 to "2nd", 3 to "3rd", 4 to "4th", 11 to "11th", 12 to "12th", 13 to "13th", 21 to "21st", 22 to "22nd", 31 to "31st")
            .forEach { (day, text) -> assertEquals("Monthly on the $text", Recurring.describe(rule(day))) }
    }
}

class AnalyticsTest {
    private val nov = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 15)
    private val rows = listOf(
        row(1, food, 100, LocalDate.of(2026, 11, 1), "Pizza"),
        row(2, food, 300, LocalDate.of(2026, 11, 2), "pizza"),
        row(3, shopping, 800, LocalDate.of(2026, 11, 9), "Shirt"),
        row(4, salary, 5_000, LocalDate.of(2026, 11, 10)),
        row(5, food, 1_000, LocalDate.of(2026, 10, 20)),
    )
    private val insights = MonthInsights.compute(rows, nov, today)

    @Test fun `totals only count this month's expenses`() {
        assertEquals(rupees(1_200), insights.spent)
        assertEquals(rupees(5_000), insights.income)
        assertEquals(rupees(1_000), insights.prevSpent)
        assertEquals(15, insights.daysCounted)
    }

    @Test fun `splits spending into days of the month`() {
        assertEquals(rupees(100), insights.daily[0])
        assertEquals(rupees(300), insights.daily[1])
        assertEquals(rupees(800), insights.daily[8])
        assertEquals(30, insights.daily.size)
    }

    @Test fun `weeks are averaged per elapsed day and future weeks are skipped`() {
        assertEquals(listOf("Days 1–7", "Days 8–14", "Days 15–21"), insights.weeks.map { it.label })
        assertEquals(rupees(57), insights.weeks[0].avgPerDay) // 400 over 7 days, rounded to whole rupees
        assertEquals(rupees(114), insights.weeks[1].avgPerDay) // 800 over 7 days
        assertEquals(0L, insights.weeks[2].avgPerDay) // only the 15th has happened
    }

    @Test fun `averages each weekday over the days that have happened`() {
        val mon = insights.weekdays.first { it.label == "Mon" } // Nov 2 and 9
        assertEquals(rupees(550), mon.avgPerDay)
        val sun = insights.weekdays.first { it.label == "Sun" } // Nov 1, 8, 15
        assertEquals(rupees(33), sun.avgPerDay)
    }

    @Test fun `groups items by note ignoring case, falling back to category`() {
        assertEquals(listOf("Shirt", "Pizza"), insights.items.map { it.title })
        assertEquals(2, insights.items.last().count)
        assertEquals(rupees(400), insights.items.last().total)
    }

    @Test fun `recurring bills count in totals but not in the day-to-day patterns`() {
        // Rent logged by a rule on Thursday Nov 5 must not make Thursday "your priciest day".
        val withRent = rows + row(9, shopping, 14_000, LocalDate.of(2026, 11, 5), "Rent", recurringId = 3)
        val i = MonthInsights.compute(withRent, nov, today)
        assertEquals(rupees(15_200), i.spent) // the bill is in the total...
        assertEquals(0L, i.daily[4]) // ...but not on its day in the chart
        assertEquals(null, i.weekdays.firstOrNull { it.label == "Thu" }?.takeIf { it.total > 0 })
        assertEquals(rupees(14_000), i.slices.first { it.name == "Shopping" }.total - rupees(800)) // and still in the category split
    }

    @Test fun `category slices are ordered by size`() {
        assertEquals(listOf("Shopping", "Food"), insights.slices.map { it.name })
    }

    @Test fun `patterns need a few days of data`() {
        assertTrue(insights.hasPatterns)
        assertFalse(MonthInsights.compute(rows, nov, LocalDate.of(2026, 11, 2)).hasPatterns)
    }
}

class ActivityFilterTest {
    private val d = LocalDate.of(2026, 11, 1)
    private val pizza = row(1, food, 250, d, "Pizza night")
    private val shirt = row(2, shopping, 900, d.plusDays(3), "Shirt")
    private val pay = row(3, salary, 10_000, d.plusDays(1))
    private val all = listOf(pizza, shirt, pay)

    private fun ActivityFilter.apply() = all.filter(::matches).map { it.tx.id }

    @Test fun `no filter matches everything`() {
        assertEquals(listOf(1L, 2L, 3L), ActivityFilter().apply())
        assertFalse(ActivityFilter().isActive)
    }

    @Test fun `search covers notes, category names and amounts`() {
        assertEquals(listOf(1L), ActivityFilter(query = "pizza").apply())
        assertEquals(listOf(2L), ActivityFilter(query = "shopping").apply())
        assertEquals(listOf(1L), ActivityFilter(query = "250").apply())
    }

    @Test fun `filters combine`() {
        assertEquals(listOf(3L), ActivityFilter(type = TxType.INCOME).apply())
        assertEquals(listOf(2L), ActivityFilter(categoryId = shopping.id).apply())
        assertEquals(listOf(2L, 3L), ActivityFilter(minPaise = rupees(500)).apply())
        assertEquals(listOf(1L), ActivityFilter(maxPaise = rupees(500), type = TxType.EXPENSE, toDay = d.toEpochDay()).apply())
        assertEquals(listOf(3L, 2L).sorted(), ActivityFilter(fromDay = d.plusDays(1).toEpochDay()).apply().sorted())
    }

    @Test fun `counts active filter groups for the badge`() {
        val f = ActivityFilter(query = "x", type = TxType.EXPENSE, fromDay = 1, toDay = 2, minPaise = 1)
        assertEquals(3, f.activeFilters) // type, dates, amount; search text isn't counted
        assertTrue(f.isActive)
    }
}

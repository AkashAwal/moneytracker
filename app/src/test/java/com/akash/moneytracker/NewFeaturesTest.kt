package com.akash.moneytracker

import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.ActivityFilter
import com.akash.moneytracker.domain.Forecast
import com.akash.moneytracker.domain.MonthInsights
import com.akash.moneytracker.domain.StreakCalc
import com.akash.moneytracker.domain.Tags
import com.akash.moneytracker.domain.WeeklySummary
import com.akash.moneytracker.notify.Scheduler
import com.akash.moneytracker.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class TagsTest {
    @Test fun `parses free text into clean tags`() {
        assertEquals(listOf("goa", "trip", "work"), Tags.parse("Goa trip, #Work"))
        assertEquals(listOf("a", "b"), Tags.parse("#a #a #b"))
        assertEquals(listOf("c", "rd"), Tags.parse("c++ #R&D"))
        assertEquals(emptyList<String>(), Tags.parse("  # , "))
    }

    @Test fun `limits how many tags one transaction can carry`() {
        assertEquals(Tags.MAX_TAGS, Tags.parse("a b c d e f g").size)
    }

    @Test fun `stores and shows tags`() {
        val stored = Tags.encode(listOf("goa", "work"))
        assertEquals("goa,work", stored)
        assertEquals(listOf("goa", "work"), Tags.decode(stored))
        assertEquals("#goa #work", Tags.display(stored))
        assertEquals(emptyList<String>(), Tags.decode(""))
    }

    @Test fun `tags show up in totals and filters`() {
        val d = LocalDate.of(2026, 11, 3)
        val rows = listOf(
            row(1, food, 500, d, tags = "goa,work"),
            row(2, shopping, 300, d, tags = "goa"),
            row(3, food, 100, d),
        )
        val stats = MonthInsights.compute(rows, YearMonth.of(2026, 11), LocalDate.of(2026, 11, 10)).tags
        assertEquals(listOf("goa", "work"), stats.map { it.tag })
        assertEquals(rupees(800), stats.first().total)
        assertEquals(2, stats.first().count)

        assertEquals(listOf(1L, 2L), rows.filter(ActivityFilter(tag = "goa")::matches).map { it.tx.id })
        assertEquals(listOf(1L), rows.filter(ActivityFilter(query = "#work")::matches).map { it.tx.id })
    }
}

class StreakTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val rows = listOf(
        row(1, food, 20, today),
        row(2, food, 100, today.minusDays(1)),
        row(3, food, 50, today.minusDays(2)),
        row(4, food, 999, today.minusDays(3), recurringId = 9), // a bill that logged itself
        row(5, food, 70, today.minusDays(4)),
        row(6, salary, 5_000, today.minusDays(5)),
    )

    @Test fun `counts consecutive days logged by hand`() {
        assertEquals(3, StreakCalc.compute(rows, today).loggingStreak) // the 12th has only the auto-logged bill
    }

    @Test fun `a streak is still alive until the day is over`() {
        assertEquals(3, StreakCalc.compute(rows, today.plusDays(1)).loggingStreak) // nothing logged yet on the 16th
        assertEquals(0, StreakCalc.compute(rows, today.plusDays(2)).loggingStreak)
    }

    @Test fun `no-spend days are finished days without day-to-day spending`() {
        // Nov 1..14 is 14 days; spending happened on the 14th, 13th and 11th. Income and the bill don't count.
        assertEquals(11, StreakCalc.compute(rows, today).noSpendDays)
    }
}

class ForecastTest {
    private val nov10 = LocalDate.of(2026, 11, 10)

    @Test fun `projects the month from the pace so far plus fixed bills`() {
        val f = Forecast.compute(rupees(30_000), rupees(5_000), rupees(3_000), rupees(6_000), nov10)!!
        assertEquals(rupees(15_000), f.projectedSpend) // 3,000 over 10 days -> 9,000 for 30 days, plus 6,000 of bills
        assertEquals(rupees(15_000), f.projectedSavings)
        assertTrue(f.onTrack)
    }

    @Test fun `reports a shortfall against the goal`() {
        val f = Forecast.compute(rupees(30_000), rupees(20_000), rupees(3_000), rupees(6_000), nov10)!!
        assertTrue(!f.onTrack)
        assertEquals(rupees(5_000), f.shortfall)
    }

    @Test fun `waits for a few days of data`() {
        assertNull(Forecast.compute(rupees(30_000), rupees(5_000), rupees(100), 0, LocalDate.of(2026, 11, 2)))
    }
}

class WeeklySummaryTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test fun `compares the last seven days with the seven before`() {
        val rows = listOf(
            row(1, food, 300, today.minusDays(5)),
            row(2, shopping, 700, today.minusDays(3)),
            row(3, food, 200, today.minusDays(12)), // the week before
            row(4, salary, 9_000, today.minusDays(1)), // income isn't spending
        )
        val s = WeeklySummary.compute(rows, today)
        assertEquals(rupees(1_000), s.spent)
        assertEquals(rupees(200), s.previousSpent)
        assertEquals(400, s.changePercent)
        assertEquals("Shopping", s.topCategory)
        assertTrue(s.message().contains("₹1,000") && s.message().contains("400% more") && s.message().contains("Shopping"))
    }

    @Test fun `a week with no spending isn't worth a notification`() {
        assertTrue(WeeklySummary.compute(listOf(row(1, salary, 100, today)), today).isEmpty)
    }
}

class SchedulerTest {
    private val evening = 21 * 60

    @Test fun `daily reminder fires today if the time is still ahead`() {
        assertEquals(Duration.ofHours(1), Scheduler.delayUntilNext(LocalDateTime.of(2026, 11, 4, 20, 0), evening))
        assertEquals(Duration.ofHours(23), Scheduler.delayUntilNext(LocalDateTime.of(2026, 11, 4, 22, 0), evening))
    }

    @Test fun `a time that is about to pass waits for tomorrow`() {
        val d = Scheduler.delayUntilNext(LocalDateTime.of(2026, 11, 4, 20, 59, 30), evening)
        assertTrue(d > Duration.ofHours(23))
    }

    @Test fun `weekly summary lands on the coming Sunday evening`() {
        // Wed 4 Nov 12:00 -> Sun 8 Nov 20:00
        assertEquals(Duration.ofHours(4 * 24 + 8), Scheduler.delayUntilNextSunday(LocalDateTime.of(2026, 11, 4, 12, 0), 20))
        // Sun 8 Nov 21:00 is already past 20:00, so a week later
        assertEquals(Duration.ofHours(7 * 24 - 1), Scheduler.delayUntilNextSunday(LocalDateTime.of(2026, 11, 8, 21, 0), 20))
    }
}

class SmsParserTest {
    @Test fun `reads a card spend with a merchant`() {
        val p = SmsParser.parse("Rs.250.00 spent on HDFC Bank Card x1234 at SWIGGY on 04-Nov-26. Avl bal Rs.5000")!!
        assertEquals(TxType.EXPENSE, p.type)
        assertEquals(25_000L, p.amountPaise)
        assertEquals("SWIGGY", p.title)
    }

    @Test fun `reads a UPI debit`() {
        val p = SmsParser.parse("INR 1,500.00 debited from A/c XX1234 on 04-11-26 to VPA zomato@upi (UPI Ref 123)")!!
        assertEquals(TxType.EXPENSE, p.type)
        assertEquals(150_000L, p.amountPaise)
        assertEquals("zomato@upi", p.title)
    }

    @Test fun `reads a person-to-person payment`() {
        val p = SmsParser.parse("Sent Rs.120.00 from HDFC Bank A/c *1234 to RAMESH KUMAR on 04/11/26 UPI Ref 1234")!!
        assertEquals(TxType.EXPENSE, p.type)
        assertEquals("RAMESH KUMAR", p.title)
    }

    @Test fun `reads a salary credit`() {
        val p = SmsParser.parse("Rs 50000 credited to your A/c XX1234 on 01-11-26 by ACME CORP Salary")!!
        assertEquals(TxType.INCOME, p.type)
        assertEquals(5_000_000L, p.amountPaise)
        assertTrue(p.title.contains("ACME"))
    }

    @Test fun `falls back to a generic title`() {
        assertEquals("Bank payment", SmsParser.parse("Rs.75 debited from A/c XX9876")!!.title)
        assertEquals("Bank credit", SmsParser.parse("Rs 100 credited")!!.title)
    }

    @Test fun `ignores texts that aren't a completed payment`() {
        assertNull(SmsParser.parse("123456 is your OTP for txn of Rs 500. Do not share it with anyone."))
        assertNull(SmsParser.parse("Your credit card bill of Rs 1,200 is due on 10-Nov. Pay now."))
        assertNull(SmsParser.parse("Rs 500 will be debited from your account tomorrow for autopay."))
        assertNull(SmsParser.parse("Hey! Lunch tomorrow?"))
        assertNotNull(SmsParser.parse("Rs 10 debited"))
    }
}

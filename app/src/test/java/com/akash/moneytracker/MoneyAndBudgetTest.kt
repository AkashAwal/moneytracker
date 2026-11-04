package com.akash.moneytracker

import com.akash.moneytracker.domain.BudgetLevel
import com.akash.moneytracker.domain.Budgets
import com.akash.moneytracker.domain.DailyBudget
import com.akash.moneytracker.domain.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MoneyTest {
    @Test fun `formats with Indian digit grouping`() {
        assertEquals("₹12,34,567", Money.format(rupees(1_234_567)))
        assertEquals("₹999", Money.format(rupees(999)))
        assertEquals("₹1,000", Money.format(rupees(1000)))
        assertEquals("₹1,00,000", Money.format(rupees(100_000)))
    }

    @Test fun `shows paise only when non-zero`() {
        assertEquals("₹560.50", Money.format(56_050))
        assertEquals("₹0.05", Money.format(5))
        assertEquals("₹0", Money.format(0))
    }

    @Test fun `signs`() {
        assertEquals("-₹15", Money.format(-1500))
        assertEquals("+₹1", Money.format(100, withSign = true))
    }

    @Test fun `parses user input`() {
        assertEquals(125_050L, Money.parseToPaise("1,250.50"))
        assertEquals(1235L, Money.parseToPaise("12.345")) // half-up
        assertNull(Money.parseToPaise(""))
        assertNull(Money.parseToPaise("abc"))
    }

    @Test fun `sanitizes amount fields`() {
        assertEquals("0.5", Money.sanitize(".5"))
        assertEquals("1.23", Money.sanitize("1.234"))
        assertEquals("1.23", Money.sanitize("1.2.3"))
        assertEquals("123", Money.sanitize("1a2b3"))
    }

    @Test fun `round-trips through the edit field`() {
        assertEquals("1250.5", Money.toInput(125_050))
        assertEquals("1250", Money.toInput(125_000))
        assertEquals("1250.05", Money.toInput(125_005))
    }
}

class DailyBudgetTest {
    private val nov10 = LocalDate.of(2026, 11, 10) // 21 days left including today

    @Test fun `allowance spreads what's left over the remaining days`() {
        val b = DailyBudget.compute(rupees(30_000), rupees(5_000), rupees(2_000), 0, nov10)
        assertEquals(109_523L, b.todayAllowance) // (25,000 - 2,000) / 21
        assertEquals(21, b.daysLeft)
        assertFalse(b.overToday)
        assertFalse(b.goalAtRisk)
    }

    @Test fun `overspending today tightens tomorrow but not today`() {
        val b = DailyBudget.compute(rupees(30_000), rupees(5_000), rupees(5_000), rupees(3_000), nov10)
        assertEquals(109_523L, b.todayAllowance) // unchanged by today's own spending
        assertTrue(b.overToday)
        assertEquals(100_000L, b.tomorrowAllowance) // (25,000 - 5,000) / 20
    }

    @Test fun `recurring bills are set aside before the limit is worked out`() {
        val b = DailyBudget.compute(rupees(30_000), rupees(5_000), rupees(2_000), 0, nov10, upcomingFixed = rupees(6_000))
        assertEquals(80_952L, b.todayAllowance) // (30,000 - 5,000 - 6,000 - 2,000) / 21
        assertEquals(rupees(6_000), b.upcomingFixed)
    }

    @Test fun `flags a goal that can no longer be reached`() {
        val b = DailyBudget.compute(rupees(30_000), rupees(5_000), rupees(26_000), 0, nov10)
        assertTrue(b.goalAtRisk)
        assertEquals(0L, b.todayAllowance) // never negative
    }

    @Test fun `last day of the month has no tomorrow`() {
        val b = DailyBudget.compute(rupees(30_000), rupees(5_000), 0, 0, LocalDate.of(2026, 11, 30))
        assertEquals(1, b.daysLeft)
        assertNull(b.tomorrowAllowance)
    }
}

class BudgetsTest {
    private val limit = rupees(1_000)

    @Test fun `warns at 80 percent and flags over at 100`() {
        assertEquals(BudgetLevel.OK, Budgets.levelOf(rupees(799), limit))
        assertEquals(BudgetLevel.WARNING, Budgets.levelOf(rupees(800), limit))
        assertEquals(BudgetLevel.WARNING, Budgets.levelOf(rupees(999), limit))
        assertEquals(BudgetLevel.OVER, Budgets.levelOf(rupees(1_000), limit))
        assertEquals(BudgetLevel.OK, Budgets.levelOf(rupees(5_000), 0)) // no budget set
    }

    @Test fun `alerts only when a threshold is newly crossed`() {
        val crossedWarning = Budgets.alertFor(food, limit, rupees(700), rupees(150))
        assertNotNull(crossedWarning)
        assertTrue(crossedWarning!!.contains("Food") && crossedWarning.contains("85%"))

        assertNull(Budgets.alertFor(food, limit, rupees(850), rupees(50))) // already warned
        assertNull(Budgets.alertFor(food, limit, rupees(100), rupees(100))) // still fine

        val crossedOver = Budgets.alertFor(food, limit, rupees(850), rupees(200))
        assertTrue(crossedOver!!.contains("over"))
        assertNull(Budgets.alertFor(food, limit, rupees(1_000), rupees(1))) // already over
    }

    @Test fun `statuses cover only categories that have a budget`() {
        val statuses = Budgets.statuses(mapOf(food.id to limit), listOf(food, shopping), mapOf(food.id to rupees(900)))
        assertEquals(1, statuses.size)
        assertEquals(BudgetLevel.WARNING, statuses.single().level)
    }
}

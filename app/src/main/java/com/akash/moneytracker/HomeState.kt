package com.akash.moneytracker

import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.RecurringEntity
import com.akash.moneytracker.data.Settings
import com.akash.moneytracker.data.SuggestionEntity
import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.BudgetLevel
import com.akash.moneytracker.domain.BudgetStatus
import com.akash.moneytracker.domain.Budgets
import com.akash.moneytracker.domain.DailyBudget
import com.akash.moneytracker.domain.Forecast
import com.akash.moneytracker.domain.Recurring
import com.akash.moneytracker.domain.StreakCalc
import com.akash.moneytracker.domain.Streaks
import com.akash.moneytracker.domain.epochRange
import com.akash.moneytracker.domain.monthKey
import java.time.LocalDate
import java.time.YearMonth

data class HomeState(
    val month: YearMonth,
    val today: LocalDate,
    val settings: Settings,
    /** Income used for the budget: the larger of expected and actually logged. */
    val income: Long,
    /** Everything spent this month, recurring bills included. */
    val spent: Long,
    /** True until the user has set a goal for this month and some income. */
    val needsSetup: Boolean,
    val budget: DailyBudget?,
    val recent: List<TransactionWithCategory>,
    /** Category budgets that are at 80% or over, worst first. */
    val budgetAlerts: List<BudgetStatus>,
    /** Monthly limit by category id, and what has been spent against it this month. */
    val budgetLimits: Map<Long, Long>,
    val categorySpend: Map<Long, Long>,
    val forecast: Forecast?,
    val streaks: Streaks,
    /** Transactions spotted in bank SMS, waiting to be confirmed. */
    val suggestions: List<SuggestionEntity>,
)

/** The planning data the home numbers depend on besides the transactions themselves. */
data class Planning(
    val budgets: Map<Long, Long>,
    val rules: List<RecurringEntity>,
    val categories: List<CategoryEntity>,
)

/** Pure calculation of everything on the home screen; shared by the app, the widget and the notification workers. */
fun computeHome(
    settings: Settings,
    all: List<TransactionWithCategory>,
    today: LocalDate,
    plan: Planning,
    suggestions: List<SuggestionEntity> = emptyList(),
): HomeState {
    val month = YearMonth.from(today)
    val range = month.epochRange()
    val monthTx = all.filter { it.tx.epochDay in range }
    val expenses = monthTx.filter { it.tx.type == TxType.EXPENSE }
    val spent = expenses.sumOf { it.tx.amountPaise }

    // Recurring bills are fixed: set aside up front, and kept out of the day-to-day spending.
    val daily = expenses.filter { it.tx.recurringId == null }
    val dailySpent = daily.sumOf { it.tx.amountPaise }
    val dailySpentToday = daily.filter { it.tx.epochDay == today.toEpochDay() }.sumOf { it.tx.amountPaise }
    val paidFixed = Recurring.paidExpenses(monthTx)
    val reserved = paidFixed + Recurring.upcomingExpenses(plan.rules, today)

    val loggedIncome = monthTx.filter { it.tx.type == TxType.INCOME }.sumOf { it.tx.amountPaise }
    val income = maxOf(settings.expectedIncomePaise, loggedIncome)
    val limitOn = settings.limitEnabled
    val needsSetup = settings.goalMonth != today.monthKey || income == 0L

    val categorySpend = expenses.groupBy { it.tx.categoryId }.mapValues { (_, rows) -> rows.sumOf { it.tx.amountPaise } }
    val alerts = Budgets.statuses(plan.budgets, plan.categories, categorySpend)
        .filter { it.level != BudgetLevel.OK }
        .sortedByDescending { it.fraction }

    return HomeState(
        month = month,
        today = today,
        settings = settings,
        income = income,
        spent = spent,
        needsSetup = needsSetup,
        budget = if (!limitOn || needsSetup) null else {
            DailyBudget.compute(income, settings.savingsGoalPaise, dailySpent, dailySpentToday, today, reserved)
        },
        recent = all.take(5),
        budgetAlerts = alerts,
        budgetLimits = plan.budgets,
        categorySpend = categorySpend,
        forecast = if (!limitOn || needsSetup) null else {
            Forecast.compute(income, settings.savingsGoalPaise, dailySpent, reserved, today)
        },
        streaks = StreakCalc.compute(all, today),
        suggestions = suggestions,
    )
}

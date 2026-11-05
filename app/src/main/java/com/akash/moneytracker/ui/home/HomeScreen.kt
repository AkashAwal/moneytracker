package com.akash.moneytracker.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.HomeState
import com.akash.moneytracker.data.TransactionEntity
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import com.akash.moneytracker.data.SuggestionEntity
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.Forecast
import com.akash.moneytracker.domain.timeLabel
import com.akash.moneytracker.domain.BudgetLevel
import com.akash.moneytracker.domain.BudgetStatus
import com.akash.moneytracker.domain.DailyBudget
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.dayLabel
import com.akash.moneytracker.domain.label
import com.akash.moneytracker.ui.components.AppCard
import com.akash.moneytracker.ui.components.EmptyState
import com.akash.moneytracker.ui.components.GoalDialog
import com.akash.moneytracker.ui.components.Hairline
import com.akash.moneytracker.ui.components.IncomeDialog
import com.akash.moneytracker.ui.components.SectionHeader
import com.akash.moneytracker.ui.components.TransactionRow
import com.akash.moneytracker.ui.theme.extra

@Composable
fun HomeScreen(
    state: HomeState?,
    onSaveGoal: (income: Long, goal: Long) -> Unit,
    onSeeAll: () -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onOpenBudgets: () -> Unit,
    onReviewSuggestion: (SuggestionEntity) -> Unit,
    onDismissSuggestion: (Long) -> Unit,
    /** Removes the daily limit from Home; it only comes back through [onShowLimit], on this screen. */
    onHideLimit: () -> Unit,
    onShowLimit: () -> Unit,
    /** Clears the savings goal and income so the limit can be set up again from scratch. */
    onResetLimit: () -> Unit,
    /** "I spent nothing today": keeps the logging streak going on a day with nothing to log. */
    onCheckIn: () -> Unit,
    /** Income alone, without a savings goal. */
    onSaveIncome: (Long) -> Unit,
) {
    if (state == null) return
    var showGoal by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    state.month.label(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val name = state.settings.name.orEmpty()
                Text(
                    if (name.isBlank()) "Hey there 👋" else "Hey, $name 👋",
                    style = MaterialTheme.typography.headlineLarge,
                )
            }
        }
        item {
            DailyLimitCard(
                state = state,
                onSetGoal = { showGoal = true },
                onSaveGoal = onSaveGoal,
                onHide = onHideLimit,
                onShow = onShowLimit,
                onReset = onResetLimit,
            )
        }
        if (state.suggestions.isNotEmpty()) {
            item { SuggestionsCard(state.suggestions, state.today, onReviewSuggestion, onDismissSuggestion) }
        }
        if (state.budgetAlerts.isNotEmpty()) {
            item { BudgetAlertsCard(state.budgetAlerts, onOpenBudgets) }
        }
        state.forecast?.let { forecast -> item { ForecastCard(forecast) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Tap to set what comes in each month; works with or without the daily limit.
                StatCard(
                    "Income",
                    if (state.income == 0L) "Tap to set" else Money.format(state.income),
                    Modifier.weight(1f),
                    onClick = { showIncome = true },
                )
                StatCard("Spent", Money.format(state.spent), Modifier.weight(1f))
                val saved = state.income - state.spent
                StatCard(
                    "Left",
                    Money.format(saved),
                    Modifier.weight(1f),
                    valueColor = if (saved < 0) MaterialTheme.extra.negative else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val streak = state.streaks
                StatCard(
                    "Logging streak",
                    if (streak.loggingStreak == 0) "Log today" else "🔥 ${streak.loggingStreak} ${if (streak.loggingStreak == 1) "day" else "days"}",
                    Modifier.weight(1f),
                )
                // Finished days this month on which no day-to-day spending was logged (today isn't counted until it ends).
                StatCard(
                    "No spend days",
                    "🌿 ${streak.noSpendDays} ${if (streak.noSpendDays == 1) "day" else "days"}",
                    Modifier.weight(1f),
                )
            }
        }
        // A day with nothing to log has no transaction to count, so it gets its own one-tap way to keep the streak.
        // Always on Home: it turns into a plain confirmation once today is counted.
        item { NoSpendTodayButton(counted = state.streaks.loggedToday, onClick = onCheckIn) }
        item {
            SectionHeader("Recent", action = if (state.recent.isNotEmpty()) "See all" else null, onAction = onSeeAll)
        }
        if (state.recent.isEmpty()) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    EmptyState("🪙", "Nothing logged yet", "Tap Add to record your first expense.")
                }
            }
        } else {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    state.recent.forEachIndexed { i, item ->
                        if (i > 0) Hairline(Modifier.padding(start = 72.dp))
                        TransactionRow(
                            item = item,
                            subtitle = dayLabel(item.tx.epochDay, state.today),
                            onClick = { onEdit(item.tx) },
                        )
                    }
                }
            }
        }
    }

    if (showIncome) {
        IncomeDialog(
            initialIncomePaise = state.settings.expectedIncomePaise,
            onDismiss = { showIncome = false },
            onSave = {
                onSaveIncome(it)
                showIncome = false
            },
        )
    }

    if (showGoal) {
        GoalDialog(
            monthLabel = state.month.label(),
            initialIncomePaise = state.settings.expectedIncomePaise,
            initialGoalPaise = state.settings.savingsGoalPaise,
            onDismiss = { showGoal = false },
            onSave = { income, goal ->
                onSaveGoal(income, goal)
                showGoal = false
            },
        )
    }
}

@Composable
private fun SuggestionsCard(
    items: List<SuggestionEntity>,
    today: java.time.LocalDate,
    onReview: (SuggestionEntity) -> Unit,
    onDismiss: (Long) -> Unit,
) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
            Text("From your messages", style = MaterialTheme.typography.titleMedium)
            Text(
                "Spotted in new bank texts. Add the ones you want to keep.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            items.take(3).forEach { s ->
                val isIncome = s.type == TxType.INCOME
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        Text(
                            "${dayLabel(s.epochDay, today)} · ${timeLabel(s.minuteOfDay)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        (if (isIncome) "+" else "-") + Money.format(s.amountPaise),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isIncome) MaterialTheme.extra.positive else MaterialTheme.colorScheme.onSurface,
                    )
                    TextButton(onClick = { onReview(s) }) { Text("Add") }
                    IconButton(onClick = { onDismiss(s.id) }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (items.size > 3) {
                Text(
                    "${items.size - 3} more waiting",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ForecastCard(f: Forecast) {
    val good = f.onTrack && f.projectedSavings >= 0
    val text = when {
        f.projectedSavings < 0 -> "At this pace you'll overspend by ${Money.format(-f.projectedSavings)} this month."
        f.onTrack -> "At this pace you'll save about ${Money.format(f.projectedSavings)}, " +
            "${Money.format(f.projectedSavings - f.goal)} above your goal."
        else -> "At this pace you'll save about ${Money.format(f.projectedSavings)}, " +
            "${Money.format(f.shortfall)} short of your ${Money.format(f.goal)} goal."
    }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Month-end forecast", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = if (good) MaterialTheme.extra.positive else MaterialTheme.extra.negative,
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    AppCard(modifier, onClick = onClick) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // One line each, so labels never wrap and every card in a row stays the same height.
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1, softWrap = false)
        }
    }
}

/**
 * Tapping it counts today as logged so the streak survives a quiet day. Once today is counted (by a transaction or
 * by this button) it stays on screen as a confirmation, so it is always easy to find.
 */
@Composable
private fun NoSpendTodayButton(counted: Boolean, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = if (counted) null else onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(if (counted) "✅" else "🌿", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (counted) "Today is counted" else "Spent nothing today",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (counted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (counted) "Your logging streak is safe for today" else "Tap to keep your logging streak going",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DailyLimitCard(
    state: HomeState,
    onSetGoal: () -> Unit,
    onSaveGoal: (income: Long, goal: Long) -> Unit,
    onHide: () -> Unit,
    onShow: () -> Unit,
    onReset: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }
    // Optional feature: not everyone wants a savings target or a red warning on their home screen.
    if (state.settings.dailyLimitHidden) {
        ShowLimitPrompt(onShow)
        return
    }
    val budget = state.budget
    if (budget == null) {
        SetupCard(state, onSaveGoal, onHide)
        return
    }

    val over = budget.overToday || budget.goalAtRisk
    val container = if (over) MaterialTheme.colorScheme.errorContainerSoft() else MaterialTheme.colorScheme.tertiaryContainer
    val accent = if (over) MaterialTheme.extra.negative else MaterialTheme.colorScheme.onTertiaryContainer

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = container,
        borderColor = Color.Transparent,
        onClick = onSetGoal,
    ) {
        // Bottom padding is small because the Reset button below supplies its own height.
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (budget.overToday) "Over today's limit" else "You can spend today",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.weight(1f).padding(top = 20.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (budget.overToday) Money.format(budget.spentToday - budget.todayAllowance) else Money.format(budget.leftToday),
                style = MaterialTheme.typography.displayMedium,
                color = if (over) accent else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            val fraction = if (budget.todayAllowance > 0) {
                (budget.spentToday.toFloat() / budget.todayAllowance).coerceIn(0f, 1f)
            } else if (budget.spentToday > 0) 1f else 0f
            val animated by animateFloatAsState(fraction, label = "limit-progress")
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(MaterialTheme.shapes.extraSmall),
                color = if (over) MaterialTheme.extra.negative else MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                strokeCap = StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Spent ${Money.format(budget.spentToday)} of ${Money.format(budget.todayAllowance)} today",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val goalText = Money.format(state.settings.savingsGoalPaise)
            Text(
                if (budget.goalAtRisk) {
                    "You've spent more than your budget, so the $goalText goal is at risk."
                } else if (budget.overToday && budget.tomorrowAllowance != null) {
                    "Tomorrow's limit becomes ${Money.format(budget.tomorrowAllowance)} so you still save $goalText. " +
                        "${budget.daysLeft - 1} ${if (budget.daysLeft - 1 == 1) "day" else "days"} left after today."
                } else {
                    "To save ${Money.format(state.settings.savingsGoalPaise)} this month, keep daily spending under " +
                        "${Money.format(budget.todayAllowance)}. ${budget.daysLeft} ${if (budget.daysLeft == 1) "day" else "days"} left."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (budget.upcomingFixed > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${Money.format(budget.upcomingFixed)} is set aside for recurring bills this month.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                // Turns the limit off; Income, Spent and Left stay. A prompt on Home turns it back on.
                TextButton(onClick = onHide, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Hide limit", style = MaterialTheme.typography.labelLarge, color = accent)
                }
                TextButton(onClick = { confirmReset = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Reset", style = MaterialTheme.typography.labelLarge, color = accent)
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Reset your daily limit?") },
            text = { Text("This clears your savings goal and expected income so you can set new ones. Your transactions aren't touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun BudgetAlertsCard(alerts: List<BudgetStatus>, onOpen: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Budgets", style = MaterialTheme.typography.titleMedium)
                Text("Manage", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            alerts.forEach { s ->
                val over = s.level == BudgetLevel.OVER
                val color = if (over) MaterialTheme.extra.negative else MaterialTheme.colorScheme.tertiary
                Column {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${s.category.emoji}  ${s.category.name}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (over) "Over by ${Money.format(s.spent - s.limit)}" else "${(s.fraction * 100).toInt()}% used",
                            style = MaterialTheme.typography.labelLarge,
                            color = color,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { s.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        strokeCap = StrokeCap.Round,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${Money.format(s.spent)} of ${Money.format(s.limit)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** What Home shows in place of the daily limit once it has been removed. */
@Composable
private fun ShowLimitPrompt(onShow: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), onClick = onShow) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                "＋  Track a daily spending limit",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Optional. Set a savings goal and see how much you can spend each day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SetupCard(state: HomeState, onSaveGoal: (income: Long, goal: Long) -> Unit, onHide: () -> Unit) {
    var goal by remember { mutableStateOf(Money.toInput(state.settings.savingsGoalPaise).takeIf { state.settings.savingsGoalPaise > 0 } ?: "") }
    var income by remember { mutableStateOf("") }
    // Income is only asked once; after that it carries over from month to month.
    val needsIncome = state.income == 0L
    val goalPaise = Money.parseToPaise(goal) ?: 0L
    val incomePaise = if (needsIncome) Money.parseToPaise(income) ?: 0L else state.settings.expectedIncomePaise
    val effectiveIncome = if (needsIncome) incomePaise else state.income
    val valid = goalPaise > 0 && effectiveIncome > goalPaise

    val field = Modifier.fillMaxWidth()
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        borderColor = Color.Transparent,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Savings goal this month",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(top = 20.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            if (needsIncome) {
                OutlinedTextField(
                    value = income,
                    onValueChange = { income = Money.sanitize(it) },
                    modifier = field,
                    label = { Text("Monthly income") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
            }
            OutlinedTextField(
                value = goal,
                onValueChange = { goal = Money.sanitize(it) },
                modifier = field,
                label = { Text("Amount to save") },
                prefix = { Text("₹") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                isError = goalPaise > 0 && effectiveIncome in 1..goalPaise,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onSaveGoal(incomePaise, goalPaise) },
                enabled = valid,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Set goal", style = MaterialTheme.typography.titleSmall) }
            // The limit is optional: skip it and Home still shows Income, Spent and Left.
            TextButton(onClick = onHide, modifier = Modifier.fillMaxWidth()) {
                Text("Not now", color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }
}

@Composable
private fun androidx.compose.material3.ColorScheme.errorContainerSoft(): Color = error.copy(alpha = 0.10f)

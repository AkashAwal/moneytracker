package com.akash.moneytracker.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
    /** "I spent nothing today": keeps the logging streak going on a day with nothing to log. */
    onCheckIn: () -> Unit,
) {
    if (state == null) return
    var showGoal by remember { mutableStateOf(false) }

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
                StatCard("Income", Money.format(state.income), Modifier.weight(1f))
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
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val streak = state.streaks
                StatCard(
                    "Logging streak",
                    if (streak.loggingStreak == 0) "Log today" else "🔥 ${streak.loggingStreak} ${if (streak.loggingStreak == 1) "day" else "days"}",
                    Modifier.weight(1f).fillMaxHeight(),
                    // A day with no spending has nothing to log, so it gets its own way to keep the streak.
                    footer = if (streak.loggedToday) null else {
                        {
                            TextButton(
                                onClick = onCheckIn,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(28.dp),
                            ) { Text("Spent nothing today", style = MaterialTheme.typography.labelMedium) }
                        }
                    },
                )
                // Finished days this month on which no day-to-day spending was logged (today isn't counted until it ends).
                StatCard(
                    "No-spend this month",
                    "🌿 ${streak.noSpendDays} ${if (streak.noSpendDays == 1) "day" else "days"}",
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
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
    footer: (@Composable () -> Unit)? = null,
) {
    AppCard(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor, maxLines = 1)
            footer?.invoke()
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
) {
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
        Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (budget.overToday) "Over today's limit" else "You can spend today",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.weight(1f).padding(top = 20.dp),
                )
                HideButton(onHide, tint = accent)
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
        }
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

/** The cross on the daily limit: removes it from Home until the user asks for it again on this screen. */
@Composable
private fun HideButton(onClick: () -> Unit, tint: Color) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp).offset(x = 12.dp)) {
        Icon(
            Icons.Outlined.Close,
            contentDescription = "Remove daily limit from Home",
            tint = tint.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp),
        )
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
                HideButton(onHide, tint = MaterialTheme.colorScheme.onTertiaryContainer)
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
        }
    }
}

@Composable
private fun androidx.compose.material3.ColorScheme.errorContainerSoft(): Color = error.copy(alpha = 0.10f)

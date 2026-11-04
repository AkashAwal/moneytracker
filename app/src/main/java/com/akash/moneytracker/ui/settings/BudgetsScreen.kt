package com.akash.moneytracker.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.BudgetLevel
import com.akash.moneytracker.domain.Budgets
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.ui.components.AppCard
import com.akash.moneytracker.ui.components.CategoryBadge
import com.akash.moneytracker.ui.components.EmptyState
import com.akash.moneytracker.ui.components.Hairline
import com.akash.moneytracker.ui.components.ScreenHeader
import com.akash.moneytracker.ui.theme.extra

@Composable
fun BudgetsScreen(
    categories: List<CategoryEntity>,
    limits: Map<Long, Long>,
    spend: Map<Long, Long>,
    onSetBudget: (categoryId: Long, limitPaise: Long) -> Unit,
    onBack: () -> Unit,
) {
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val expenseCategories = categories.filter { it.type == TxType.EXPENSE }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Budgets", onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Set a monthly limit for a category. You'll get a warning at 80% and again when you go over.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                if (expenseCategories.isEmpty()) {
                    AppCard(Modifier.fillMaxWidth()) { EmptyState("🎯", "No categories yet", "Add an expense category first.") }
                } else {
                    AppCard(Modifier.fillMaxWidth()) {
                        expenseCategories.forEachIndexed { i, c ->
                            if (i > 0) Hairline(Modifier.padding(start = 72.dp))
                            BudgetRow(c, limits[c.id], spend[c.id] ?: 0L) { editing = c }
                        }
                    }
                }
            }
        }
    }

    editing?.let { category ->
        BudgetDialog(
            category = category,
            currentLimit = limits[category.id],
            onSave = { onSetBudget(category.id, it); editing = null },
            onRemove = { onSetBudget(category.id, 0); editing = null },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun BudgetRow(category: CategoryEntity, limit: Long?, spent: Long, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(category.emoji, Color(category.color))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(category.name, style = MaterialTheme.typography.titleSmall)
            if (limit == null) {
                Text("No budget", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val level = Budgets.levelOf(spent, limit)
                val color = when (level) {
                    BudgetLevel.OK -> MaterialTheme.colorScheme.primary
                    BudgetLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                    BudgetLevel.OVER -> MaterialTheme.extra.negative
                }
                val progress by animateFloatAsState((spent.toFloat() / limit).coerceIn(0f, 1f), label = "budget")
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.extraSmall),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${Money.format(spent)} of ${Money.format(limit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (level == BudgetLevel.OVER) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            if (limit == null) "Set" else "Edit",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun BudgetDialog(
    category: CategoryEntity,
    currentLimit: Long?,
    onSave: (Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(currentLimit?.let { Money.toInput(it) } ?: "") }
    val paise = Money.parseToPaise(amount) ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("${category.emoji}  ${category.name} budget") },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = Money.sanitize(it) },
                    label = { Text("Monthly limit") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (currentLimit != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove budget", color = MaterialTheme.extra.negative)
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = paise > 0, onClick = { onSave(paise) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

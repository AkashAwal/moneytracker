package com.akash.moneytracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.RecurringEntity
import com.akash.moneytracker.data.RecurringWithCategory
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.Recurring
import com.akash.moneytracker.ui.add.AmountInput
import com.akash.moneytracker.ui.add.CategoryChip
import com.akash.moneytracker.ui.components.AppCard
import com.akash.moneytracker.ui.components.CategoryBadge
import com.akash.moneytracker.ui.components.EmptyState
import com.akash.moneytracker.ui.components.Hairline
import com.akash.moneytracker.ui.components.ScreenHeader
import com.akash.moneytracker.ui.theme.extra

/** Opens the sheet for a new rule (edit == null) or to change an existing one. */
private data class RuleRequest(val edit: RecurringEntity?)

@Composable
fun RecurringScreen(
    rules: List<RecurringWithCategory>,
    categories: List<CategoryEntity>,
    onSave: (RecurringEntity) -> Unit,
    onDelete: (RecurringEntity) -> Unit,
    onBack: () -> Unit,
) {
    var sheet by remember { mutableStateOf<RuleRequest?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader("Recurring", onBack)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Rent, subscriptions, salary: add them once and they're logged on the right day every month. " +
                        "Upcoming bills are set aside before your daily limit is worked out.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(onClick = { sheet = RuleRequest(null) }, shape = CircleShape) { Text("＋  Add recurring") }
            }
            if (rules.isEmpty()) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        EmptyState("🔁", "Nothing repeating yet", "Things like rent or Netflix belong here.")
                    }
                }
            } else {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        rules.forEachIndexed { i, item ->
                            if (i > 0) Hairline(Modifier.padding(start = 72.dp))
                            RuleRow(item) { sheet = RuleRequest(item.rule) }
                        }
                    }
                }
            }
        }
    }

    sheet?.let { request ->
        RecurringSheet(
            categories = categories,
            editing = request.edit,
            onSave = { onSave(it); sheet = null },
            onDelete = { onDelete(it); sheet = null },
            onDismiss = { sheet = null },
        )
    }
}

@Composable
private fun RuleRow(item: RecurringWithCategory, onClick: () -> Unit) {
    val rule = item.rule
    val isIncome = rule.type == TxType.INCOME
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(item.category.emoji, Color(item.category.color))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(rule.note.ifBlank { item.category.name }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                Recurring.describe(rule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            (if (isIncome) "+" else "-") + Money.format(rule.amountPaise),
            style = MaterialTheme.typography.titleSmall,
            color = if (isIncome) MaterialTheme.extra.positive else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RecurringSheet(
    categories: List<CategoryEntity>,
    editing: RecurringEntity?,
    onSave: (RecurringEntity) -> Unit,
    onDelete: (RecurringEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by remember { mutableStateOf(editing?.type ?: TxType.EXPENSE) }
    var amount by remember { mutableStateOf(editing?.let { Money.toInput(it.amountPaise) } ?: "") }
    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var day by remember { mutableStateOf(editing?.dayOfMonth?.toString() ?: "") }
    var note by remember { mutableStateOf(editing?.note ?: "") }

    val amountPaise = Money.parseToPaise(amount) ?: 0L
    val dayNumber = day.toIntOrNull()
    val canSave = amountPaise > 0 && categoryId != null && dayNumber != null && dayNumber in 1..31 &&
        categories.any { it.id == categoryId && it.type == type }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
    ) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    TxType.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = {
                                type = t
                                if (categories.none { it.id == categoryId && it.type == t }) categoryId = null
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, TxType.entries.size),
                        ) { Text(if (t == TxType.EXPENSE) "Expense" else "Income") }
                    }
                }
                Spacer(Modifier.height(24.dp))
                AmountInput(amount, { amount = Money.sanitize(it) }, remember { FocusRequester() })
                Spacer(Modifier.height(24.dp))

                Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { it.type == type }.forEach { c ->
                        CategoryChip(c, selected = c.id == categoryId, onClick = { categoryId = c.id })
                    }
                }

                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("Day of the month") },
                    supportingText = { Text("Months that are shorter use their last day.") },
                    isError = day.isNotEmpty() && (dayNumber == null || dayNumber !in 1..31),
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    placeholder = { Text("Name, e.g. Rent (optional)") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (editing != null) {
                    TextButton(onClick = { onDelete(editing) }) {
                        Text("Delete this recurring entry", color = MaterialTheme.extra.negative)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onSave(
                        RecurringEntity(
                            id = editing?.id ?: 0,
                            amountPaise = amountPaise,
                            type = type,
                            categoryId = categoryId!!,
                            note = note.trim(),
                            dayOfMonth = dayNumber!!,
                            minuteOfDay = editing?.minuteOfDay ?: (9 * 60),
                            lastGeneratedMonth = editing?.lastGeneratedMonth ?: 0,
                        )
                    )
                },
                enabled = canSave,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (editing == null) "Add" else "Save changes", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

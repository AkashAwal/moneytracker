package com.akash.moneytracker.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.ActivityFilter
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.ui.add.CategoryChip
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** Filters apply live as they're changed; the sheet is just a way to set them. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterSheet(
    filter: ActivityFilter,
    categories: List<CategoryEntity>,
    knownTags: List<String>,
    onChange: (ActivityFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pickingFrom by remember { mutableStateOf(false) }
    var pickingTo by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Filters", style = MaterialTheme.typography.titleLarge)
                    TextButton(
                        enabled = filter.activeFilters > 0,
                        onClick = { onChange(ActivityFilter(query = filter.query)) },
                    ) { Text("Reset") }
                }

                Label("Type")
                val options = listOf<TxType?>(null, TxType.EXPENSE, TxType.INCOME)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    options.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = filter.type == t,
                            onClick = {
                                // A category of the other type can no longer match, so drop it.
                                val keep = categories.firstOrNull { it.id == filter.categoryId }?.takeIf { t == null || it.type == t }
                                onChange(filter.copy(type = t, categoryId = keep?.id))
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(when (t) { null -> "All"; TxType.EXPENSE -> "Expense"; TxType.INCOME -> "Income" }) }
                    }
                }

                Label("Category")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.filter { filter.type == null || it.type == filter.type }.forEach { c ->
                        CategoryChip(
                            c,
                            selected = c.id == filter.categoryId,
                            onClick = { onChange(filter.copy(categoryId = if (c.id == filter.categoryId) null else c.id)) },
                        )
                    }
                }

                if (knownTags.isNotEmpty()) {
                    Label("Tag")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        knownTags.forEach { t ->
                            Surface(
                                onClick = { onChange(filter.copy(tag = if (t == filter.tag) null else t)) },
                                shape = CircleShape,
                                color = if (t == filter.tag) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ) {
                                Text("#$t", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                            }
                        }
                    }
                }

                Label("Date")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateChip("From", filter.fromDay) { pickingFrom = true }
                    DateChip("To", filter.toDay) { pickingTo = true }
                }
                if (filter.fromDay != null || filter.toDay != null) {
                    TextButton(onClick = { onChange(filter.copy(fromDay = null, toDay = null)) }) { Text("Clear dates") }
                }

                Label("Amount")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AmountField("Min", filter.minPaise, Modifier.weight(1f)) { onChange(filter.copy(minPaise = it)) }
                    AmountField("Max", filter.maxPaise, Modifier.weight(1f)) { onChange(filter.copy(maxPaise = it)) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onDismiss, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Done", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (pickingFrom) {
        DayPicker(filter.fromDay, onDismiss = { pickingFrom = false }) { onChange(filter.copy(fromDay = it)); pickingFrom = false }
    }
    if (pickingTo) {
        DayPicker(filter.toDay, onDismiss = { pickingTo = false }) { onChange(filter.copy(toDay = it)); pickingTo = false }
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DateChip(label: String, epochDay: Long?, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(
            "$label: " + (epochDay?.let { LocalDate.ofEpochDay(it).format(dayFormat) } ?: "any"),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun AmountField(label: String, paise: Long?, modifier: Modifier, onChange: (Long?) -> Unit) {
    // Keep the raw text locally so typing "1." isn't rewritten while the user is mid-entry.
    var text by remember { mutableStateOf(paise?.let { Money.toInput(it) } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = Money.sanitize(it)
            onChange(Money.parseToPaise(text))
        },
        label = { Text(label) },
        prefix = { Text("₹") },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(initial: Long?, onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = (initial ?: LocalDate.now().toEpochDay()) * 86_400_000L)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()) }
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}

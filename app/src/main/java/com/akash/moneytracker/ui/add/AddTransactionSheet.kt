package com.akash.moneytracker.ui.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.SuggestionEntity
import com.akash.moneytracker.data.TransactionEntity
import com.akash.moneytracker.domain.Tags
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.dayLabel
import com.akash.moneytracker.domain.nowMinuteOfDay
import com.akash.moneytracker.domain.timeLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTransactionSheet(
    categories: List<CategoryEntity>,
    editing: TransactionEntity?,
    suggestion: SuggestionEntity?,
    knownTags: List<String>,
    onSave: (TransactionEntity) -> Unit,
    onCreateCategory: (CategoryEntity, onCreated: (Long) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today = remember { LocalDate.now() }

    // A bank-SMS suggestion pre-fills everything except the category and tags.
    var type by remember { mutableStateOf(editing?.type ?: suggestion?.type ?: TxType.EXPENSE) }
    var amount by remember {
        mutableStateOf((editing?.amountPaise ?: suggestion?.amountPaise)?.let { Money.toInput(it) } ?: "")
    }
    var categoryId by remember { mutableStateOf(editing?.categoryId) }
    var note by remember { mutableStateOf(editing?.note ?: suggestion?.title ?: "") }
    var tagsText by remember { mutableStateOf(editing?.let { Tags.toInput(it.tags) } ?: "") }
    var day by remember {
        mutableStateOf((editing?.epochDay ?: suggestion?.epochDay)?.let(LocalDate::ofEpochDay) ?: today)
    }
    var minuteOfDay by remember { mutableStateOf(editing?.minuteOfDay ?: suggestion?.minuteOfDay ?: nowMinuteOfDay()) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var creatingCategory by remember { mutableStateOf(false) }

    val visibleCategories = categories.filter { it.type == type }
    val amountPaise = Money.parseToPaise(amount) ?: 0L
    val canSave = amountPaise > 0 && categoryId != null && categories.any { it.id == categoryId && it.type == type }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (editing == null) focus.requestFocus() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
    ) {
        // The form scrolls; the Add button stays pinned above the keyboard.
        Column(
            modifier = Modifier
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
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
            AmountInput(amount, { amount = Money.sanitize(it) }, focus)
            Spacer(Modifier.height(24.dp))

            Text("Category", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visibleCategories.forEach { c ->
                    CategoryChip(c, selected = c.id == categoryId, onClick = { categoryId = c.id })
                }
                NewCategoryChip(onClick = { creatingCategory = true })
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("📅  " + dayLabel(day.toEpochDay(), today)) { pickingDate = true }
                PillButton("🕒  " + timeLabel(minuteOfDay)) { pickingTime = true }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(80) },
                placeholder = { Text("Note (optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it.take(60) },
                placeholder = { Text("Tags, e.g. #goa #work (optional)") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )
            val chosen = Tags.parse(tagsText)
            val suggestions = knownTags.filter { it !in chosen }.take(8)
            if (suggestions.isNotEmpty() && chosen.size < Tags.MAX_TAGS) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { tag ->
                        Surface(
                            onClick = { tagsText = (Tags.encode(chosen).replace(",", " ") + " #$tag").trim() },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                "#$tag",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }

        }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    onSave(
                        TransactionEntity(
                            id = editing?.id ?: 0,
                            amountPaise = amountPaise,
                            type = type,
                            categoryId = categoryId!!,
                            note = note.trim(),
                            epochDay = day.toEpochDay(),
                            minuteOfDay = minuteOfDay,
                            createdAt = editing?.createdAt ?: System.currentTimeMillis(),
                            recurringId = editing?.recurringId, // keep a rule-made bill counted as a fixed bill
                            tags = Tags.encode(Tags.parse(tagsText)),
                        )
                    )
                },
                enabled = canSave,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (editing == null) "Add" else "Save changes", style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (creatingCategory) {
        NewCategoryDialog(
            type = type,
            existingNames = categories.filter { it.type == type }.map { it.name.lowercase() }.toSet(),
            nextSortOrder = (categories.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            onCreate = { category ->
                creatingCategory = false
                onCreateCategory(category) { id -> categoryId = id }
            },
            onDismiss = { creatingCategory = false },
        )
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = day.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }

    if (pickingTime) {
        val timeState = rememberTimePickerState(
            initialHour = minuteOfDay / 60,
            initialMinute = minuteOfDay % 60,
            is24Hour = false,
        )
        Dialog(onDismissRequest = { pickingTime = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timeState)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { pickingTime = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            minuteOfDay = timeState.hour * 60 + timeState.minute
                            pickingTime = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun AmountInput(value: String, onChange: (String) -> Unit, focus: FocusRequester) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("₹", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.displayMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(IntrinsicSize.Min).widthIn(min = 28.dp).focusRequester(focus),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("0", style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.outline)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
internal fun CategoryChip(category: CategoryEntity, selected: Boolean, onClick: () -> Unit) {
    val color = Color(category.color)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.5.dp, if (selected) color else Color.Transparent),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(category.emoji)
            Spacer(Modifier.width(6.dp))
            Text(category.name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun NewCategoryChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
    ) {
        Text(
            "＋  New",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

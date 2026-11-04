package com.akash.moneytracker.ui.add

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.ui.components.CategoryBadge
import com.akash.moneytracker.ui.theme.ChartPalette

private val EmojiChoices = listOf(
    "🍔", "🍕", "☕", "🍺", "🛒", "🚌", "🚗", "⛽", "🏠", "💡",
    "📱", "🎬", "🎮", "🎵", "👕", "🛍️", "💊", "🏥", "📚", "🎓",
    "✈️", "🏖️", "🐶", "💇", "🎁", "💍", "🏋️", "🔧", "🧾", "📦",
    "💼", "💰", "📈", "🏦", "🏆", "🧸",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCategoryDialog(
    type: TxType,
    existingNames: Set<String>,
    nextSortOrder: Int,
    onCreate: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf(if (type == TxType.INCOME) "💰" else "🍔") }
    var color by remember { mutableStateOf(ChartPalette[nextSortOrder % ChartPalette.size]) }

    val trimmed = name.trim()
    val duplicate = trimmed.lowercase() in existingNames
    val canCreate = trimmed.isNotEmpty() && !duplicate

    AlertDialog(
        onDismissRequest = onDismiss,
        // Edge-to-edge dialog window + imePadding shrinks the dialog above the keyboard,
        // so Create stays visible and only the icon grid scrolls.
        properties = DialogProperties(decorFitsSystemWindows = false),
        modifier = Modifier.imePadding().statusBarsPadding(),
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (type == TxType.INCOME) "New income category" else "New expense category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(emoji, color, size = 52.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        placeholder = { Text("Category name") },
                        singleLine = true,
                        isError = duplicate,
                        supportingText = if (duplicate) {
                            { Text("You already have this one") }
                        } else null,
                        shape = MaterialTheme.shapes.small,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        // Closing the keyboard reveals the Create button underneath it.
                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    EmojiChoices.forEach { e ->
                        val selected = e == emoji
                        Surface(
                            onClick = { emoji = e },
                            shape = CircleShape,
                            color = if (selected) color.copy(alpha = 0.18f) else Color.Transparent,
                            border = BorderStroke(1.5.dp, if (selected) color else Color.Transparent),
                        ) {
                            Text(e, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text("Color", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChartPalette.forEach { c ->
                        Surface(
                            onClick = { color = c },
                            shape = CircleShape,
                            color = c,
                            border = BorderStroke(3.dp, if (c == color) MaterialTheme.colorScheme.onSurface else Color.Transparent),
                            modifier = Modifier.size(32.dp),
                        ) {}
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = {
                    onCreate(
                        CategoryEntity(
                            name = trimmed,
                            emoji = emoji,
                            color = color.toArgb().toLong() and 0xFFFFFFFFL,
                            type = type,
                            sortOrder = nextSortOrder,
                        )
                    )
                },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

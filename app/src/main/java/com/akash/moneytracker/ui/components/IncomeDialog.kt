package com.akash.moneytracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.domain.Money

/** Asks only for the monthly income. No savings goal or daily limit is involved. */
@Composable
fun IncomeDialog(
    initialIncomePaise: Long,
    onDismiss: () -> Unit,
    onSave: (incomePaise: Long) -> Unit,
) {
    var income by remember { mutableStateOf(if (initialIncomePaise > 0) Money.toInput(initialIncomePaise) else "") }
    val paise = Money.parseToPaise(income)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Monthly income") },
        text = {
            Column {
                Text(
                    "What you expect to receive each month. Home uses it to show how much of it is left.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = income,
                    onValueChange = { income = Money.sanitize(it) },
                    label = { Text("Income per month") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = paise != null && paise >= 0, onClick = { onSave(paise!!) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

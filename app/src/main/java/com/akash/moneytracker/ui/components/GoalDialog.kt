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

/** Asks for this month's expected income and how much the user wants to save. */
@Composable
fun GoalDialog(
    monthLabel: String,
    initialIncomePaise: Long,
    initialGoalPaise: Long,
    onDismiss: () -> Unit,
    onSave: (incomePaise: Long, goalPaise: Long) -> Unit,
) {
    var income by remember { mutableStateOf(if (initialIncomePaise > 0) Money.toInput(initialIncomePaise) else "") }
    var goal by remember { mutableStateOf(if (initialGoalPaise > 0) Money.toInput(initialGoalPaise) else "") }
    val incomePaise = Money.parseToPaise(income)
    val goalPaise = Money.parseToPaise(goal) ?: 0L
    val valid = incomePaise != null && incomePaise > 0 && goalPaise < incomePaise

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Plan for $monthLabel") },
        text = {
            Column {
                Text(
                    "Tell us what comes in and how much you want to keep. We'll work out a daily spending limit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = income,
                    onValueChange = { income = Money.sanitize(it) },
                    label = { Text("Income this month") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = Money.sanitize(it) },
                    label = { Text("Save by month end") },
                    prefix = { Text("₹") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    isError = incomePaise != null && goalPaise >= incomePaise,
                    supportingText = if (incomePaise != null && goalPaise >= incomePaise) {
                        { Text("Goal must be less than income") }
                    } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSave(incomePaise!!, goalPaise) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

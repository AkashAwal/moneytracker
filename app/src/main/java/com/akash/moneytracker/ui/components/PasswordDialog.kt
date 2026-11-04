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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.data.BackupCrypto

/**
 * Asks for a backup password. When [confirm] is true (creating a backup) it asks twice, because a typo would
 * make the backup unreadable and there is no way to recover a forgotten password.
 */
@Composable
fun PasswordDialog(
    title: String,
    message: String,
    confirm: Boolean,
    submitLabel: String,
    errorText: String?,
    busy: Boolean,
    onSubmit: (CharArray) -> Unit,
    onDismiss: () -> Unit,
    skipLabel: String? = null,
    onSkip: () -> Unit = {},
) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val longEnough = password.length >= BackupCrypto.MIN_PASSWORD_LENGTH
    val matches = !confirm || password == again
    val valid = longEnough && matches && !busy

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title) },
        text = {
            Column {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it.take(128) },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    isError = errorText != null,
                    supportingText = {
                        Text(errorText ?: if (confirm) "At least ${BackupCrypto.MIN_PASSWORD_LENGTH} characters" else "")
                    },
                    shape = MaterialTheme.shapes.small,
                    enabled = !busy,
                )
                if (confirm) {
                    OutlinedTextField(
                        value = again,
                        onValueChange = { again = it.take(128) },
                        label = { Text("Repeat password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        isError = again.isNotEmpty() && !matches,
                        supportingText = { if (again.isNotEmpty() && !matches) Text("Passwords don't match") },
                        shape = MaterialTheme.shapes.small,
                        enabled = !busy,
                    )
                }
                if (skipLabel != null) {
                    // Kept apart from the main buttons, and in the warning color, so it isn't chosen by accident.
                    TextButton(enabled = !busy, onClick = onSkip) { Text(skipLabel, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onSubmit(password.toCharArray()) }) {
                Text(if (busy) "Working…" else submitLabel)
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

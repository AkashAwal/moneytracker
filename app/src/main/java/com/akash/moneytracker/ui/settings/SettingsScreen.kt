package com.akash.moneytracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.HomeState
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.label
import com.akash.moneytracker.domain.timeLabel
import com.akash.moneytracker.ui.components.AppCard
import com.akash.moneytracker.ui.components.GoalDialog
import com.akash.moneytracker.ui.components.Hairline
import com.akash.moneytracker.ui.components.NameDialog
import com.akash.moneytracker.ui.components.TimeDialog

@Composable
fun SettingsScreen(
    state: HomeState?,
    budgetCount: Int,
    recurringCount: Int,
    onSaveGoal: (income: Long, goal: Long) -> Unit,
    onSaveName: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderTime: (Int) -> Unit,
    onToggleWeekly: (Boolean) -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onPasteBankText: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExportCsv: () -> Unit,
) {
    if (state == null) return
    val s = state.settings
    var showGoal by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge)

        Section("Profile")
        AppCard(Modifier.fillMaxWidth()) {
            SettingRow("Name", s.name?.ifBlank { null } ?: "Not set") { showName = true }
        }

        // The daily limit is optional; it is switched on and off from the home screen only.
        if (s.limitEnabled) {
            Section("This month")
            AppCard(Modifier.fillMaxWidth()) {
                SettingRow("Expected income", if (s.expectedIncomePaise > 0) Money.format(s.expectedIncomePaise) else "Not set") { showGoal = true }
                Hairline(Modifier.padding(start = 16.dp))
                SettingRow("Savings goal", if (s.savingsGoalPaise > 0) Money.format(s.savingsGoalPaise) else "Not set") { showGoal = true }
            }
        }

        Section("Plan")
        AppCard(Modifier.fillMaxWidth()) {
            SettingRow("Category budgets", if (budgetCount == 0) "None set" else "$budgetCount set", onOpenBudgets)
            Hairline(Modifier.padding(start = 16.dp))
            SettingRow("Recurring transactions", if (recurringCount == 0) "None" else "$recurringCount active", onOpenRecurring)
        }

        Section("Reminders")
        AppCard(Modifier.fillMaxWidth()) {
            SwitchRow("Daily reminder", "A nudge to log your spending, skipped if you already have", s.reminderEnabled, onToggleReminder)
            if (s.reminderEnabled) {
                Hairline(Modifier.padding(start = 16.dp))
                SettingRow("Reminder time", timeLabel(s.reminderMinute)) { showTime = true }
            }
            Hairline(Modifier.padding(start = 16.dp))
            SwitchRow("Weekly summary", "Sundays at 8 PM: how the week went", s.weeklySummaryEnabled, onToggleWeekly)
        }

        Section("Privacy and automation")
        AppCard(Modifier.fillMaxWidth()) {
            SwitchRow("App lock", "Ask for your fingerprint, face or phone PIN", s.lockEnabled, onToggleLock)
            Hairline(Modifier.padding(start = 16.dp))
            SettingRow("Add from a bank text", "Paste copied text", onPasteBankText)
        }

        Section("Your data")
        AppCard(Modifier.fillMaxWidth()) {
            SettingRow("Back up everything", "Password-protected", onBackup)
            Hairline(Modifier.padding(start = 16.dp))
            SettingRow("Restore from backup", "Replaces current data", onRestore)
            Hairline(Modifier.padding(start = 16.dp))
            SettingRow("Export transactions", "CSV spreadsheet", onExportCsv)
        }
        Text(
            "Everything is stored on this phone only. Back up regularly, especially before changing phones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Section("About")
        AppCard(Modifier.fillMaxWidth()) {
            val context = LocalContext.current
            val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "" }
            SettingRow("Version", version, onClick = null)
        }
    }

    if (showName) {
        NameDialog(
            initialName = s.name.orEmpty(),
            onSave = {
                onSaveName(it)
                showName = false
            },
            onSkip = { showName = false },
        )
    }

    if (showTime) {
        TimeDialog(
            initialMinute = s.reminderMinute,
            onDismiss = { showTime = false },
            onPick = {
                onReminderTime(it)
                showTime = false
            },
        )
    }

    if (showGoal) {
        GoalDialog(
            monthLabel = state.month.label(),
            initialIncomePaise = s.expectedIncomePaise,
            initialGoalPaise = s.savingsGoalPaise,
            onDismiss = { showGoal = false },
            onSave = { income, goal ->
                onSaveGoal(income, goal)
                showGoal = false
            },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

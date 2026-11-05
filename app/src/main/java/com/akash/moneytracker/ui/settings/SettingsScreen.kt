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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.HomeState
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.label
import com.akash.moneytracker.domain.timeLabel
import com.akash.moneytracker.ui.components.AppCard
import com.akash.moneytracker.ui.components.GoalDialog
import com.akash.moneytracker.ui.components.Hairline
import com.akash.moneytracker.ui.components.IncomeDialog
import com.akash.moneytracker.ui.components.NameDialog
import com.akash.moneytracker.ui.components.TimeDialog

@Composable
fun SettingsScreen(
    state: HomeState?,
    budgetCount: Int,
    recurringCount: Int,
    onSaveGoal: (income: Long, goal: Long) -> Unit,
    onSaveIncome: (Long) -> Unit,
    onSaveName: (String) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenRecurring: () -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onReminderTime: (Int) -> Unit,
    onToggleWeekly: (Boolean) -> Unit,
    onToggleLock: (Boolean) -> Unit,
    onPasteBankText: () -> Unit,
    onThemeMode: (Int) -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onExportCsv: () -> Unit,
) {
    if (state == null) return
    val s = state.settings
    var showGoal by remember { mutableStateOf(false) }
    var showName by remember { mutableStateOf(false) }
    var showIncome by remember { mutableStateOf(false) }
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

        // Income is always here. The savings goal belongs to the optional daily limit, so it only shows while that
        // is on; Home is the only place that turns the limit back on.
        Section("This month")
        AppCard(Modifier.fillMaxWidth()) {
            SettingRow("Expected income", if (s.expectedIncomePaise > 0) Money.format(s.expectedIncomePaise) else "Not set") {
                if (s.dailyLimitHidden) showIncome = true else showGoal = true
            }
            if (!s.dailyLimitHidden) {
                Hairline(Modifier.padding(start = 16.dp))
                SettingRow("Savings goal", if (s.savingsGoalPaise > 0) Money.format(s.savingsGoalPaise) else "Not set") { showGoal = true }
            }
        }

        Section("Appearance")
        AppCard(Modifier.fillMaxWidth()) {
            ThemePicker(s.themeMode, onThemeMode)
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
            ActionRow("Add from a bank text", "Copy a bank text, then tap here to add it", onPasteBankText)
        }

        Section("Your data")
        AppCard(Modifier.fillMaxWidth()) {
            ActionRow("Back up everything", "Saved as a password-protected file", onBackup)
            Hairline(Modifier.padding(start = 16.dp))
            ActionRow("Restore from backup", "Replaces everything on this phone", onRestore)
            Hairline(Modifier.padding(start = 16.dp))
            ActionRow("Export transactions", "A CSV file you can open in a spreadsheet", onExportCsv)
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

        MadeBy()
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

    if (showIncome) {
        IncomeDialog(
            initialIncomePaise = s.expectedIncomePaise,
            onDismiss = { showIncome = false },
            onSave = {
                onSaveIncome(it)
                showIncome = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemePicker(mode: Int, onChange: (Int) -> Unit) {
    val options = listOf("System", "Light", "Dark")
    Column(Modifier.padding(16.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = mode == i,
                    onClick = { onChange(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (mode == 0) "Follows your phone's light or dark setting." else "Always ${options[mode].lowercase()}, whatever the phone is set to.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A short value on the right, like a name or an amount. Longer descriptions belong in [ActionRow]. */
@Composable
private fun SettingRow(title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The value is measured first and kept on one line; the title takes whatever width is left.
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * A tappable row with the title on top and a full-width description underneath. The description used to sit
 * beside the title, where it was squeezed into a narrow column and broke across several lines.
 */
@Composable
private fun ActionRow(title: String, description: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(2.dp))
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private const val APP_PAGE = "https://akashawal.com/money-tracker"

/** Credit line at the bottom of Settings; the whole line opens the app's page in the browser. */
@Composable
private fun MadeBy() {
    val uriHandler = LocalUriHandler.current
    Text(
        "Money Tracker by Akash Awal",
        style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.Underline),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Open the Money Tracker page") {
                // The app itself never goes online: this just hands the address to the phone's browser.
                try {
                    uriHandler.openUri(APP_PAGE)
                } catch (_: Exception) {
                    // No browser installed: nothing to open.
                }
            }
            .padding(vertical = 12.dp),
    )
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

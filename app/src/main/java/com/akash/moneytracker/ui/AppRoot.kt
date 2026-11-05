package com.akash.moneytracker.ui

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.akash.moneytracker.MainViewModel
import com.akash.moneytracker.data.Backup
import com.akash.moneytracker.data.BackupCrypto
import com.akash.moneytracker.data.BackupLimits
import com.akash.moneytracker.data.DataSnapshot
import com.akash.moneytracker.data.InvalidBackupException
import com.akash.moneytracker.data.WrongBackupPasswordException
import com.akash.moneytracker.ui.components.PasswordDialog
import com.akash.moneytracker.data.SuggestionEntity
import com.akash.moneytracker.data.TransactionEntity
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.Budgets
import com.akash.moneytracker.domain.Tags
import com.akash.moneytracker.export.Exporter
import com.akash.moneytracker.lock.AppLock
import com.akash.moneytracker.ui.add.AddTransactionSheet
import com.akash.moneytracker.ui.analytics.AnalyticsScreen
import com.akash.moneytracker.ui.components.NameDialog
import com.akash.moneytracker.ui.components.SwipeAwaySnackbarHost
import com.akash.moneytracker.ui.components.showFor
import com.akash.moneytracker.ui.home.HomeScreen
import com.akash.moneytracker.ui.settings.BudgetsScreen
import com.akash.moneytracker.ui.settings.RecurringScreen
import com.akash.moneytracker.ui.settings.SettingsScreen
import com.akash.moneytracker.ui.transactions.TransactionsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

private const val ROUTE_BUDGETS = "budgets"
private const val ROUTE_RECURRING = "recurring"

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Outlined.Home),
    Activity("activity", "Activity", Icons.Outlined.Receipt),
    Insights("insights", "Insights", Icons.Outlined.BarChart),
    Settings("settings", "Settings", Icons.Outlined.Settings),
}

/** Opens the add sheet: blank, to edit [edit], or pre-filled from a bank-SMS [suggestion]. */
private data class SheetRequest(val edit: TransactionEntity? = null, val suggestion: SuggestionEntity? = null)

private tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AppRoot(vm: MainViewModel, quickAddSignal: Int) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }
    var pendingRestore by remember { mutableStateOf<DataSnapshot?>(null) }
    var pendingPermission by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    var backupPrompt by remember { mutableStateOf(false) }
    var encryptedRestoreText by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    val home by vm.home.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val recurring by vm.recurring.collectAsStateWithLifecycle()

    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current


    // Opened from the widget, tile, shortcut or reminder notification.
    LaunchedEffect(quickAddSignal) {
        if (quickAddSignal > 0) sheet = SheetRequest()
    }

    val knownTags by vm.knownTags.collectAsStateWithLifecycle()

    fun say(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showFor(message)
        }
    }

    // Results of adding a bank text (shared from the messages app, or pasted).
    LaunchedEffect(Unit) { vm.events.collect { say(it) } }

    val deleteWithUndo: (TransactionEntity) -> Unit = { tx ->
        vm.delete(tx)
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showFor("Transaction deleted", actionLabel = "Undo") == SnackbarResult.ActionPerformed) {
                vm.restore(tx)
            }
        }
    }

    /** Saves, and warns if a new expense pushes its category budget to 80% or over. */
    val saveTransaction: (TransactionEntity, Boolean) -> Unit = { tx, isNew ->
        vm.save(tx)
        val state = home
        if (isNew && tx.type == TxType.EXPENSE && state != null && YearMonth.from(LocalDate.ofEpochDay(tx.epochDay)) == state.month) {
            val limit = state.budgetLimits[tx.categoryId]
            val category = categories.firstOrNull { it.id == tx.categoryId }
            if (limit != null && category != null) {
                Budgets.alertFor(category, limit, state.categorySpend[tx.categoryId] ?: 0L, tx.amountPaise)?.let(::say)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.invoke(granted)
        pendingPermission = null
    }

    /** Runs [onResult] once [permission] is granted, asking first if needed. */
    fun withPermission(permission: String, onResult: (Boolean) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            onResult(true)
        } else {
            pendingPermission = onResult
            permissionLauncher.launch(permission)
        }
    }

    fun withNotifications(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onResult(true)
        else withPermission(Manifest.permission.POST_NOTIFICATIONS, onResult)
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) { readBounded(context, uri) }
                if (BackupCrypto.isEncrypted(text)) {
                    passwordError = null
                    encryptedRestoreText = text
                } else {
                    pendingRestore = withContext(Dispatchers.Default) { Backup.fromJson(text) }
                }
            } catch (e: InvalidBackupException) {
                say(e.message ?: "That file couldn't be read.")
            } catch (e: Exception) {
                say("That file couldn't be read.")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Each screen applies the status-bar inset itself; letting the Scaffold add it too doubled the top gap.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SwipeAwaySnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                TabItem(Tab.Home, route, nav)
                TabItem(Tab.Activity, route, nav)
                AddItem(onClick = { sheet = SheetRequest() })
                TabItem(Tab.Insights, route, nav)
                TabItem(Tab.Settings, route, nav)
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    state = home,
                    onSaveGoal = vm::saveGoal,
                    onSeeAll = { nav.navigate(Tab.Activity.route) },
                    onEdit = { sheet = SheetRequest(edit = it) },
                    onOpenBudgets = { nav.navigate(ROUTE_BUDGETS) },
                    onReviewSuggestion = { sheet = SheetRequest(suggestion = it) },
                    onDismissSuggestion = vm::dismissSuggestion,
                    onHideLimit = { vm.setDailyLimitHidden(true) },
                    onShowLimit = { vm.setDailyLimitHidden(false) },
                    onResetLimit = vm::resetDailyLimit,
                    onCheckIn = vm::checkInToday,
                )
            }
            composable(Tab.Activity.route) {
                TransactionsScreen(
                    transactions = transactions,
                    categories = categories,
                    knownTags = knownTags,
                    onEdit = { sheet = SheetRequest(edit = it) },
                    onDelete = deleteWithUndo,
                )
            }
            composable(Tab.Insights.route) { AnalyticsScreen(transactions, home?.settings?.name) }
            composable(Tab.Settings.route) {
                val reminderMinute = home?.settings?.reminderMinute ?: (21 * 60)
                SettingsScreen(
                    state = home,
                    budgetCount = home?.budgetLimits?.size ?: 0,
                    recurringCount = recurring.size,
                    onSaveGoal = vm::saveGoal,
                    onSaveName = vm::saveName,
                    onOpenBudgets = { nav.navigate(ROUTE_BUDGETS) },
                    onOpenRecurring = { nav.navigate(ROUTE_RECURRING) },
                    onToggleReminder = { enable ->
                        if (!enable) vm.setReminder(false, reminderMinute)
                        else withNotifications { granted ->
                            if (granted) vm.setReminder(true, reminderMinute) else say("Allow notifications to get the daily reminder.")
                        }
                    },
                    onReminderTime = { vm.setReminder(true, it) },
                    onToggleWeekly = { enable ->
                        if (!enable) vm.setWeeklySummary(false)
                        else withNotifications { granted ->
                            if (granted) vm.setWeeklySummary(true) else say("Allow notifications to get the weekly summary.")
                        }
                    },
                    onToggleLock = { enable ->
                        val activity = context.findActivity()
                        when {
                            activity == null -> vm.setLock(enable)
                            !enable -> AppLock.prompt(activity, "Turn off app lock", { vm.setLock(false) }, ::say)
                            !AppLock.isAvailable(context) -> say("Set up a screen lock or fingerprint on your phone first.")
                            else -> AppLock.prompt(activity, "Turn on app lock", { vm.setLock(true) }, ::say)
                        }
                    },
                    onThemeMode = vm::setThemeMode,
                    onPasteBankText = {
                        // Read only when the user taps the button; Android shows its own "pasted from clipboard" notice.
                        val copied = clipboard.getText()?.text.orEmpty()
                        if (copied.isBlank()) say("Copy a bank text first, then tap this again.") else vm.importText(copied)
                    },
                    onBackup = { backupPrompt = true },
                    onRestore = { restoreLauncher.launch(arrayOf("*/*")) },
                    onExportCsv = {
                        scope.launch {
                            val file = withContext(Dispatchers.IO) {
                                Exporter.newFile(context, "money-tracker-transactions-${LocalDate.now()}.csv")
                                    .also { it.writeText(vm.transactionsCsv()) }
                            }
                            Exporter.share(context, file, "text/csv", "Export transactions")
                        }
                    },
                )
            }
            composable(ROUTE_BUDGETS) {
                BudgetsScreen(
                    categories = categories,
                    limits = home?.budgetLimits ?: emptyMap(),
                    spend = home?.categorySpend ?: emptyMap(),
                    onSetBudget = vm::setBudget,
                    onBack = { nav.popBackStack() },
                )
            }
            composable(ROUTE_RECURRING) {
                RecurringScreen(
                    rules = recurring,
                    categories = categories,
                    onSave = vm::saveRecurring,
                    onDelete = vm::deleteRecurring,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }

    // First launch: ask for a name once (null = never asked; "" = skipped).
    if (home != null && home?.settings?.name == null) {
        NameDialog(initialName = "", onSave = vm::saveName, onSkip = { vm.saveName("") })
    }

    sheet?.let { request ->
        AddTransactionSheet(
            categories = categories,
            editing = request.edit,
            suggestion = request.suggestion,
            knownTags = knownTags,
            onCreateCategory = vm::addCategory,
            onSave = {
                saveTransaction(it, request.edit == null)
                request.suggestion?.let { s -> vm.dismissSuggestion(s.id) }
                sheet = null
            },
            onDismiss = { sheet = null },
        )
    }

    if (backupPrompt) {
        PasswordDialog(
            title = "Protect your backup",
            message = "Your backup is encrypted with this password, so it's safe to keep in Drive or send to yourself. " +
                "There is no way to recover a forgotten password.",
            confirm = true,
            submitLabel = "Encrypt and share",
            errorText = null,
            busy = working,
            onSubmit = { password ->
                working = true
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.Default) {
                            val sealed = BackupCrypto.encrypt(vm.backupJson(), password)
                            Exporter.newFile(context, "money-tracker-backup-${LocalDate.now()}.mtbk").also { it.writeText(sealed) }
                        }
                        backupPrompt = false
                        Exporter.share(context, file, "application/octet-stream", "Save backup")
                    } finally {
                        working = false
                        password.fill('\u0000')
                    }
                }
            },
            onDismiss = { backupPrompt = false },
            skipLabel = "Save without a password instead",
            onSkip = {
                backupPrompt = false
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        Exporter.newFile(context, "money-tracker-backup-${LocalDate.now()}.json").also { it.writeText(vm.backupJson()) }
                    }
                    Exporter.share(context, file, "application/json", "Save backup")
                }
            },
        )
    }

    encryptedRestoreText?.let { sealed ->
        PasswordDialog(
            title = "Enter the backup password",
            message = "This backup is password-protected.",
            confirm = false,
            submitLabel = "Unlock",
            errorText = passwordError,
            busy = working,
            onSubmit = { password ->
                working = true
                scope.launch {
                    try {
                        val snapshot = withContext(Dispatchers.Default) { Backup.fromJson(BackupCrypto.decrypt(sealed, password)) }
                        encryptedRestoreText = null
                        passwordError = null
                        pendingRestore = snapshot
                    } catch (e: WrongBackupPasswordException) {
                        passwordError = e.message
                    } catch (e: InvalidBackupException) {
                        encryptedRestoreText = null
                        say(e.message ?: "That file couldn't be read.")
                    } finally {
                        working = false
                        password.fill('\u0000')
                    }
                }
            },
            onDismiss = {
                encryptedRestoreText = null
                passwordError = null
            },
        )
    }

    pendingRestore?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Replace everything on this phone?") },
            text = {
                Text(
                    "This backup has ${backup.transactions.size} transactions and ${backup.categories.size} categories. " +
                        "What's on this phone now (${transactions?.size ?: 0} transactions) will be replaced, and this can't be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        vm.restoreBackup(backup)
                        nav.navigate(Tab.Home.route) {
                            popUpTo(nav.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                        say("Backup restored")
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

/** Reads a chosen file but gives up past the size limit, so a huge file can't exhaust memory. */
private fun readBounded(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: throw InvalidBackupException("That file couldn't be opened.")
    input.use { stream ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > BackupLimits.MAX_BYTES) throw InvalidBackupException("This file is too large to be a backup.")
        }
        return out.toString(Charsets.UTF_8.name())
    }
}

@Composable
private fun RowScope.TabItem(tab: Tab, currentRoute: String?, nav: NavHostController) {
    // Budgets and Recurring are opened from Settings, so Settings stays highlighted while they're showing.
    val selected = currentRoute == tab.route ||
        (tab == Tab.Settings && (currentRoute == ROUTE_BUDGETS || currentRoute == ROUTE_RECURRING))
    NavigationBarItem(
        selected = selected,
        onClick = {
            nav.navigate(tab.route) {
                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
        icon = { Icon(tab.icon, contentDescription = tab.label) },
        label = { Text(tab.label) },
        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

/** Center slot: a raised round button that opens the add sheet instead of navigating. */
@Composable
private fun RowScope.AddItem(onClick: () -> Unit) {
    NavigationBarItem(
        selected = false,
        onClick = onClick,
        icon = {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add transaction",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
    )
}

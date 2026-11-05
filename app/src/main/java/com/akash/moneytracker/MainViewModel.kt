package com.akash.moneytracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.akash.moneytracker.data.Backup
import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.DataSnapshot
import com.akash.moneytracker.data.RecurringEntity
import com.akash.moneytracker.data.RecurringWithCategory
import com.akash.moneytracker.data.Repository
import com.akash.moneytracker.data.Settings
import com.akash.moneytracker.data.SettingsStore
import com.akash.moneytracker.data.TransactionEntity
import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.domain.Recurring
import com.akash.moneytracker.domain.monthKey
import com.akash.moneytracker.data.SuggestionEntity
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.sms.SmsParser
import com.akash.moneytracker.domain.Tags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Longest text accepted when importing; real bank texts are a few hundred characters. */
private const val MAX_IMPORT_CHARS = 2_000

class MainViewModel(
    private val repo: Repository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> = repo.categories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Null until the first database read finishes, so screens don't flash an empty state. */
    val transactions: StateFlow<List<TransactionWithCategory>?> = repo.allTransactions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recurring: StateFlow<List<RecurringWithCategory>> = repo.recurring()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Re-emits when the date changes so the daily limit rolls over at midnight. */
    private val today = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60_000)
        }
    }.distinctUntilChanged()

    init {
        // Creates recurring transactions that have come due, at launch and whenever the date rolls over.
        viewModelScope.launch { today.collect { repo.processRecurring(it) } }
    }

    private val planning = combine(repo.budgets(), repo.recurring(), repo.categories()) { budgets, rules, categories ->
        Planning(budgets.associate { it.categoryId to it.limitPaise }, rules.map { it.rule }, categories)
    }

    /**
     * Built from the one shared [transactions] query (not a second and third copy of it), and calculated on a
     * background thread: with a long history, doing this on the main thread froze the app after every
     * delete or new category, because each of those re-reads and re-totals the whole table.
     */
    val home: StateFlow<HomeState?> =
        combine(settingsStore.settings, transactions.filterNotNull(), today, planning, repo.suggestions()) { s, all, today, plan, suggestions ->
            computeHome(s, all, today, plan, suggestions)
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Tags used most often, offered as quick picks in the add sheet. Worked out off the main thread. */
    val knownTags: StateFlow<List<String>> = transactions
        .filterNotNull()
        .map { rows ->
            rows.flatMap { Tags.decode(it.tx.tags) }
                .groupingBy { it }.eachCount()
                .entries.sortedByDescending { it.value }.map { it.key }.take(12)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(tx: TransactionEntity) {
        viewModelScope.launch { repo.save(tx) }
    }

    fun delete(tx: TransactionEntity) {
        viewModelScope.launch { repo.delete(tx) }
    }

    fun restore(tx: TransactionEntity) {
        viewModelScope.launch { repo.restore(tx) }
    }

    /** Creates a category and reports its new id so the add sheet can select it. */
    fun addCategory(category: CategoryEntity, onCreated: (Long) -> Unit) {
        viewModelScope.launch { onCreated(repo.addCategory(category)) }
    }

    fun setBudget(categoryId: Long, limitPaise: Long) {
        viewModelScope.launch { repo.setBudget(categoryId, limitPaise) }
    }

    fun saveRecurring(rule: RecurringEntity) {
        viewModelScope.launch {
            val toSave = if (rule.id == 0L) {
                rule.copy(lastGeneratedMonth = Recurring.initialLastGenerated(rule.dayOfMonth, LocalDate.now()))
            } else rule
            repo.saveRecurring(toSave)
            repo.processRecurring(LocalDate.now())
        }
    }

    fun deleteRecurring(rule: RecurringEntity) {
        viewModelScope.launch { repo.deleteRecurring(rule) }
    }

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)

    /** One-line results to show the user, e.g. after adding a bank text. */
    val events: SharedFlow<String> = _events

    /**
     * Turns a bank text (shared from the messages app, or pasted) into a suggestion to confirm. The text is only
     * read here, never stored: a hash of it is kept so the same message isn't suggested twice.
     */
    fun importText(raw: String) {
        viewModelScope.launch {
            val text = raw.take(MAX_IMPORT_CHARS)
            val parsed = SmsParser.parse(text)
            if (parsed == null) {
                _events.emit("Couldn't find a payment in that text.")
                return@launch
            }
            val now = java.time.LocalDateTime.now()
            val added = repo.addSuggestion(
                SuggestionEntity(
                    amountPaise = parsed.amountPaise,
                    type = parsed.type,
                    title = parsed.title,
                    epochDay = now.toLocalDate().toEpochDay(),
                    minuteOfDay = now.hour * 60 + now.minute,
                    fingerprint = sha256(text.trim()),
                )
            )
            _events.emit(if (added) "Found ${Money.format(parsed.amountPaise)} · ${parsed.title}. Check Home to add it." else "That text is already in your suggestions.")
        }
    }

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    fun dismissSuggestion(id: Long) {
        viewModelScope.launch { repo.dismissSuggestion(id) }
    }

    suspend fun snapshot(): DataSnapshot = repo.snapshot(settingsStore.settings.first())

    suspend fun backupJson(): String = Backup.toJson(snapshot())

    suspend fun transactionsCsv(): String = Backup.toCsv(repo.allTransactions().first())

    /** Replaces everything on this device with [snapshot]. */
    suspend fun restoreBackup(snapshot: DataSnapshot) {
        repo.replaceAll(snapshot)
        settingsStore.replaceAll(snapshot.settings)
    }

    fun saveName(name: String) {
        viewModelScope.launch { settingsStore.saveName(name) }
    }

    fun saveGoal(incomePaise: Long, goalPaise: Long) {
        viewModelScope.launch {
            settingsStore.saveGoal(incomePaise, goalPaise, LocalDate.now().monthKey)
        }
    }

    fun setReminder(enabled: Boolean, minuteOfDay: Int) {
        viewModelScope.launch { settingsStore.setReminder(enabled, minuteOfDay) }
    }

    fun setWeeklySummary(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setWeeklySummary(enabled) }
    }

    /** Removes (or brings back) the daily limit on Home; the goal and income are kept. */
    fun setDailyLimitHidden(hidden: Boolean) {
        viewModelScope.launch { settingsStore.setDailyLimitHidden(hidden) }
    }

    /** Forgets the savings goal and expected income, so Home asks for them again. Transactions are untouched. */
    fun resetDailyLimit() {
        viewModelScope.launch { settingsStore.resetGoal() }
    }

    /** "I spent nothing today": counts as logging so a quiet day doesn't break the streak. */
    fun checkInToday() {
        viewModelScope.launch { settingsStore.checkIn(LocalDate.now().toEpochDay()) }
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setLock(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setLock(enabled) }
    }
}

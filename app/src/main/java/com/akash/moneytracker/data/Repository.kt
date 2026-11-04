package com.akash.moneytracker.data

import androidx.room.withTransaction
import com.akash.moneytracker.Planning
import com.akash.moneytracker.domain.Recurring
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth

class Repository(private val db: AppDatabase) {
    private val txDao = db.transactions()
    private val categoryDao = db.categories()
    private val budgetDao = db.budgets()
    private val recurringDao = db.recurring()
    private val suggestionDao = db.suggestions()

    fun transactionsBetween(fromDay: Long, toDay: Long): Flow<List<TransactionWithCategory>> =
        txDao.observeBetween(fromDay, toDay)

    fun allTransactions(): Flow<List<TransactionWithCategory>> = txDao.observeAll()

    fun categories(): Flow<List<CategoryEntity>> = categoryDao.observeActive()

    fun budgets(): Flow<List<BudgetEntity>> = budgetDao.observeAll()

    fun recurring(): Flow<List<RecurringWithCategory>> = recurringDao.observeAll()

    fun suggestions(): Flow<List<SuggestionEntity>> = suggestionDao.observeAll()

    /** False if this SMS was already suggested before. */
    suspend fun addSuggestion(s: SuggestionEntity): Boolean {
        val added = suggestionDao.insert(s) != -1L
        if (added) suggestionDao.trimTo(MAX_SUGGESTIONS)
        return added
    }

    suspend fun dismissSuggestion(id: Long) = suggestionDao.remove(id)

    /** One-off reads for the widget, the tile and the notification workers, which don't hold a Flow open. */
    suspend fun allOnce(): List<TransactionWithCategory> = txDao.allWithCategory()

    suspend fun planningOnce(): Planning = Planning(
        budgets = budgetDao.all().associate { it.categoryId to it.limitPaise },
        rules = recurringDao.all(),
        categories = categoryDao.all().filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id })),
    )

    suspend fun save(tx: TransactionEntity) {
        if (tx.id == 0L) txDao.insert(tx) else txDao.update(tx)
    }

    suspend fun delete(tx: TransactionEntity) = txDao.delete(tx)

    /** Re-inserts a deleted transaction under its original id (used by "Undo"). */
    suspend fun restore(tx: TransactionEntity) {
        txDao.insert(tx)
    }

    suspend fun addCategory(category: CategoryEntity): Long = categoryDao.insert(category)

    /** A limit of 0 (or less) removes the budget. */
    suspend fun setBudget(categoryId: Long, limitPaise: Long) {
        if (limitPaise > 0) budgetDao.upsert(BudgetEntity(categoryId, limitPaise)) else budgetDao.remove(categoryId)
    }

    suspend fun saveRecurring(rule: RecurringEntity) {
        if (rule.id == 0L) recurringDao.insert(rule) else recurringDao.update(rule)
    }

    suspend fun deleteRecurring(rule: RecurringEntity) = recurringDao.delete(rule)

    /**
     * Creates the transactions that recurring rules owe up to [today]. Safe to call repeatedly:
     * each rule remembers the last month it produced, so nothing is created twice.
     */
    suspend fun processRecurring(today: LocalDate) {
        val current = YearMonth.from(today)
        for (rule in recurringDao.all()) {
            db.withTransaction {
                var month = Recurring.monthOf(rule.lastGeneratedMonth).plusMonths(1)
                var last = rule.lastGeneratedMonth
                while (!month.isAfter(current)) {
                    val due = Recurring.dueDate(rule, month)
                    if (due.isAfter(today)) break
                    txDao.insert(
                        TransactionEntity(
                            amountPaise = rule.amountPaise,
                            type = rule.type,
                            categoryId = rule.categoryId,
                            note = rule.note,
                            epochDay = due.toEpochDay(),
                            minuteOfDay = rule.minuteOfDay,
                            recurringId = rule.id,
                        )
                    )
                    last = Recurring.keyOf(month)
                    month = month.plusMonths(1)
                }
                if (last != rule.lastGeneratedMonth) recurringDao.update(rule.copy(lastGeneratedMonth = last))
            }
        }
    }

    suspend fun snapshot(settings: Settings): DataSnapshot = DataSnapshot(
        categories = categoryDao.all(),
        transactions = txDao.all(),
        budgets = budgetDao.all(),
        recurring = recurringDao.all(),
        settings = settings,
    )

    /** Wipes the database and loads [s] in a single transaction, so a failure leaves the old data intact. */
    suspend fun replaceAll(s: DataSnapshot) {
        db.withTransaction {
            txDao.deleteAll()
            recurringDao.deleteAll()
            budgetDao.deleteAll()
            categoryDao.deleteAll()
            categoryDao.insertAll(s.categories)
            budgetDao.insertAll(s.budgets)
            recurringDao.insertAll(s.recurring)
            txDao.insertAll(s.transactions)
        }
    }

    suspend fun seedIfEmpty() {
        if (categoryDao.count() > 0) return
        categoryDao.insertAll(DefaultCategories)
    }
}

private const val MAX_SUGGESTIONS = 50

private fun expense(name: String, emoji: String, color: Long, order: Int) =
    CategoryEntity(name = name, emoji = emoji, color = color, type = TxType.EXPENSE, sortOrder = order)

private fun income(name: String, emoji: String, color: Long, order: Int) =
    CategoryEntity(name = name, emoji = emoji, color = color, type = TxType.INCOME, sortOrder = order)

private val DefaultCategories = listOf(
    expense("Food", "🍔", 0xFFFF511C, 0),
    expense("Groceries", "🛒", 0xFF34C38F, 1),
    expense("Transport", "🚌", 0xFF72A7E3, 2),
    expense("Shopping", "🛍️", 0xFFE192C7, 3),
    expense("Bills", "🧾", 0xFFFFBB00, 4),
    expense("Entertainment", "🎬", 0xFF9A89E3, 5),
    expense("Health", "💊", 0xFFE5393B, 6),
    expense("Travel", "✈️", 0xFFC9A574, 7),
    expense("Other", "📦", 0xFF5B6B7C, 8),
    income("Salary", "💼", 0xFF1E9E5A, 9),
    income("Other income", "🎁", 0xFF34C38F, 10),
)

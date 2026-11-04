package com.akash.moneytracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Transaction
    @Query("SELECT * FROM transactions WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, minuteOfDay DESC, createdAt DESC")
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY epochDay DESC, minuteOfDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionWithCategory>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY epochDay DESC, minuteOfDay DESC, createdAt DESC")
    suspend fun allWithCategory(): List<TransactionWithCategory>

    @Query("SELECT * FROM transactions")
    suspend fun all(): List<TransactionEntity>

    @Insert
    suspend fun insert(tx: TransactionEntity): Long

    @Insert
    suspend fun insertAll(txs: List<TransactionEntity>)

    @Update
    suspend fun update(tx: TransactionEntity)

    @Delete
    suspend fun delete(tx: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun all(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Insert
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<BudgetEntity>

    @Upsert
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun remove(categoryId: Long)

    @Insert
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()
}

@Dao
interface SuggestionDao {
    @Query("SELECT * FROM suggestions ORDER BY epochDay DESC, minuteOfDay DESC, id DESC")
    fun observeAll(): Flow<List<SuggestionEntity>>

    /** Returns the new row id, or -1 if this SMS was already suggested. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(suggestion: SuggestionEntity): Long

    @Query("DELETE FROM suggestions WHERE id = :id")
    suspend fun remove(id: Long)

    /** Keeps only the newest [keep] rows, so a flood of spoofed texts can't grow the table without limit. */
    @Query("DELETE FROM suggestions WHERE id NOT IN (SELECT id FROM suggestions ORDER BY id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}

@Dao
interface RecurringDao {
    @Transaction
    @Query("SELECT * FROM recurring ORDER BY dayOfMonth, id")
    fun observeAll(): Flow<List<RecurringWithCategory>>

    @Query("SELECT * FROM recurring")
    suspend fun all(): List<RecurringEntity>

    @Insert
    suspend fun insert(rule: RecurringEntity): Long

    @Insert
    suspend fun insertAll(rules: List<RecurringEntity>)

    @Update
    suspend fun update(rule: RecurringEntity)

    @Delete
    suspend fun delete(rule: RecurringEntity)

    @Query("DELETE FROM recurring")
    suspend fun deleteAll()
}

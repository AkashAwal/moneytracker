package com.akash.moneytracker.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class TxType { EXPENSE, INCOME }

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    /** ARGB, stored as Long so 0xFF-prefixed literals fit without sign tricks. */
    val color: Long,
    val type: TxType,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
)

/** Amounts are stored in paise so sums never pick up floating-point error. */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("epochDay")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: TxType,
    val categoryId: Long,
    val note: String = "",
    val epochDay: Long,
    /** Time of day the transaction happened, as minutes after midnight (0..1439). */
    val minuteOfDay: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    /** Set when a recurring rule created this transaction; such bills count as fixed, not daily, spending. */
    val recurringId: Long? = null,
    /** Lowercase labels such as "goa" or "work", comma-separated; see [com.akash.moneytracker.domain.Tags]. */
    val tags: String = "",
)

/** A transaction spotted in a bank SMS, waiting for the user to confirm it and pick a category. */
@Entity(tableName = "suggestions", indices = [Index(value = ["fingerprint"], unique = true)])
data class SuggestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: TxType,
    /** Merchant or counterparty when one could be read, otherwise a generic label. */
    val title: String,
    val epochDay: Long,
    val minuteOfDay: Int,
    /** Hash of the SMS, so the same message is never suggested twice. The text itself is not kept. */
    val fingerprint: String,
)

/** Monthly spending limit for one expense category; absent row = no budget. */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BudgetEntity(
    @PrimaryKey val categoryId: Long,
    val limitPaise: Long,
)

/** A transaction that repeats on the same day every month (rent, subscriptions, salary). */
@Entity(
    tableName = "recurring",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId")],
)
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val type: TxType,
    val categoryId: Long,
    val note: String = "",
    /** 1..31; shorter months use their last day. */
    val dayOfMonth: Int,
    val minuteOfDay: Int = 9 * 60,
    /** yyyyMM of the latest month whose transaction has already been created. */
    val lastGeneratedMonth: Int,
)

data class RecurringWithCategory(
    @Embedded val rule: RecurringEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity,
)

data class TransactionWithCategory(
    @Embedded val tx: TransactionEntity,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity,
)

package com.akash.moneytracker

import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.TransactionEntity
import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import java.time.LocalDate

val food = CategoryEntity(1, "Food", "🍔", 0xFFFF511C, TxType.EXPENSE, 0)
val shopping = CategoryEntity(2, "Shopping", "🛍️", 0xFFE192C7, TxType.EXPENSE, 1)
val salary = CategoryEntity(3, "Salary", "💼", 0xFF1E9E5A, TxType.INCOME, 2)

fun rupees(n: Long) = n * 100

fun row(
    id: Long,
    category: CategoryEntity,
    amountRupees: Long,
    date: LocalDate,
    note: String = "",
    recurringId: Long? = null,
    tags: String = "",
) = TransactionWithCategory(
    TransactionEntity(
        id = id,
        amountPaise = rupees(amountRupees),
        type = category.type,
        categoryId = category.id,
        note = note,
        epochDay = date.toEpochDay(),
        minuteOfDay = 12 * 60,
        createdAt = 0L,
        recurringId = recurringId,
        tags = tags,
    ),
    category,
)

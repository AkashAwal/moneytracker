package com.akash.moneytracker.domain

import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType

/** Search text plus the optional filters from the Activity filter sheet. */
data class ActivityFilter(
    val query: String = "",
    val type: TxType? = null,
    val categoryId: Long? = null,
    val tag: String? = null,
    val fromDay: Long? = null,
    val toDay: Long? = null,
    val minPaise: Long? = null,
    val maxPaise: Long? = null,
) {
    /** How many filter groups (type, category, tag, dates, amount) are set; shown as a badge on the filter button. */
    val activeFilters: Int
        get() = listOf(
            type != null,
            categoryId != null,
            tag != null,
            fromDay != null || toDay != null,
            minPaise != null || maxPaise != null,
        ).count { it }

    val isActive: Boolean get() = query.isNotBlank() || activeFilters > 0

    fun matches(item: TransactionWithCategory): Boolean {
        val tx = item.tx
        if (type != null && tx.type != type) return false
        if (categoryId != null && tx.categoryId != categoryId) return false
        if (tag != null && tag !in Tags.decode(tx.tags)) return false
        if (fromDay != null && tx.epochDay < fromDay) return false
        if (toDay != null && tx.epochDay > toDay) return false
        if (minPaise != null && tx.amountPaise < minPaise) return false
        if (maxPaise != null && tx.amountPaise > maxPaise) return false
        val q = query.trim()
        if (q.isNotEmpty()) {
            val hit = tx.note.contains(q, ignoreCase = true) ||
                item.category.name.contains(q, ignoreCase = true) ||
                tx.tags.contains(q.removePrefix("#"), ignoreCase = true) ||
                Money.toInput(tx.amountPaise).contains(q.replace(",", ""))
            if (!hit) return false
        }
        return true
    }
}

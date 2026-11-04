package com.akash.moneytracker.domain

import com.akash.moneytracker.data.CategoryEntity

enum class BudgetLevel { OK, WARNING, OVER }

data class BudgetStatus(val category: CategoryEntity, val limit: Long, val spent: Long) {
    val fraction: Float get() = if (limit > 0) spent.toFloat() / limit else 0f
    val level: BudgetLevel get() = Budgets.levelOf(spent, limit)
}

object Budgets {
    /** Warn once a category has used this much of its budget. */
    const val WARN_AT = 0.8

    fun levelOf(spent: Long, limit: Long): BudgetLevel = when {
        limit <= 0 -> BudgetLevel.OK
        spent >= limit -> BudgetLevel.OVER
        spent >= limit * WARN_AT -> BudgetLevel.WARNING
        else -> BudgetLevel.OK
    }

    fun statuses(limits: Map<Long, Long>, categories: List<CategoryEntity>, spend: Map<Long, Long>): List<BudgetStatus> =
        categories
            .filter { it.id in limits }
            .map { BudgetStatus(it, limits.getValue(it.id), spend[it.id] ?: 0L) }

    /**
     * A message if adding [amount] to [spentBefore] pushes the category into a worse level
     * (OK -> WARNING, or anything -> OVER); null if nothing newly crossed.
     */
    fun alertFor(category: CategoryEntity, limit: Long, spentBefore: Long, amount: Long): String? {
        val before = levelOf(spentBefore, limit)
        val after = levelOf(spentBefore + amount, limit)
        if (after.ordinal <= before.ordinal) return null
        val limitText = Money.format(limit)
        return when (after) {
            BudgetLevel.OVER -> "${category.name} is over its $limitText budget"
            BudgetLevel.WARNING -> "${category.name} is at ${((spentBefore + amount) * 100.0 / limit).toInt()}% of its $limitText budget"
            BudgetLevel.OK -> null
        }
    }
}

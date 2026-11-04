package com.akash.moneytracker.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.akash.moneytracker.data.CategoryEntity
import com.akash.moneytracker.data.TransactionEntity
import com.akash.moneytracker.data.TransactionWithCategory
import com.akash.moneytracker.data.TxType
import com.akash.moneytracker.domain.ActivityFilter
import com.akash.moneytracker.domain.Money
import com.akash.moneytracker.domain.dayLabel
import com.akash.moneytracker.ui.components.AppCard
import com.akash.moneytracker.ui.components.EmptyState
import com.akash.moneytracker.ui.components.Hairline
import com.akash.moneytracker.ui.components.TransactionRow
import com.akash.moneytracker.ui.theme.extra
import java.time.LocalDate

@Composable
fun TransactionsScreen(
    transactions: List<TransactionWithCategory>?,
    categories: List<CategoryEntity>,
    knownTags: List<String>,
    onEdit: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
) {
    if (transactions == null) return
    val today = remember { LocalDate.now() }
    var filter by remember { mutableStateOf(ActivityFilter()) }
    var showFilters by remember { mutableStateOf(false) }

    val visible = remember(transactions, filter) { transactions.filter(filter::matches) }
    val grouped = remember(visible) { visible.groupBy { it.tx.epochDay }.toList() }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Activity",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        if (transactions.isEmpty()) {
            EmptyState("🧾", "No transactions yet", "Everything you log will show up here, newest first.")
            return
        }

        OutlinedTextField(
            value = filter.query,
            onValueChange = { filter = filter.copy(query = it) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Search notes, categories, amounts") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                Box {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { filter = filter.copy(query = "") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        FilterBar(filter, visible, onOpenFilters = { showFilters = true })

        if (visible.isEmpty()) {
            EmptyState("🔍", "Nothing matches", "Try a different search or loosen the filters.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                grouped.forEach { (day, items) ->
                    item(key = "header-$day") {
                        Text(
                            dayLabel(day, today),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        )
                    }
                    item(key = "group-$day") {
                        AppCard(Modifier.fillMaxWidth()) {
                            items.forEachIndexed { i, item ->
                                if (i > 0) Hairline(Modifier.padding(start = 72.dp))
                                SwipeRow(item, onEdit, onDelete)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(filter, categories, knownTags, onChange = { filter = it }, onDismiss = { showFilters = false })
    }
}

/** Filter button (with a count badge) and, while filtering, a one-line summary of the results. */
@Composable
private fun FilterBar(filter: ActivityFilter, visible: List<TransactionWithCategory>, onOpenFilters: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val summary = if (filter.isActive) {
            val out = visible.filter { it.tx.type == TxType.EXPENSE }.sumOf { it.tx.amountPaise }
            val inn = visible.filter { it.tx.type == TxType.INCOME }.sumOf { it.tx.amountPaise }
            listOfNotNull(
                "${visible.size} ${if (visible.size == 1) "result" else "results"}",
                if (out > 0) "${Money.format(out)} out" else null,
                if (inn > 0) "${Money.format(inn)} in" else null,
            ).joinToString(" · ")
        } else ""
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onOpenFilters) {
            BadgedBox(badge = { if (filter.activeFilters > 0) Badge { Text(filter.activeFilters.toString()) } }) {
                Icon(Icons.Outlined.FilterList, contentDescription = "Filters")
            }
        }
    }
}

@Composable
private fun SwipeRow(
    item: TransactionWithCategory,
    onEdit: (TransactionEntity) -> Unit,
    onDelete: (TransactionEntity) -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(item.tx)
                true
            } else false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.extra.negative)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color.White)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.surface)) {
            TransactionRow(item = item, subtitle = null, onClick = { onEdit(item.tx) })
        }
    }
}

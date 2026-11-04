package com.akash.moneytracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.akash.moneytracker.MainActivity
import com.akash.moneytracker.MoneyTrackerApp
import com.akash.moneytracker.R
import com.akash.moneytracker.computeHome
import com.akash.moneytracker.domain.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Home-screen widget: what's left to spend today, and a button that opens the add sheet straight away. */
class QuickAddWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                render(context, manager, appWidgetIds)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** Redraws every placed instance; called when the data changes. */
        suspend fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QuickAddWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val app = context.applicationContext as MoneyTrackerApp
            val settings = app.settingsStore.settings.first()
            val home = computeHome(settings, app.repository.allOnce(), LocalDate.now(), app.repository.planningOnce())
            val budget = home.budget
            val views = RemoteViews(context.packageName, R.layout.widget_quick_add)

            val (amount, detail, over) = when {
                // A home-screen widget is visible to anyone who sees the screen, so it shows no figures while the app lock is on.
                settings.lockEnabled -> Triple("🔒 Locked", "Amounts hidden · tap + to add", false)
                budget == null -> Triple("Set your goal", "Open the app to start", false)
                budget.overToday -> Triple(
                    "Over by ${Money.format(budget.spentToday - budget.todayAllowance)}",
                    "Spent ${Money.format(budget.spentToday)} of ${Money.format(budget.todayAllowance)}",
                    true,
                )
                else -> Triple(
                    "${Money.format(budget.leftToday)} left",
                    "Spent ${Money.format(budget.spentToday)} of ${Money.format(budget.todayAllowance)}",
                    false,
                )
            }
            views.setTextViewText(R.id.widget_amount, amount)
            views.setTextViewText(R.id.widget_detail, detail)
            views.setTextColor(
                R.id.widget_amount,
                ContextCompat.getColor(context, if (over) R.color.widget_negative else R.color.widget_text),
            )
            views.setOnClickPendingIntent(R.id.widget_root, activity(context, quickAdd = false, request = 0))
            views.setOnClickPendingIntent(R.id.widget_add, activity(context, quickAdd = true, request = 1))
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun activity(context: Context, quickAdd: Boolean, request: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (quickAdd) action = MainActivity.ACTION_QUICK_ADD
            }
            return PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

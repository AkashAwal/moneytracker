package com.akash.moneytracker

import android.app.Application
import com.akash.moneytracker.data.AppDatabase
import com.akash.moneytracker.data.Repository
import com.akash.moneytracker.data.SettingsStore
import com.akash.moneytracker.export.Exporter
import com.akash.moneytracker.notify.Notifications
import com.akash.moneytracker.notify.Scheduler
import com.akash.moneytracker.widget.QuickAddWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

class MoneyTrackerApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var settingsStore: SettingsStore
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this))
        settingsStore = SettingsStore(this)
        Notifications.createChannels(this)

        appScope.launch { repository.seedIfEmpty() }
        appScope.launch(Dispatchers.IO) { Exporter.cleanup(this@MoneyTrackerApp) }

        // Keeps the scheduled reminder and summary in line with the switches in Settings.
        appScope.launch {
            settingsStore.settings
                .distinctUntilChangedBy { Triple(it.reminderEnabled, it.reminderMinute, it.weeklySummaryEnabled) }
                .collect { Scheduler.sync(this@MoneyTrackerApp, it) }
        }

        // Redraws the home-screen widget whenever what it shows could have changed.
        appScope.launch {
            combine(repository.allTransactions(), settingsStore.settings, repository.budgets(), repository.recurring()) { _, _, _, _ -> }
                .debounce(500)
                .collect { QuickAddWidget.refresh(this@MoneyTrackerApp) }
        }
    }
}

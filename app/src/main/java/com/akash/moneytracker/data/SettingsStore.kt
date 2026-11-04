package com.akash.moneytracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    /** What the user expects to earn this month; 0 means "not set". */
    val expectedIncomePaise: Long = 0,
    val savingsGoalPaise: Long = 0,
    /** Month the goal was set for, as yyyyMM; 0 if never set. */
    val goalMonth: Int = 0,
    /** null = never asked, "" = the user skipped it. */
    val name: String? = null,
    // Device-level switches. These are not part of a backup, so restoring never changes them.
    val reminderEnabled: Boolean = false,
    /** Minutes after midnight for the daily "log your expenses" nudge. */
    val reminderMinute: Int = 21 * 60,
    val weeklySummaryEnabled: Boolean = false,
    val lockEnabled: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val income = longPreferencesKey("expected_income_paise")
        val goal = longPreferencesKey("savings_goal_paise")
        val goalMonth = intPreferencesKey("goal_month")
        val name = stringPreferencesKey("name")
        val reminderEnabled = booleanPreferencesKey("reminder_enabled")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val weeklyEnabled = booleanPreferencesKey("weekly_summary_enabled")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            expectedIncomePaise = p[Keys.income] ?: 0,
            savingsGoalPaise = p[Keys.goal] ?: 0,
            goalMonth = p[Keys.goalMonth] ?: 0,
            name = p[Keys.name],
            reminderEnabled = p[Keys.reminderEnabled] ?: false,
            reminderMinute = p[Keys.reminderMinute] ?: (21 * 60),
            weeklySummaryEnabled = p[Keys.weeklyEnabled] ?: false,
            lockEnabled = p[Keys.lockEnabled] ?: false,
        )
    }

    suspend fun saveName(name: String) {
        context.dataStore.edit { it[Keys.name] = name.trim() }
    }

    suspend fun saveGoal(incomePaise: Long, goalPaise: Long, month: Int) {
        context.dataStore.edit {
            it[Keys.income] = incomePaise
            it[Keys.goal] = goalPaise
            it[Keys.goalMonth] = month
        }
    }

    suspend fun setReminder(enabled: Boolean, minuteOfDay: Int) {
        context.dataStore.edit {
            it[Keys.reminderEnabled] = enabled
            it[Keys.reminderMinute] = minuteOfDay
        }
    }

    suspend fun setWeeklySummary(enabled: Boolean) {
        context.dataStore.edit { it[Keys.weeklyEnabled] = enabled }
    }

    suspend fun setLock(enabled: Boolean) {
        context.dataStore.edit { it[Keys.lockEnabled] = enabled }
    }

    /** Restores the money-related settings from a backup; device switches are left as they are. */
    suspend fun replaceAll(s: Settings) {
        context.dataStore.edit {
            it[Keys.income] = s.expectedIncomePaise
            it[Keys.goal] = s.savingsGoalPaise
            it[Keys.goalMonth] = s.goalMonth
            if (s.name != null) it[Keys.name] = s.name else it.remove(Keys.name)
        }
    }
}

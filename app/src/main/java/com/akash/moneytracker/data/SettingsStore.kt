package com.akash.moneytracker.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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
    /** The daily limit and savings forecast are optional: true while the user has removed them from Home. */
    val dailyLimitHidden: Boolean = false,
    /** Days (epoch days) the user marked "spent nothing", so a quiet day keeps the logging streak alive. */
    val checkInDays: Set<Long> = emptySet(),
    // Device-level switches. These are not part of a backup, so restoring never changes them.
    val reminderEnabled: Boolean = false,
    /** Minutes after midnight for the daily "log your expenses" nudge. */
    val reminderMinute: Int = 21 * 60,
    val weeklySummaryEnabled: Boolean = false,
    val lockEnabled: Boolean = false,
    /** 0 = follow the phone, 1 = light, 2 = dark. */
    val themeMode: Int = 0,
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
        val themeMode = intPreferencesKey("theme_mode")
        val dailyLimitHidden = booleanPreferencesKey("daily_limit_hidden")
        val checkInDays = stringSetPreferencesKey("check_in_days")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            expectedIncomePaise = p[Keys.income] ?: 0,
            savingsGoalPaise = p[Keys.goal] ?: 0,
            goalMonth = p[Keys.goalMonth] ?: 0,
            name = p[Keys.name],
            dailyLimitHidden = p[Keys.dailyLimitHidden] ?: false,
            checkInDays = p[Keys.checkInDays].orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
            reminderEnabled = p[Keys.reminderEnabled] ?: false,
            reminderMinute = p[Keys.reminderMinute] ?: (21 * 60),
            weeklySummaryEnabled = p[Keys.weeklyEnabled] ?: false,
            lockEnabled = p[Keys.lockEnabled] ?: false,
            themeMode = p[Keys.themeMode] ?: 0,
        )
    }

    suspend fun saveName(name: String) {
        context.dataStore.edit { it[Keys.name] = name.trim() }
    }

    /** Setting a goal means the user wants the daily limit, so it also brings the card back if it was removed. */
    suspend fun saveGoal(incomePaise: Long, goalPaise: Long, month: Int) {
        context.dataStore.edit {
            it[Keys.income] = incomePaise
            it[Keys.goal] = goalPaise
            it[Keys.goalMonth] = month
            it[Keys.dailyLimitHidden] = false
        }
    }

    /** Back to "no goal set": the setup card on Home then starts empty. */
    suspend fun resetGoal() {
        context.dataStore.edit {
            it[Keys.income] = 0
            it[Keys.goal] = 0
            it[Keys.goalMonth] = 0
        }
    }

    suspend fun setDailyLimitHidden(hidden: Boolean) {
        context.dataStore.edit { it[Keys.dailyLimitHidden] = hidden }
    }

    /** Marks [epochDay] as a day with nothing to log. Only recent days are kept: older ones can't affect a streak. */
    suspend fun checkIn(epochDay: Long) {
        context.dataStore.edit { p ->
            val kept = p[Keys.checkInDays].orEmpty().mapNotNull { it.toLongOrNull() }.filter { it > epochDay - CHECK_IN_KEEP_DAYS }
            p[Keys.checkInDays] = (kept + epochDay).map { it.toString() }.toSet()
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

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[Keys.themeMode] = mode.coerceIn(0, 2) }
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
            it[Keys.dailyLimitHidden] = s.dailyLimitHidden
            it[Keys.checkInDays] = s.checkInDays.map { d -> d.toString() }.toSet()
        }
    }

    private companion object {
        /** Check-ins older than this can't be part of a streak anyone is still on. */
        const val CHECK_IN_KEEP_DAYS = 800L
    }
}

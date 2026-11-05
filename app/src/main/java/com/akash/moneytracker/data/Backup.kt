package com.akash.moneytracker.data

import com.akash.moneytracker.domain.Tags
import com.akash.moneytracker.domain.timeLabel
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate

/** Everything the app stores, as one value that can be written to or read from a backup file. */
data class DataSnapshot(
    val categories: List<CategoryEntity>,
    val transactions: List<TransactionEntity>,
    val budgets: List<BudgetEntity>,
    val recurring: List<RecurringEntity>,
    val settings: Settings,
)

class InvalidBackupException(message: String) : Exception(message)

/** Hard limits applied to anything read from a file, so a crafted backup can't exhaust memory or smuggle in odd values. */
object BackupLimits {
    const val MAX_BYTES = 20 * 1024 * 1024
    const val MAX_TRANSACTIONS = 200_000
    const val MAX_CATEGORIES = 500
    const val MAX_BUDGETS = 500
    const val MAX_RECURRING = 500
    const val MAX_CHECK_INS = 5_000

    /** ₹1,00,000 crore expressed in paise; far above any real balance, far below Long overflow when summed. */
    const val MAX_PAISE = 100_000_000_000_000L
    val EPOCH_DAYS = -25_000L..250_000L // roughly the years 1901 to 2654
    const val MAX_NOTE = 200
    const val MAX_NAME = 40
    const val MAX_EMOJI = 16
    const val MAX_TAGS_TEXT = 120
}

/** JSON backup/restore and CSV export. org.json ships with Android, so no extra library is needed. */
object Backup {
    private const val APP = "MoneyTracker"
    private const val VERSION = 1

    fun toJson(s: DataSnapshot, exportedAt: Long = System.currentTimeMillis()): String =
        JSONObject()
            .put("app", APP)
            .put("version", VERSION)
            .put("exportedAt", exportedAt)
            .put(
                "settings",
                JSONObject()
                    .put("name", s.settings.name ?: JSONObject.NULL)
                    .put("expectedIncomePaise", s.settings.expectedIncomePaise)
                    .put("savingsGoalPaise", s.settings.savingsGoalPaise)
                    .put("goalMonth", s.settings.goalMonth)
                    .put("dailyLimitHidden", s.settings.dailyLimitHidden)
                    .put("checkInDays", JSONArray(s.settings.checkInDays.sorted())),
            )
            .put("categories", JSONArray(s.categories.map {
                JSONObject().put("id", it.id).put("name", it.name).put("emoji", it.emoji).put("color", it.color)
                    .put("type", it.type.name).put("sortOrder", it.sortOrder).put("archived", it.archived)
            }))
            .put("transactions", JSONArray(s.transactions.map {
                JSONObject().put("id", it.id).put("amountPaise", it.amountPaise).put("type", it.type.name)
                    .put("categoryId", it.categoryId).put("note", it.note).put("epochDay", it.epochDay)
                    .put("minuteOfDay", it.minuteOfDay).put("createdAt", it.createdAt)
                    .put("recurringId", it.recurringId ?: JSONObject.NULL).put("tags", it.tags)
            }))
            .put("budgets", JSONArray(s.budgets.map {
                JSONObject().put("categoryId", it.categoryId).put("limitPaise", it.limitPaise)
            }))
            .put("recurring", JSONArray(s.recurring.map {
                JSONObject().put("id", it.id).put("amountPaise", it.amountPaise).put("type", it.type.name)
                    .put("categoryId", it.categoryId).put("note", it.note).put("dayOfMonth", it.dayOfMonth)
                    .put("minuteOfDay", it.minuteOfDay).put("lastGeneratedMonth", it.lastGeneratedMonth)
            }))
            .toString(2)

    /**
     * Parses and validates a backup; throws [InvalidBackupException] if it isn't one of ours, is inconsistent,
     * or contains values outside what the app could have produced. A backup is untrusted input.
     */
    fun fromJson(text: String): DataSnapshot {
        if (text.length > BackupLimits.MAX_BYTES) throw InvalidBackupException("This backup is too large.")
        try {
            val root = JSONObject(text)
            if (root.optString("app") != APP) throw InvalidBackupException("This isn't a Money Tracker backup.")
            if (root.optInt("version", 0) !in 1..VERSION) throw InvalidBackupException("This backup was made by a newer version of the app.")

            val st = root.getJSONObject("settings")
            val settings = Settings(
                expectedIncomePaise = st.optLong("expectedIncomePaise").also { bad(it !in 0..BackupLimits.MAX_PAISE, "income") },
                savingsGoalPaise = st.optLong("savingsGoalPaise").also { bad(it !in 0..BackupLimits.MAX_PAISE, "savings goal") },
                goalMonth = st.optInt("goalMonth").also { bad(it != 0 && !validMonth(it), "goal month") },
                name = if (st.isNull("name")) null else st.getString("name").clean(60),
                // Both are absent in backups made before they existed.
                dailyLimitHidden = st.optBoolean("dailyLimitHidden"),
                checkInDays = st.optJSONArray("checkInDays")?.let { days ->
                    bad(days.length() > BackupLimits.MAX_CHECK_INS, "no-spend days")
                    List(days.length()) { days.getLong(it) }
                        .also { all -> bad(all.any { d -> d !in BackupLimits.EPOCH_DAYS }, "no-spend day") }
                        .toSet()
                } ?: emptySet(),
            )

            val categoryArray = root.getJSONArray("categories").also { bad(it.length() > BackupLimits.MAX_CATEGORIES, "categories") }
            val categories = categoryArray.objects().map {
                CategoryEntity(
                    id = it.getLong("id"),
                    name = it.getString("name").clean(BackupLimits.MAX_NAME).also { n -> bad(n.isBlank(), "category name") },
                    emoji = it.getString("emoji").clean(BackupLimits.MAX_EMOJI).also { e -> bad(e.isBlank(), "category icon") },
                    color = it.getLong("color").also { c -> bad(c !in 0..0xFFFFFFFFL, "category color") },
                    type = TxType.valueOf(it.getString("type")),
                    sortOrder = it.optInt("sortOrder").also { o -> bad(o !in 0..100_000, "category order") },
                    archived = it.optBoolean("archived"),
                )
            }
            bad(categories.any { it.id <= 0 } || categories.map { it.id }.toSet().size != categories.size, "category ids")
            val categoryById = categories.associateBy { it.id }

            val transactionArray = root.getJSONArray("transactions").also { bad(it.length() > BackupLimits.MAX_TRANSACTIONS, "transactions") }
            val transactions = transactionArray.objects().map {
                val tx = TransactionEntity(
                    id = it.getLong("id"),
                    amountPaise = it.getLong("amountPaise"),
                    type = TxType.valueOf(it.getString("type")),
                    categoryId = it.getLong("categoryId"),
                    note = it.optString("note").clean(BackupLimits.MAX_NOTE),
                    epochDay = it.getLong("epochDay"),
                    minuteOfDay = it.optInt("minuteOfDay"),
                    createdAt = it.optLong("createdAt"),
                    recurringId = if (it.isNull("recurringId")) null else it.getLong("recurringId"),
                    tags = it.optString("tags"), // absent in backups made before tags existed
                )
                bad(tx.id <= 0 || tx.amountPaise !in 1..BackupLimits.MAX_PAISE, "transaction amount")
                bad(tx.epochDay !in BackupLimits.EPOCH_DAYS || tx.minuteOfDay !in 0..1439 || tx.createdAt < 0, "transaction date")
                bad(tx.recurringId != null && tx.recurringId <= 0, "transaction link")
                bad(!validTags(tx.tags), "transaction tags")
                bad(categoryById[tx.categoryId]?.type != tx.type, "transaction category")
                tx
            }
            bad(transactions.map { it.id }.toSet().size != transactions.size, "transaction ids")

            val budgets = root.getJSONArray("budgets").also { bad(it.length() > BackupLimits.MAX_BUDGETS, "budgets") }.objects().map {
                BudgetEntity(categoryId = it.getLong("categoryId"), limitPaise = it.getLong("limitPaise"))
            }
            bad(budgets.any { it.limitPaise !in 1..BackupLimits.MAX_PAISE || categoryById[it.categoryId]?.type != TxType.EXPENSE }, "budgets")
            bad(budgets.map { it.categoryId }.toSet().size != budgets.size, "budget categories")

            val recurring = root.getJSONArray("recurring").also { bad(it.length() > BackupLimits.MAX_RECURRING, "recurring") }.objects().map {
                RecurringEntity(
                    id = it.getLong("id"),
                    amountPaise = it.getLong("amountPaise"),
                    type = TxType.valueOf(it.getString("type")),
                    categoryId = it.getLong("categoryId"),
                    note = it.optString("note").clean(BackupLimits.MAX_NOTE),
                    dayOfMonth = it.getInt("dayOfMonth"),
                    minuteOfDay = it.optInt("minuteOfDay", 9 * 60),
                    lastGeneratedMonth = it.getInt("lastGeneratedMonth"),
                )
            }
            bad(
                recurring.any {
                    it.id <= 0 || it.amountPaise !in 1..BackupLimits.MAX_PAISE || it.dayOfMonth !in 1..31 ||
                        it.minuteOfDay !in 0..1439 || !validMonth(it.lastGeneratedMonth) || categoryById[it.categoryId]?.type != it.type
                } || recurring.map { it.id }.toSet().size != recurring.size,
                "recurring entries",
            )
            return DataSnapshot(categories, transactions, budgets, recurring, settings)
        } catch (e: JSONException) {
            throw InvalidBackupException("This file isn't a readable Money Tracker backup.")
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This backup contains values the app doesn't understand.")
        }
    }

    /**
     * One row per transaction, for opening in a spreadsheet. Text cells that start with = + - @ are prefixed with
     * an apostrophe: spreadsheets would otherwise run them as formulas, and a note can come from a text message.
     */
    fun toCsv(rows: List<TransactionWithCategory>): String {
        fun cell(v: String): String {
            val safe = if (v.isNotEmpty() && v[0] in "=+-@\t\r") "'$v" else v
            return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
        }
        val sb = StringBuilder("Date,Time,Type,Category,Note,Tags,Amount (INR)\n")
        rows.forEach { r ->
            val amount = BigDecimal(r.tx.amountPaise).movePointLeft(2).setScale(2).toPlainString()
            sb.append(LocalDate.ofEpochDay(r.tx.epochDay)).append(',')
                .append(timeLabel(r.tx.minuteOfDay)).append(',')
                .append(r.tx.type.name.lowercase()).append(',')
                .append(cell(r.category.name)).append(',')
                .append(cell(r.tx.note)).append(',')
                .append(cell(r.tx.tags.replace(",", " "))).append(',')
                .append(amount).append('\n')
        }
        return sb.toString()
    }

    private fun bad(condition: Boolean, what: String) {
        if (condition) throw InvalidBackupException("This backup has an invalid $what.")
    }

    private fun validMonth(key: Int): Boolean = key / 100 in 1900..2999 && key % 100 in 1..12

    private fun validTags(stored: String): Boolean {
        if (stored.length > BackupLimits.MAX_TAGS_TEXT) return false
        val tags = Tags.decode(stored)
        return tags.size <= Tags.MAX_TAGS && tags.all { it.matches(Regex("[a-z0-9_-]{1,20}")) }
    }

    /** Drops control characters and text-direction overrides (which can make a note display as something it isn't). */
    internal fun String.clean(max: Int): String =
        filter { c -> !c.isISOControl() && c.code !in 0x202A..0x202E && c.code !in 0x2066..0x2069 && c.code != 0x200E && c.code != 0x200F }
            .take(max)

    private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
}

package dev.wizishan.stonks.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

enum class RecurringType { EXPENSE, INCOME }

enum class RecurringFrequency { DAILY, WEEKLY, MONTHLY }

/**
 * A standing instruction to create an expense or income entry on a schedule — rent, a
 * subscription, a salary.
 *
 * The rule is not itself a transaction. It generates rows in `expenses` or `income`, and
 * those rows carry `recurringRuleId` so their provenance is visible.
 *
 * [nextDueDate] is stored rather than derived so a rule that has been paused, or an app
 * that has not been opened for a month, still knows exactly where it left off.
 */
@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Trip::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("tripId"), Index("nextDueDate")],
)
data class RecurringRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: RecurringType,
    val amountMinor: Long,
    /** Set when [type] is EXPENSE. */
    val categoryId: Long? = null,
    /** Set when [type] is INCOME. */
    val source: String? = null,
    val tripId: Long? = null,
    val frequency: RecurringFrequency,
    val startDate: LocalDate,
    val nextDueDate: LocalDate,
    val note: String? = null,
    /** Pausing keeps the rule and everything it has already generated. */
    val isActive: Boolean = true,
)

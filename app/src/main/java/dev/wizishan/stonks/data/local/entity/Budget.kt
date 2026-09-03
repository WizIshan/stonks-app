package dev.wizishan.stonks.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A monthly spending limit, either for one category or for everything.
 *
 * A null [categoryId] means the overall budget. There is at most one budget per category
 * and at most one overall, enforced by the unique index — two limits on the same thing
 * would leave the app with no answer for which one an alert refers to.
 */
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId"], unique = true)],
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Null is the overall monthly budget. */
    val categoryId: Long? = null,
    val monthlyLimitMinor: Long,
    /** Spend crossing this share of the limit fires the first alert. */
    val alertThresholdPercent: Int = DEFAULT_THRESHOLD_PERCENT,
    /**
     * The month (`YYYY-MM`) each alert was last sent for.
     *
     * Without these the daily check would re-notify every single day once a budget was
     * over. They are per-month so a new month starts quiet again, and separate so crossing
     * 100% still notifies someone who was already warned at 80%.
     */
    val notifiedThresholdMonth: String? = null,
    val notifiedOverMonth: String? = null,
) {
    val isOverall: Boolean get() = categoryId == null

    companion object {
        const val DEFAULT_THRESHOLD_PERCENT = 80
    }
}

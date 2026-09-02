package dev.wizishan.stonks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * A cash inflow, tracked separately from expenses so the dashboard can show net cash
 * flow rather than spend alone. [amountMinor] is a count of minor units.
 *
 * [source] is free text ("Salary", "Freelance") rather than a foreign key: income sources
 * are few, rarely reused, and never colour-coded, so a lookup table would be overhead
 * with no payoff. The DAO exposes the distinct sources for autocomplete.
 */
@Entity(
    tableName = "income",
    indices = [Index("date")],
)
data class Income(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountMinor: Long,
    val date: LocalDate,
    val source: String,
    val note: String? = null,
    /** See the note on [Expense.recurringRuleId]. */
    val recurringRuleId: Long? = null,
)

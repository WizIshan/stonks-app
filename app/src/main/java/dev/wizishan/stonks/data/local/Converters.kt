package dev.wizishan.stonks.data.local

import androidx.room.TypeConverter
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.RecurringType
import java.time.LocalDate

/**
 * Dates are stored as ISO-8601 text (`2026-09-02`), not as an epoch day number.
 *
 * The text form sorts lexicographically in exactly chronological order, so `ORDER BY date`
 * and `date BETWEEN ? AND ?` work directly; `substr(date, 1, 7)` gives the year-month for
 * grouping without any date arithmetic; and the value is readable in the Database
 * Inspector and in an exported backup, which matters for a format meant to stay stable
 * across versions.
 */
class Converters {

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    // Enums are stored by name, not ordinal: an ordinal silently changes meaning if a
    // constant is ever inserted in the middle, and a name does not.
    @TypeConverter
    fun toRecurringType(value: String?): RecurringType? = value?.let(RecurringType::valueOf)

    @TypeConverter
    fun fromRecurringType(type: RecurringType?): String? = type?.name

    @TypeConverter
    fun toRecurringFrequency(value: String?): RecurringFrequency? =
        value?.let(RecurringFrequency::valueOf)

    @TypeConverter
    fun fromRecurringFrequency(frequency: RecurringFrequency?): String? = frequency?.name
}

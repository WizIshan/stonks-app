package dev.wizishan.stonks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A user-created trip tag, e.g. "Japan 2026", that expenses can be grouped under. */
@Entity(
    tableName = "trips",
    indices = [Index(value = ["name"], unique = true)],
)
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
)

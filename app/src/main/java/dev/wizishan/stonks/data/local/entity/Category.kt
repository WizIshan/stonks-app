package dev.wizishan.stonks.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A spend category. [colorHex] holds the canonical light-mode hex of one
 * [dev.wizishan.stonks.core.CategorySlot] — the colour belongs to this row, so it stays
 * put however the user later sorts or filters (DESIGN.md §3b).
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorHex: String,
)

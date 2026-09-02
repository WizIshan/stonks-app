package dev.wizishan.stonks.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.data.local.dao.CategoryDao
import dev.wizishan.stonks.data.local.dao.ExpenseDao
import dev.wizishan.stonks.data.local.dao.IncomeDao
import dev.wizishan.stonks.data.local.dao.TripDao
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Expense
import dev.wizishan.stonks.data.local.entity.Income
import dev.wizishan.stonks.data.local.entity.Trip

@Database(
    entities = [Category::class, Trip::class, Expense::class, Income::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StonksDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun tripDao(): TripDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao

    companion object {
        const val NAME = "stonks.db"

        /**
         * Seeds the eight default categories, in palette slot order, the first time the
         * database is created.
         *
         * Raw SQL rather than the DAO because `onCreate` runs inside Room's own
         * transaction on the schema it has just built — going back through the DAO here
         * would re-enter the database that is still being opened.
         */
        val SeedCallback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CategorySlots.all.forEach { slot ->
                    db.execSQL(
                        "INSERT INTO categories (name, colorHex) VALUES (?, ?)",
                        arrayOf(slot.defaultCategoryName, slot.lightHex),
                    )
                }
            }
        }

        fun build(context: Context): StonksDatabase =
            Room.databaseBuilder(context.applicationContext, StonksDatabase::class.java, NAME)
                .addCallback(SeedCallback)
                .build()
    }
}

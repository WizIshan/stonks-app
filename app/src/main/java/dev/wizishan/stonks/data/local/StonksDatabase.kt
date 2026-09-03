package dev.wizishan.stonks.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.wizishan.stonks.core.CategorySlots
import dev.wizishan.stonks.data.local.dao.CategoryDao
import dev.wizishan.stonks.data.local.dao.ExpenseDao
import dev.wizishan.stonks.data.local.dao.IncomeDao
import dev.wizishan.stonks.data.local.dao.RecurringRuleDao
import dev.wizishan.stonks.data.local.dao.TripDao
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Expense
import dev.wizishan.stonks.data.local.entity.Income
import dev.wizishan.stonks.data.local.entity.RecurringRule
import dev.wizishan.stonks.data.local.entity.Trip

@Database(
    entities = [Category::class, Trip::class, Expense::class, Income::class, RecurringRule::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class StonksDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun tripDao(): TripDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun incomeDao(): IncomeDao
    abstract fun recurringRuleDao(): RecurringRuleDao

    companion object {
        const val NAME = "stonks.db"

        /**
         * Adds `recurring_rules`.
         *
         * The DDL is copied verbatim from the generated `schemas/2.json`, because Room
         * validates the live schema against that file on open and rejects anything that
         * differs — even in ways SQLite would accept.
         *
         * `expenses.recurringRuleId` and `income.recurringRuleId` have existed since
         * version 1 but deliberately gain no foreign key here. Adding one means recreating
         * both tables and copying their rows, and the column only records provenance —
         * nothing reads it to join. Until the app can export a backup (milestone 8) there
         * is no way back from a migration that goes wrong, and that is not a trade worth
         * making for a constraint with no query behind it.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recurring_rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`type` TEXT NOT NULL, " +
                        "`amountMinor` INTEGER NOT NULL, " +
                        "`categoryId` INTEGER, " +
                        "`source` TEXT, " +
                        "`tripId` INTEGER, " +
                        "`frequency` TEXT NOT NULL, " +
                        "`startDate` TEXT NOT NULL, " +
                        "`nextDueDate` TEXT NOT NULL, " +
                        "`note` TEXT, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE RESTRICT , " +
                        "FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE SET NULL )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_categoryId` ON `recurring_rules` (`categoryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_tripId` ON `recurring_rules` (`tripId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_nextDueDate` ON `recurring_rules` (`nextDueDate`)")
            }
        }

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
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

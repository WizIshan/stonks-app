package dev.wizishan.stonks.data.local

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wizishan.stonks.data.local.entity.RecurringFrequency
import dev.wizishan.stonks.data.local.entity.RecurringRule
import dev.wizishan.stonks.data.local.entity.RecurringType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.Executor

/**
 * Exercises the real 1 → 2 upgrade against a hand-built version 1 database.
 *
 * This matters more than most tests here: the app is already installed with real data and
 * has no export yet, so a migration that fails takes someone's records with it. Room
 * validates the post-migration schema against `schemas/2.json` when it opens, which is
 * what makes "the database opened" a meaningful assertion rather than a smoke test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK], application = Application::class)
class MigrationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath("migration-test.db")
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
    }

    @After
    fun tearDown() {
        dbFile.delete()
    }

    /** Version 1 exactly as `schemas/1.json` describes it, including Room's own bookkeeping. */
    private fun createVersion1Database() {
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        listOf(
            "CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `colorHex` TEXT NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_categories_name` ON `categories` (`name`)",
            "CREATE TABLE IF NOT EXISTS `trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `startDate` TEXT, `endDate` TEXT)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_trips_name` ON `trips` (`name`)",
            "CREATE TABLE IF NOT EXISTS `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountMinor` INTEGER NOT NULL, `date` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, `tripId` INTEGER, `note` TEXT, `recurringRuleId` INTEGER, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT , FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            "CREATE INDEX IF NOT EXISTS `index_expenses_categoryId` ON `expenses` (`categoryId`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_tripId` ON `expenses` (`tripId`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_date` ON `expenses` (`date`)",
            "CREATE TABLE IF NOT EXISTS `income` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountMinor` INTEGER NOT NULL, `date` TEXT NOT NULL, `source` TEXT NOT NULL, `note` TEXT, `recurringRuleId` INTEGER)",
            "CREATE INDEX IF NOT EXISTS `index_income_date` ON `income` (`date`)",
            // Room refuses to open a database whose identity hash it cannot recognise.
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, '$VERSION_1_HASH')",
            // The user's data, which the migration must not touch.
            "INSERT INTO categories (id, name, colorHex) VALUES (1, 'Food & Drink', '#2A78D6')",
            "INSERT INTO expenses (amountMinor, date, categoryId) VALUES (4250, '2026-09-01', 1)",
            "INSERT INTO income (amountMinor, date, source) VALUES (250000, '2026-09-01', 'Salary')",
        ).forEach(db::execSQL)
        db.version = 1
        db.close()
    }

    private fun openVersion2(): StonksDatabase {
        val direct = Executor(Runnable::run)
        return Room.databaseBuilder(context, StonksDatabase::class.java, dbFile.absolutePath)
            .addMigrations(StonksDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .setQueryExecutor(direct)
            .setTransactionExecutor(direct)
            .build()
    }

    @Test
    fun `version 1 data survives the upgrade`() = runTest {
        createVersion1Database()

        val db = openVersion2()
        try {
            assertEquals("Food & Drink", db.categoryDao().getByName("Food & Drink")?.name)
            assertEquals(4250L, db.expenseDao().getById(1)?.amountMinor)
            assertEquals("Salary", db.incomeDao().getById(1)?.source)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the new table is usable straight after the upgrade`() = runTest {
        createVersion1Database()

        val db = openVersion2()
        try {
            val id = db.recurringRuleDao().insert(
                RecurringRule(
                    type = RecurringType.EXPENSE,
                    amountMinor = 95_000,
                    categoryId = 1,
                    frequency = RecurringFrequency.MONTHLY,
                    startDate = java.time.LocalDate.parse("2026-09-01"),
                    nextDueDate = java.time.LocalDate.parse("2026-09-01"),
                )
            )
            assertNotNull(db.recurringRuleDao().getById(id))
            assertEquals(1, db.recurringRuleDao().observeAll().first().size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `the seed callback does not re-run on an upgrade`() = runTest {
        createVersion1Database()

        val db = openVersion2()
        try {
            // onCreate fires for a new database, not an upgraded one. If it ran here the
            // eight defaults would land on top of the user's existing categories.
            assertEquals(1, db.categoryDao().count())
        } finally {
            db.close()
        }
    }

    private companion object {
        /** From `schemas/1.json`. */
        const val VERSION_1_HASH = "5fd1ec58d8413f86ea4acb49e84b7ddb"
    }
}

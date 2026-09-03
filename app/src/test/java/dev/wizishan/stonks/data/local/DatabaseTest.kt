package dev.wizishan.stonks.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.wizishan.stonks.data.local.entity.Category
import dev.wizishan.stonks.data.local.entity.Expense
import dev.wizishan.stonks.data.local.entity.Income
import dev.wizishan.stonks.data.local.entity.Trip
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.concurrent.Executor

/**
 * The Android API level these tests run against.
 *
 * Robolectric ships a fixed set of Android runtimes and has none for the app's
 * `compileSdk`, so it must be pinned to the newest level Robolectric actually has. This is
 * only about which android.jar the JVM tests load — none of the SQLite behaviour these
 * tests exercise differs by API level. Raise it when Robolectric adds a newer runtime.
 */
const val ROBOLECTRIC_SDK = 35

/**
 * Base for DAO tests: a fresh in-memory database per test, seeded exactly as a real
 * install would be.
 *
 * These run on the JVM under Robolectric rather than on a device, so the whole suite is a
 * few seconds and needs no emulator — which is the point of testing the data layer before
 * any UI exists.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [ROBOLECTRIC_SDK])
abstract class DatabaseTest {

    protected lateinit var db: StonksDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Run Room on the calling thread. By default queries and invalidation callbacks
        // hop to a background executor, so a DAO write and the Flow emission it triggers
        // land after the test has already asserted. A direct executor makes both
        // synchronous, which is what lets these tests read a value straight back.
        val directExecutor = Executor(Runnable::run)

        db = Room.inMemoryDatabaseBuilder(context, StonksDatabase::class.java)
            .addCallback(StonksDatabase.SeedCallback)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    // ---- fixtures ----------------------------------------------------------------

    protected suspend fun category(name: String, hex: String = "#2A78D6"): Long =
        db.categoryDao().insert(Category(name = name, colorHex = hex))

    protected suspend fun trip(
        name: String,
        start: LocalDate? = null,
        end: LocalDate? = null,
    ): Long = db.tripDao().insert(Trip(name = name, startDate = start, endDate = end))

    protected suspend fun expense(
        amountMinor: Long,
        date: String,
        categoryId: Long,
        tripId: Long? = null,
        note: String? = null,
    ): Long = db.expenseDao().insert(
        Expense(
            amountMinor = amountMinor,
            date = LocalDate.parse(date),
            categoryId = categoryId,
            tripId = tripId,
            note = note,
        )
    )

    protected suspend fun income(
        amountMinor: Long,
        date: String,
        source: String,
    ): Long = db.incomeDao().insert(
        Income(amountMinor = amountMinor, date = LocalDate.parse(date), source = source)
    )

    /** The id of a seeded default category, by name. */
    protected suspend fun seededCategoryId(name: String): Long =
        requireNotNull(db.categoryDao().getByName(name)) { "no seeded category named $name" }.id
}

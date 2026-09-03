package dev.wizishan.stonks.data.local

import android.database.sqlite.SQLiteConstraintException
import dev.wizishan.stonks.data.local.entity.Trip
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TripDaoTest : DatabaseTest() {

    private val dao get() = db.tripDao()

    @Test
    fun `round-trips nullable dates`() = runTest {
        val id = trip("Japan 2026", start = LocalDate.parse("2026-04-01"), end = LocalDate.parse("2026-04-14"))
        val undated = trip("Someday")

        assertEquals(LocalDate.parse("2026-04-01"), dao.getById(id)?.startDate)
        assertEquals(LocalDate.parse("2026-04-14"), dao.getById(id)?.endDate)
        assertNull(dao.getById(undated)?.startDate)
    }

    @Test
    fun `most recent trips come first and undated trips lead`() = runTest {
        trip("Japan 2026", start = LocalDate.parse("2026-04-01"))
        trip("Lisbon 2025", start = LocalDate.parse("2025-06-10"))
        trip("Someday")

        assertEquals(
            listOf("Someday", "Japan 2026", "Lisbon 2025"),
            dao.observeAll().first().map { it.name },
        )
    }

    @Test(expected = SQLiteConstraintException::class)
    fun `duplicate trip names are rejected`() = runTest {
        trip("Japan 2026")
        dao.insert(Trip(name = "Japan 2026"))
    }
}

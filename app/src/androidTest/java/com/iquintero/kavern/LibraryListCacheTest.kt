package com.iquintero.kavern

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.iquintero.kavern.data.local.db.KavernDatabase
import com.iquintero.kavern.data.local.db.entity.CachedSeriesListRefEntity
import com.iquintero.kavern.data.local.db.entity.CachedSeriesV2Entity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryListCacheTest {
    private fun series(id: Int) =
        CachedSeriesV2Entity(
            id = id,
            name = "Series $id",
            summary = null,
            libraryId = null,
            format = null,
            pages = null,
            pagesRead = null,
            readState = null,
            minHoursToRead = null,
            maxHoursToRead = null,
            avgHoursToRead = null,
            localThumbPath = null,
            updatedAt = 1L,
        )

    private fun ref(id: Int) = CachedSeriesListRefEntity(listType = "all", listKey = "", seriesId = id, position = id, updatedAt = 1L)

    private fun withDatabase(block: suspend (KavernDatabase) -> Unit) =
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val database = Room.inMemoryDatabaseBuilder(context, KavernDatabase::class.java).build()
            try {
                block(database)
            } finally {
                database.close()
            }
        }

    @Test
    fun replacingAListSwapsItsContents() =
        withDatabase { database ->
            val dao = database.libraryV2Dao()
            dao.replaceSeriesList("all", "", listOf(series(1), series(2)), listOf(ref(1), ref(2)))
            dao.replaceSeriesList("all", "", listOf(series(3)), listOf(ref(3)))

            assertEquals(listOf(3), dao.getSeriesForList("all", "").map { it.id })
        }

    @Test
    fun aFailureWhileReplacingLeavesThePreviousListInPlace() =
        withDatabase { database ->
            val dao = database.libraryV2Dao()
            dao.replaceSeriesList("all", "", listOf(series(1), series(2)), listOf(ref(1), ref(2)))
            // Make the last step of the replacement fail, after the old references were already cleared.
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_refs BEFORE INSERT ON cached_series_list_refs_v2 BEGIN SELECT RAISE(ABORT, 'boom'); END",
            )

            val failed = runCatching { dao.replaceSeriesList("all", "", listOf(series(3)), listOf(ref(3))) }.isFailure

            assertTrue(failed)
            assertEquals(listOf(1, 2), dao.getSeriesForList("all", "").map { it.id })
        }
}

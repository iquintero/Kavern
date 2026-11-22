package com.iquintero.kavern.ui.home

import com.iquintero.kavern.domain.model.Series
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeSectionsTest {
    private fun series(
        id: Int,
        pagesRead: Int = 0,
    ) = Series(
        id = id,
        name = "Series $id",
        summary = null,
        libraryId = 1,
        format = null,
        pages = 100,
        pagesRead = pagesRead,
        readState = null,
    )

    @Test
    fun theFirstStartedSeriesIsTheOneToResume() {
        val sections = buildHomeSections(onDeck = listOf(series(1, 10), series(2, 20)), wantToRead = emptyList(), recentlyAdded = emptyList())

        assertEquals(1, sections.resume?.id)
        assertEquals(listOf(2), sections.inProgress.map { it.id })
    }

    @Test
    fun aSavedSeriesThatWasStartedMovesToInProgress() {
        val sections =
            buildHomeSections(
                onDeck = listOf(series(1, 10)),
                wantToRead = listOf(series(2, 30), series(3, 0)),
                recentlyAdded = emptyList(),
            )

        assertEquals(listOf(2), sections.inProgress.map { it.id })
        assertEquals(listOf(3), sections.wantToRead.map { it.id })
    }

    @Test
    fun aSeriesAppearsOnlyOnceAcrossSections() {
        val sections =
            buildHomeSections(
                onDeck = listOf(series(1, 10)),
                wantToRead = listOf(series(1, 10), series(2, 0)),
                recentlyAdded = listOf(series(1, 10), series(2, 0), series(3, 0)),
            )

        assertEquals(1, sections.resume?.id)
        assertEquals(listOf(2), sections.wantToRead.map { it.id })
        assertEquals(listOf(3), sections.recentlyAdded.map { it.id })
    }

    @Test
    fun nothingStartedLeavesNoSeriesToResume() {
        val sections = buildHomeSections(emptyList(), listOf(series(1, 0)), emptyList())

        assertNull(sections.resume)
        assertEquals(listOf(1), sections.wantToRead.map { it.id })
    }
}

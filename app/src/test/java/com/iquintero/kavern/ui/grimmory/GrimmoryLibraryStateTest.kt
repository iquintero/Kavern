package com.iquintero.kavern.ui.grimmory

import com.iquintero.kavern.core.network.grimmory.GrimmoryBook
import org.junit.Assert.assertEquals
import org.junit.Test

class GrimmoryLibraryStateTest {
    private fun book(
        id: Int,
        series: String?,
        number: Float?,
        progress: Float,
    ) = GrimmoryBook(id, "Book $id", emptyList(), series, number, 1, null, null, progress, "EPUB")

    private val library =
        listOf(
            book(1, "Saga", 1f, 19f),
            book(2, "Saga", 2f, 25f),
            book(3, "Saga", 3f, 0f),
            book(4, null, null, 0f),
        )

    @Test
    fun `a series that is filtered says how many of its books are shown`() {
        val state = GrimmoryLibraryState(isLoading = false, books = library, status = BookStatus.Reading)
        val saga = state.groups.single()
        assertEquals(2, saga.books.size)
        assertEquals("the series has three books in all", 3, saga.total)
    }

    @Test
    fun `an unfiltered series shows all its books and no partial count`() {
        val saga = GrimmoryLibraryState(isLoading = false, books = library).groups.first { it.series == "Saga" }
        assertEquals(3, saga.books.size)
        assertEquals(saga.books.size, saga.total)
    }

    @Test
    fun `a book without a series stays a book of its own`() {
        val alone = GrimmoryLibraryState(isLoading = false, books = library).groups.first { it.series == null }
        assertEquals(1, alone.total)
    }
}

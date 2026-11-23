package com.iquintero.kavern.domain.usecase

import com.iquintero.kavern.domain.model.Format
import com.iquintero.kavern.domain.model.filter.KavitaField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseFilterBuilderTest {
    private fun formatIds(filter: AppliedFilter): Set<String> =
        buildQueries(filter, search = "", page = 1, pageSize = 25)
            .map { query -> query.clauses.single { it.field == KavitaField.Formats }.value }
            .toSet()

    @Test
    fun browseCoversBooksPdfsAndComics() {
        val expected = setOf(Format.Epub, Format.Pdf, Format.Archive, Format.Image).map { it.id.toString() }.toSet()
        assertEquals(expected, formatIds(AppliedFilter()))
    }

    @Test
    fun comicsAreIncludedUnderEveryKindOfFilter() {
        val comicFormats = setOf(Format.Archive.id.toString(), Format.Image.id.toString())
        assertTrue(formatIds(AppliedFilter(libraries = mapOf(2 to com.iquintero.kavern.domain.model.filter.TriState.Include))).containsAll(comicFormats))
        assertTrue(formatIds(AppliedFilter(special = SpecialFilter.WantToRead)).containsAll(comicFormats))
    }

    @Test
    fun oneQueryPerFormat() {
        assertEquals(BROWSE_FORMATS.size, buildQueries(AppliedFilter(), "", 1, 25).size)
    }
}

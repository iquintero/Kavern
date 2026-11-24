package com.iquintero.kavern.data.api.dto

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesDetailPlusDtoTest {
    private val adapter =
        Moshi
            .Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
            .adapter(SeriesDetailPlusDto::class.java)

    /** Inkita issue #75: Kavita 0.9.1 sent an owned recommendation without an id and the series page failed to open. */
    @Test
    fun ownedRecommendationWithoutIdStillParses() {
        val json =
            """{"recommendations":{"ownedSeries":[{"id":5,"name":"Has id"},{"name":"No id"}],"externalSeries":[]}}"""

        val owned = adapter.fromJson(json)!!.recommendations!!.ownedSeries!!

        assertEquals(2, owned.size)
        assertEquals(5, owned[0].id)
        assertNull(owned[1].id)
        assertEquals("No id", owned[1].name)
    }

    @Test
    fun cachedJsonRoundTrips() {
        val json = """{"recommendations":{"ownedSeries":[{"id":7,"name":"A","libraryId":2}]}}"""
        val parsed = adapter.fromJson(json)!!

        assertEquals(parsed, adapter.fromJson(adapter.toJson(parsed)))
    }
}

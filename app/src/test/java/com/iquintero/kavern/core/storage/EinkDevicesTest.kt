package com.iquintero.kavern.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EinkDevicesTest {
    @Test
    fun onyxBooxReadersAreRecognizedWhateverTheCase() {
        assertTrue(isEinkDevice("ONYX", "Onyx"))
        assertTrue(isEinkDevice("onyx", null))
        assertTrue(isEinkDevice("Unknown", "BOOX"))
    }

    @Test
    fun otherEinkMakersAreRecognized() {
        listOf("Bigme", "Meebook", "Likebook", "iReader", "Dasung").forEach {
            assertTrue(it, isEinkDevice(it, null))
        }
    }

    @Test
    fun phonesAndTabletsAreNot() {
        assertFalse(isEinkDevice("samsung", "samsung"))
        assertFalse(isEinkDevice("Google", "google"))
        assertFalse(isEinkDevice("Amazon", "Amazon"))
        assertFalse(isEinkDevice(null, null))
    }

    @Test
    fun aNameThatOnlyContainsAMakerIsNotEnough() {
        assertFalse(isEinkDevice("Onyxia Phones", "onyxia"))
    }
}

package com.iquintero.kavern.core.downloadv2.strategies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class AssetFailureTest {
    @Test
    fun missingAssetsAreSkipped() {
        assertTrue(isPermanentAssetFailure(AssetHttpException(404)))
        assertTrue(isPermanentAssetFailure(AssetHttpException(410)))
    }

    @Test
    fun transientFailuresFailThePageSoItIsRetried() {
        assertFalse(isPermanentAssetFailure(AssetHttpException(500)))
        assertFalse(isPermanentAssetFailure(AssetHttpException(408)))
        assertFalse(isPermanentAssetFailure(AssetHttpException(429)))
        assertFalse(isPermanentAssetFailure(SocketTimeoutException("timeout")))
        assertFalse(isPermanentAssetFailure(IOException("Empty body")))
    }

    @Test
    fun protocolRelativeImagesUseTheServerScheme() {
        assertEquals("http://10.0.2.2:5000/api/book/1/book-resources?file=a.png", absoluteAssetUrl("//10.0.2.2:5000/api/book/1/book-resources?file=a.png", "http://10.0.2.2:5000"))
        assertEquals("https://kavita.example/api/x", absoluteAssetUrl("//kavita.example/api/x", "https://kavita.example"))
    }

    @Test
    fun otherSourcesAreResolvedOrSkipped() {
        assertEquals("https://cdn.example/a.png", absoluteAssetUrl("https://cdn.example/a.png", "http://server"))
        assertEquals("http://server/api/a.png", absoluteAssetUrl("/api/a.png", "http://server/"))
        assertNull(absoluteAssetUrl("images/a.png", "http://server"))
    }
}

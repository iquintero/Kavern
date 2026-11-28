package com.iquintero.kavern.core.network.grimmory

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrimmoryInterceptorTest {
    private fun served(
        path: String,
        method: String = "GET",
    ) = GrimmoryInterceptor.servesToViews(method, path)

    @Test
    fun `covers and the files of a book are served`() {
        assertTrue(served("/api/v1/media/book/8/cover"))
        assertTrue(served("/api/v1/media/book/8/thumbnail"))
        assertTrue(served("/api/v1/epub/9/file/OEBPS/text/chapter1.xhtml"))
        assertTrue(served("/api/v1/epub/9/file/images/cover%20art.jpg"))
    }

    @Test
    fun `the rest of the api is not served to images or book pages`() {
        listOf(
            "/api/v1/app/users/me",
            "/api/v1/app/books",
            "/api/v1/auth/login",
            "/api/v1/reading-sessions",
            "/api/v1/libraries",
            "/api/v1/books/8/download",
            "/api/v1/epub/9/info",
            "/api/v1/epub/9/file/",
            "/api/v1/media/author/3/thumbnail",
            "/api/v1/media/bookdrop/3/cover",
            "/api/v1/media/book/x/cover",
            "/api/v1/media/book/8/cover/extra",
        ).forEach { assertFalse(it, served(it)) }
    }

    @Test
    fun `a path cannot climb out of what is served`() {
        listOf(
            "/api/v1/epub/9/file/../../app/users/me",
            "/api/v1/epub/9/file/%2e%2e/%2E%2E/app/users/me",
            "/api/v1/epub/9/file/./x",
            "/api/v1/epub/9/file/.%2e/x",
        ).forEach { assertFalse(it, served(it)) }
    }

    @Test
    fun `only a plain read is served`() {
        listOf("POST", "PUT", "DELETE", "PATCH").forEach { assertFalse(it, served("/api/v1/media/book/8/cover", it)) }
    }
}

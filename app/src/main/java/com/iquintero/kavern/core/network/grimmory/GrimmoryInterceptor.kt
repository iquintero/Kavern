package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.data.api.dto.ChapterDto
import com.iquintero.kavern.data.api.dto.FilterV2Dto
import com.iquintero.kavern.data.api.dto.GenreTagDto
import com.iquintero.kavern.data.api.dto.LibraryDto
import com.iquintero.kavern.data.api.dto.MangaFileDto
import com.iquintero.kavern.data.api.dto.PersonDto
import com.iquintero.kavern.data.api.dto.RatingDto
import com.iquintero.kavern.data.api.dto.RecentlyAddedItemDto
import com.iquintero.kavern.data.api.dto.SeriesDetailDto
import com.iquintero.kavern.data.api.dto.SeriesDto
import com.iquintero.kavern.data.api.dto.SeriesMetadataDto
import com.iquintero.kavern.data.api.dto.TagDto
import com.iquintero.kavern.data.api.dto.TimeLeftDto
import com.iquintero.kavern.data.api.dto.UserDto
import com.iquintero.kavern.data.api.dto.VolumeDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException

/**
 * Lets the app, which speaks Kavita's API everywhere, work against Grimmory. Every request the app makes to the server
 * passes through here; the ones that Kavita would answer are answered from what Grimmory has, in Kavita's shapes.
 *
 * Grimmory has books, where Kavita has series made of volumes made of chapters. Every book becomes a series of its own
 * with one volume and one chapter, all three carrying the book's id, so that books are what the app lists, downloads
 * and resumes. The books of a Grimmory series are only shown as context on each book's page.
 */
internal class GrimmoryInterceptor(
    server: GrimmoryServer,
) : Interceptor {
    private val client = server.client
    private val account = server.account
    private val catalog = server.catalog
    private val epubs = server.epubs
    private val reader = GrimmoryReader(client, catalog, epubs)
    private val sessions = GrimmorySessions(client, catalog)
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsonType = "application/json".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return try {
            route(request) ?: reply(request, NOT_FOUND, "")
        } catch (e: GrimmoryHttpException) {
            LoggingManager.w("Grimmory", "${request.url.encodedPath}: HTTP ${e.code}")
            reply(request, if (e.code == 401 || e.code == 403 || e.code == 404) e.code else BAD_GATEWAY, "")
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            LoggingManager.e("Grimmory", "${request.url.encodedPath} could not be answered", e)
            throw IOException("Grimmory answer could not be built", e)
        }
    }

    private fun route(request: Request): Response? {
        val path =
            request.url.encodedPath
                .lowercase()
                .trimEnd('/')
        val q = request.url

        fun int(name: String) = q.queryParameter(name)?.toIntOrNull()
        // Kavita numbers its pages from 1, and the app is written for that.
        val page = ((int("PageNumber") ?: 1) - 1).coerceAtLeast(0)
        val size = int("PageSize") ?: DEFAULT_PAGE
        return when {
            path.startsWith("/api/v1/media/") -> media(request, path.removePrefix("/"))
            path == "/api/account" -> json(request, UserDto(id = accountId(), username = account.username, email = null))
            path == "/api/library" || path == "/api/library/libraries" -> json(request, libraries())
            path == "/api/users/has-library-access" -> json(request, true)
            path == "/api/series/v2" || path == "/api/series/all-v2" -> json(request, seriesPage(request, page, size))
            path == "/api/series/recently-added-v2" -> json(request, seriesPage(request, page, size, byAdded = true))
            path == "/api/series/on-deck" -> json(request, onDeck(int("libraryId") ?: 0, page, size))
            path == "/api/series/recently-updated-series" -> json(request, recentlyUpdated(page, size))
            path == "/api/series/series-detail" -> int("seriesId")?.let { json(request, seriesDetail(it)) }
            path == "/api/series/volumes" -> int("seriesId")?.let { json(request, seriesDetail(it).volumes.orEmpty()) }
            path == "/api/series/metadata" -> int("seriesId")?.let { json(request, metadata(it)) }
            path == "/api/volume" -> int("volumeId")?.let { id -> volumeOf(id)?.let { json(request, it) } }
            path.startsWith("/api/series/") && path.count { it == '/' } == 3 ->
                path.substringAfterLast('/').toIntOrNull()?.let { id -> catalog.book(id)?.let { json(request, it.toSeriesDto()) } }
            path == "/api/image/series-cover" -> int("seriesId")?.let { cover(request, it) }
            path == "/api/image/chapter-cover" -> int("chapterId")?.let { cover(request, it) }
            path == "/api/image/volume-cover" -> int("volumeId")?.let { cover(request, it) }
            path == "/api/reader/continue-point" -> int("seriesId")?.let { continuePoint(request, it) }
            path == "/api/reader/time-left" -> json(request, TimeLeftDto())
            path == "/api/reader/has-progress" -> json(request, int("seriesId")?.let { catalog.book(it) }?.let { it.progress > 0 } == true)
            path == "/api/stats/reading-history" -> json(request, sessions.history(q.queryParameter("TimeZoneId")))
            path == "/api/stats/reading-session" -> recordSession(request)
            path == "/api/rating/overall-series" -> json(request, RatingDto())
            path == "/api/want-to-read" -> json(request, false)
            path in EMPTY_LISTS -> json(request, emptyList<Any>())
            else -> reader.answer(request, ::json, ::reply)
        }
    }

    private fun recordSession(request: Request): Response {
        val buffer = Buffer().also { request.body?.writeTo(it) }

        @Suppress("UNCHECKED_CAST")
        val body = GrimmoryJson.parse(buffer.readUtf8()) as? Json ?: return reply(request, BAD_REQUEST, "")
        sessions.record(body)
        return reply(request, OK, "")
    }

    private fun accountId(): Int = (account.username.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    private fun libraries(): List<LibraryDto> =
        catalog.libraries().mapNotNull { library ->
            LibraryDto(
                id = library.int("id") ?: return@mapNotNull null,
                name = library.str("name"),
                type = BOOK_LIBRARY,
                includeInDashboard = true,
                includeInRecommended = true,
                manageCollections = false,
                manageReadingLists = false,
                includeInSearch = true,
                allowScrobbling = false,
                collapseSeriesRelationships = false,
                allowMetadataMatching = false,
                enableMetadata = false,
                removePrefixForSortName = false,
                inheritWebLinksFromFirstChapter = false,
            )
        }

    /** In lists a book is 100 pages long, so the pages read are its percentage; the real page count comes with its chapter. */
    private fun GrimmoryBook.toSeriesDto(): SeriesDto =
        SeriesDto(
            id = id,
            name = title,
            localizedName = title,
            sortName = title,
            pages = GrimmoryBook.UNKNOWN_PAGES,
            pagesRead = progress.toInt(),
            latestReadDate = lastReadTime,
            format = format,
            created = addedOn,
            lastChapterAddedUtc = addedOn,
            libraryId = libraryId,
            totalReads = 0,
        )

    private fun seriesPage(
        request: Request,
        page: Int,
        size: Int,
        byAdded: Boolean = false,
    ): List<SeriesDto> {
        val filter = request.bodyAs<FilterV2Dto>()
        var all = catalog.books()
        filter?.statements.orEmpty().forEach { statement ->
            val value = statement.value.orEmpty()
            when (statement.field) {
                FIELD_LIBRARIES -> {
                    val ids = value.split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()
                    if (ids.isNotEmpty()) all = all.filter { it.libraryId in ids }
                }
                FIELD_SERIES_NAME -> if (value.isNotBlank()) all = all.filter { it.title.contains(value, ignoreCase = true) }
            }
        }
        val ascending = filter?.sortOptions?.isAscending ?: true
        val sorted =
            when {
                byAdded -> all.sortedByDescending { it.addedOn }
                else ->
                    when (filter?.sortOptions?.sortField) {
                        SORT_CREATED, SORT_MODIFIED, SORT_ADDED -> all.sortedBy { it.addedOn }
                        SORT_LAST_READ -> all.sortedBy { it.lastReadTime.orEmpty() }
                        SORT_RANDOM -> all.shuffled()
                        else -> all.sortedBy { it.title.lowercase() }
                    }.let { if (ascending) it else it.reversed() }
            }
        return sorted.drop(page * size).take(size).map { it.toSeriesDto() }
    }

    private fun onDeck(
        libraryId: Int,
        page: Int,
        size: Int,
    ): List<SeriesDto> =
        catalog
            .books()
            .filter { (libraryId == 0 || it.libraryId == libraryId) && it.inProgress }
            .sortedByDescending { it.lastReadTime.orEmpty() }
            .drop(page * size)
            .take(size)
            .map { it.toSeriesDto() }

    private fun recentlyUpdated(
        page: Int,
        size: Int,
    ): List<RecentlyAddedItemDto> =
        catalog
            .books()
            .sortedByDescending { it.addedOn }
            .drop(page * size)
            .take(size)
            .map {
                RecentlyAddedItemDto(
                    seriesName = it.title,
                    seriesId = it.id,
                    libraryId = it.libraryId,
                    title = it.title,
                    created = it.addedOn,
                    chapterId = it.id,
                    volumeId = it.id,
                    id = it.id,
                    format = it.format,
                )
            }

    private fun chapterOf(book: GrimmoryBook): ChapterDto {
        // Pages are the book's chapters, the same unit the reader and the offline copy use. A book the app cannot
        // read has none, so "download all" leaves it out instead of queueing a download that can never finish.
        val pages = epubs.pagesOf(book) ?: 0
        val read = Math.round(book.progress * pages / WHOLE).toInt()
        return ChapterDto(
            id = book.id,
            range = book.seriesNumber?.let(::numberText) ?: book.title,
            number = book.seriesNumber?.let(::numberText) ?: "0",
            minNumber = book.seriesNumber,
            maxNumber = book.seriesNumber,
            sortOrder = book.seriesNumber,
            pages = pages,
            isSpecial = false,
            title = book.title,
            titleName = book.title,
            files = listOf(MangaFileDto(id = book.id, pages = pages, format = book.format, extension = book.fileType?.let { ".${it.lowercase()}" })),
            pagesRead = read,
            volumeId = book.id,
            created = book.addedOn,
            createdUtc = book.addedOn,
            lastReadingProgressUtc = book.lastReadTime,
            format = book.format,
        )
    }

    private fun volumeOf(book: GrimmoryBook): VolumeDto {
        val chapter = chapterOf(book)
        return VolumeDto(
            id = book.id,
            minNumber = book.seriesNumber,
            maxNumber = book.seriesNumber,
            name = book.title,
            title = book.title,
            pages = chapter.pages,
            pagesRead = chapter.pagesRead,
            created = book.addedOn,
            seriesId = book.id,
            chapters = listOf(chapter),
        )
    }

    private fun volumeOf(id: Int): VolumeDto? = catalog.book(id)?.let(::volumeOf)

    /** The book's page: the book itself, with the other books of its series listed as context. */
    private fun seriesDetail(bookId: Int): SeriesDetailDto {
        val book = catalog.book(bookId) ?: throw GrimmoryHttpException(NOT_FOUND)
        val books = catalog.siblings(book)
        return SeriesDetailDto(
            volumes = books.map(::volumeOf),
            unreadCount = books.count { it.progress < GrimmoryBook.FINISHED },
            totalCount = books.size,
            chapters = emptyList(),
            specials = emptyList(),
            storylineChapters = emptyList(),
        )
    }

    private fun metadata(bookId: Int): SeriesMetadataDto {
        val book = catalog.book(bookId) ?: throw GrimmoryHttpException(NOT_FOUND)
        val detail = runCatching { client.getObject("api/v1/app/books/$bookId") }.getOrDefault(emptyMap())
        return SeriesMetadataDto(
            seriesId = bookId,
            summary = detail.str("description"),
            genres = detail.strings("categories").mapIndexed { i, name -> GenreTagDto(i + 1, name) },
            tags = detail.strings("tags").mapIndexed { i, name -> TagDto(i + 1, name) },
            writers =
                book.authors
                    .map(::personName)
                    .distinctBy { it.lowercase() }
                    .map { PersonDto(name = it) },
            publishers = listOfNotNull(detail.str("publisher")).map { PersonDto(name = it) },
            language = detail.str("language"),
            releaseYear = detail.str("publishedDate")?.take(YEAR_LENGTH)?.toIntOrNull(),
            totalCount = 1,
        )
    }

    private fun continuePoint(
        request: Request,
        bookId: Int,
    ): Response = json(request, chapterOf(catalog.book(bookId) ?: throw GrimmoryHttpException(NOT_FOUND)))

    /** Series, volume and chapter all have the book's id, so any of their covers is the book's cover. */
    private fun cover(
        request: Request,
        bookId: Int,
    ): Response? = media(request, "api/v1/media/book/$bookId/cover")

    /** An image from the server, fetched with the login the app holds for it. */
    private fun media(
        request: Request,
        path: String,
    ): Response? {
        val upstream = client.open(path)
        val body = upstream.body ?: return null
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(OK)
            .message("OK")
            .header("Content-Type", upstream.header("Content-Type") ?: "image/jpeg")
            .body(body)
            .build()
    }

    /** Turns "Brown, Pierce" into "Pierce Brown" so one author is not listed twice. */
    private fun personName(raw: String): String {
        val parts = raw.split(",").map { it.trim() }
        return if (parts.size == 2 && parts.all { it.isNotEmpty() }) "${parts[1]} ${parts[0]}" else raw.trim()
    }

    private fun numberText(number: Float): String = if (number % 1f == 0f) number.toInt().toString() else number.toString()

    private inline fun <reified T> Request.bodyAs(): T? {
        val body = body ?: return null
        val buffer = Buffer().also { body.writeTo(it) }
        return runCatching { moshi.adapter(T::class.java).fromJson(buffer.readUtf8()) }.getOrNull()
    }

    @Suppress("UNCHECKED_CAST")
    private fun json(
        request: Request,
        value: Any,
    ): Response {
        val text =
            when (value) {
                is List<*> ->
                    if (value.isEmpty()) {
                        "[]"
                    } else {
                        moshi.adapter<List<Any>>(Types.newParameterizedType(List::class.java, value.first()!!.javaClass)).toJson(value as List<Any>)
                    }
                is Boolean, is Number -> value.toString()
                else -> moshi.adapter(value.javaClass).toJson(value)
            }
        return reply(request, OK, text)
    }

    private fun reply(
        request: Request,
        code: Int,
        text: String,
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == OK) "OK" else "Error")
            .body(text.toResponseBody(jsonType))
            .build()

    private companion object {
        const val OK = 200
        const val NOT_FOUND = 404
        const val BAD_REQUEST = 400
        const val BAD_GATEWAY = 502
        const val DEFAULT_PAGE = 50
        const val BOOK_LIBRARY = 2
        const val WHOLE = 100f
        const val YEAR_LENGTH = 4
        const val FIELD_SERIES_NAME = 1
        const val FIELD_LIBRARIES = 19
        const val SORT_CREATED = 2
        const val SORT_MODIFIED = 3
        const val SORT_ADDED = 4
        const val SORT_LAST_READ = 7
        const val SORT_RANDOM = 9
        val EMPTY_LISTS =
            setOf(
                "/api/collection",
                "/api/collection/all-series",
                "/api/readinglist/lists",
                "/api/readinglist/lists-for-series",
                "/api/series/series-by-collection",
                "/api/want-to-read/v2",
                "/api/reader/series-bookmarks",
                "/api/annotation/all-for-series",
                "/api/series/all-related",
                "/api/person/all",
            )
    }
}

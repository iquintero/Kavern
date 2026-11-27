package com.iquintero.kavern.core.network.grimmory

import com.iquintero.kavern.core.network.KavitaApiFactory
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.data.api.dto.FilterV2Dto
import com.iquintero.kavern.data.api.dto.ReaderProgressDto
import com.iquintero.kavern.data.api.dto.SortOptionDto
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Runs the Kavita-to-Grimmory translation against a real server. Only runs when GRIMMORY_URL, GRIMMORY_USER and
 * GRIMMORY_PASS are set. It prints what the server answers; it changes nothing but writes back the progress it read.
 */
class GrimmoryLiveTest {
    private fun config(): AppConfig {
        val url = System.getenv("GRIMMORY_URL")
        val user = System.getenv("GRIMMORY_USER")
        val pass = System.getenv("GRIMMORY_PASS")
        assumeTrue(url != null && user != null && pass != null)
        val secret = grimmorySecret(user!!, pass!!)
        return AppConfig(url!!, secret, secret, 0)
    }

    private val everything = FilterV2Dto(statements = null, combination = 1, sortOptions = SortOptionDto(1, true))

    @Test
    fun browse() =
        runBlocking {
            val api = KavitaApiFactory.createAuthenticated(config())
            println("account: " + api.getAccount().body())
            println("libraries: " + api.getLibraries().body()?.map { it.id to it.name })
            val books = api.getAllSeriesV2(everything, 0, 50, 0).body().orEmpty()
            books.forEach { println("book ${it.id} ${it.name} read=${it.pagesRead}/${it.pages} format=${it.format} library=${it.libraryId}") }
            val first = books.first()
            println(
                "volumes=" +
                    api
                        .getSeriesDetail(first.id)
                        .body()
                        ?.volumes
                        ?.map { it.id to it.name },
            )
            println(
                "summary=" +
                    api
                        .getSeriesMetadata(first.id)
                        .body()
                        ?.summary
                        ?.take(60),
            )
            println("added=" + api.getRecentlyAddedSeries(everything, 0, 10).body()?.map { it.name })
            println("continue=" + api.getContinuePoint(first.id).body()?.id)
        }

    @Test
    fun read() =
        runBlocking {
            val api = KavitaApiFactory.createAuthenticated(config())
            val epub =
                api
                    .getAllSeriesV2(everything, 0, 50, 0)
                    .body()
                    .orEmpty()
                    .first { it.format == 3 }
            val id = epub.id
            println("info=" + api.getBookInfo(id).body())
            println("contents=" + api.getBookChapters(id).body()?.take(3))
            val html = api.getBookPage(id, 1).body().orEmpty()
            println("page length=${html.length} head=" + html.take(300).replace("\n", " "))
            println(
                "resources=" +
                    Regex("book-resources\\?file=[^\"')]+")
                        .findAll(html)
                        .map { it.value }
                        .take(3)
                        .toList(),
            )
            val saved = api.getReaderProgress(id).body()
            println("progress=$saved")
            val rewrite = ReaderProgressDto(chapterId = id, pageNum = saved?.pageNum ?: 0, bookScrollId = saved?.bookScrollId)
            println("save=" + api.setReaderProgress(rewrite).code())
            println("progress after=" + api.getReaderProgress(id).body())
            val download = api.downloadChapterFile(id)
            println("download=${download.code()} bytes=${download.body()?.bytes()?.size}")
        }
}

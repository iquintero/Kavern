package com.iquintero.kavern.data.api

import com.iquintero.kavern.data.api.dto.AnnotationDto
import com.iquintero.kavern.data.api.dto.AppUserCollectionDto
import com.iquintero.kavern.data.api.dto.BookmarkDto
import com.iquintero.kavern.data.api.dto.BrowsePersonDto
import com.iquintero.kavern.data.api.dto.BrowsePersonFilterDto
import com.iquintero.kavern.data.api.dto.CollectionDto
import com.iquintero.kavern.data.api.dto.DecodeFilterRequest
import com.iquintero.kavern.data.api.dto.FilterDefinitionDto
import com.iquintero.kavern.data.api.dto.FilterV2Dto
import com.iquintero.kavern.data.api.dto.HourEstimateRangeDto
import com.iquintero.kavern.data.api.dto.LanguageDto
import com.iquintero.kavern.data.api.dto.LibraryDto
import com.iquintero.kavern.data.api.dto.NamedDto
import com.iquintero.kavern.data.api.dto.RatingDto
import com.iquintero.kavern.data.api.dto.ReadingListDto
import com.iquintero.kavern.data.api.dto.RecentlyAddedItemDto
import com.iquintero.kavern.data.api.dto.SeriesDetailPlusDto
import com.iquintero.kavern.data.api.dto.SeriesDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Streaming

@Suppress("TooManyFunctions")
interface KavitaApi {
    @GET("api/Library")
    suspend fun getLibraries(): retrofit2.Response<List<LibraryDto>>

    @GET("api/Collection")
    suspend fun getCollections(): Response<List<CollectionDto>>

    @GET("api/Collection")
    suspend fun getCollectionsAll(
        @Query("ownedOnly") ownedOnly: Boolean = false,
    ): Response<List<AppUserCollectionDto>>

    @POST("api/ReadingList/lists")
    suspend fun getReadingLists(
        @Query("includePromoted") includePromoted: Boolean = true,
        @Query("sortByLastModified") sortByLastModified: Boolean = false,
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 50,
    ): Response<List<ReadingListDto>>

    @POST("api/Person/all")
    suspend fun getBrowsePeople(
        @Body filter: BrowsePersonFilterDto,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): Response<List<BrowsePersonDto>>

    @GET("api/Collection")
    suspend fun getOwnedCollections(
        @Query("ownedOnly") ownedOnly: Boolean = true,
    ): Response<List<CollectionDto>>

    @GET("api/Collection/all-series")
    suspend fun getCollectionsForSeries(
        @Query("seriesId") seriesId: Int,
        @Query("includePromoted") includePromoted: Boolean = true,
    ): Response<List<AppUserCollectionDto>>

    @GET("api/Collection/all-series")
    suspend fun getCollectionsForSeriesOwned(
        @Query("seriesId") seriesId: Int,
        @Query("ownedOnly") ownedOnly: Boolean = false,
    ): Response<List<AppUserCollectionDto>>

    @POST("api/Series/v2")
    suspend fun getSeriesV2(
        @Body filter: FilterV2Dto,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): Response<List<SeriesDto>>

    @POST("api/Series/all-v2")
    suspend fun getAllSeriesV2(
        @Body filter: FilterV2Dto,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
        @Query("context") context: Int,
    ): Response<List<SeriesDto>>

    @GET("api/Series/series-by-collection")
    suspend fun getSeriesByCollection(
        @Query("collectionId") collectionId: Int,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): Response<List<SeriesDto>>

    @GET("api/Series/series-detail")
    suspend fun getSeriesDetail(
        @Query("seriesId") seriesId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.SeriesDetailDto>

    @GET("api/Series/{seriesId}")
    suspend fun getSeriesById(
        @retrofit2.http.Path("seriesId") seriesId: Int,
    ): Response<SeriesDto>

    @GET("api/reader/time-left")
    suspend fun getTimeLeft(
        @Query("seriesId") seriesId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.TimeLeftDto>

    @GET("api/reader/time-left")
    suspend fun getSeriesTimeLeft(
        @Query("seriesId") seriesId: Int,
    ): Response<HourEstimateRangeDto>

    @GET("api/reader/has-progress")
    suspend fun getHasProgress(
        @Query("seriesId") seriesId: Int,
    ): Response<Boolean>

    @GET("api/reader/continue-point")
    suspend fun getContinuePoint(
        @Query("seriesId") seriesId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.ChapterDto>

    @GET("api/reader/series-bookmarks")
    suspend fun getSeriesBookmarks(
        @Query("seriesId") seriesId: Int,
    ): Response<List<BookmarkDto>>

    @GET("api/Annotation/all-for-series")
    suspend fun getAnnotationsForSeries(
        @Query("seriesId") seriesId: Int,
    ): Response<List<AnnotationDto>>

    @GET("api/Rating/overall-series")
    suspend fun getOverallSeriesRating(
        @Query("seriesId") seriesId: Int,
    ): Response<RatingDto>

    @GET("api/Metadata/series-detail-plus")
    suspend fun getSeriesDetailPlus(
        @Query("seriesId") seriesId: Int,
        @Query("libraryType") libraryType: Int,
    ): Response<SeriesDetailPlusDto>

    @GET("api/Series/metadata")
    suspend fun getSeriesMetadata(
        @Query("seriesId") seriesId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.SeriesMetadataDto>

    @GET("api/Series/volumes")
    suspend fun getSeriesVolumes(
        @Query("seriesId") seriesId: Int,
    ): Response<List<com.iquintero.kavern.data.api.dto.VolumeDto>>

    @GET("api/volume")
    suspend fun getVolumeById(
        @Query("volumeId") volumeId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.VolumeDto>

    @GET("api/Stats/user/{userId}/read")
    suspend fun getUserReadStats(
        @retrofit2.http.Path("userId") userId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.UserReadStatisticsDto>

    @GET("api/Stats/reading-count-by-day")
    suspend fun getReadingCountByDay(
        @Query("userId") userId: Int = 0,
        @Query("days") days: Int = 30,
    ): Response<List<com.iquintero.kavern.data.api.dto.DateTimePagesReadOnADayCountDto>>

    @GET("api/Stats/pages-per-year")
    suspend fun getPagesPerYear(
        @Query("userId") userId: Int = 0,
    ): Response<List<com.iquintero.kavern.data.api.dto.Int32StatCountDto>>

    @GET("api/Stats/words-per-year")
    suspend fun getWordsPerYear(
        @Query("userId") userId: Int = 0,
    ): Response<List<com.iquintero.kavern.data.api.dto.Int32StatCountDto>>

    @GET("api/Book/{chapterId}/chapters")
    suspend fun getBookChapters(
        @retrofit2.http.Path("chapterId") chapterId: Int,
    ): Response<List<com.iquintero.kavern.data.api.dto.BookChapterItemDto>>

    @GET("api/Book/{chapterId}/book-info")
    suspend fun getBookInfo(
        @retrofit2.http.Path("chapterId") chapterId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.BookInfoDto>

    @GET("api/metadata/languages")
    suspend fun getLanguagesMeta(): Response<List<LanguageDto>>

    @GET("api/library/libraries")
    suspend fun getLibrariesFilter(): Response<List<LibraryDto>>

    @GET("api/users/has-library-access")
    suspend fun hasLibraryAccess(
        @Query("libraryId") libraryId: Int,
    ): Response<Boolean>

    @POST("api/Series/on-deck")
    suspend fun getOnDeckSeries(
        @Query("libraryId") libraryId: Int = 0,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): Response<List<SeriesDto>>

    @POST("api/Series/recently-updated-series")
    suspend fun getRecentlyUpdatedSeries(
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): Response<List<RecentlyAddedItemDto>>

    @POST("api/Series/recently-added-v2")
    suspend fun getRecentlyAddedSeries(
        @Body filter: FilterV2Dto,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): Response<List<SeriesDto>>

    @GET("api/Filter")
    suspend fun getFilters(): Response<List<FilterDefinitionDto>>

    @POST("api/Filter/decode")
    suspend fun decodeFilter(
        @Body req: DecodeFilterRequest,
    ): Response<FilterV2Dto>

    @POST("api/want-to-read/v2")
    suspend fun getWantToRead(
        @Body filter: FilterV2Dto,
        @Query("PageNumber") pageNumber: Int,
        @Query("PageSize") pageSize: Int,
    ): Response<List<SeriesDto>>

    @GET("api/want-to-read")
    suspend fun hasWantToRead(
        @Query("seriesId") seriesId: Int,
    ): Response<Boolean>

    @GET("api/ReadingList/lists-for-series")
    suspend fun getReadingListsForSeries(
        @Query("seriesId") seriesId: Int,
    ): Response<List<ReadingListDto>>

    @GET("api/Metadata/tags")
    suspend fun getTagsForLibraries(
        @Query("libraryIds") libraryIds: String,
    ): Response<List<NamedDto>>

    @GET("api/Metadata/genres")
    suspend fun getGenresForLibraries(
        @Query("libraryIds") libraryIds: String,
    ): Response<List<NamedDto>>

    @POST("api/Collection/update-for-series")
    suspend fun addSeriesToCollection(
        @Body body: com.iquintero.kavern.data.api.dto.CollectionTagBulkAddDto,
    ): Response<Unit>

    @POST("api/Collection/update-series")
    suspend fun updateSeriesForCollection(
        @Body body: com.iquintero.kavern.data.api.dto.UpdateSeriesForTagDto,
    ): Response<Unit>

    @POST("api/want-to-read/add-series")
    suspend fun addWantToRead(
        @Body body: com.iquintero.kavern.data.api.dto.WantToReadDto,
    ): Response<Unit>

    @POST("api/want-to-read/remove-series")
    suspend fun removeWantToRead(
        @Body body: com.iquintero.kavern.data.api.dto.WantToReadDto,
    ): Response<Unit>

    @GET("api/Series/all-related")
    suspend fun getAllRelated(
        @Query("seriesId") seriesId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.RelatedSeriesDto>

    @GET("api/Account")
    suspend fun getAccount(): Response<com.iquintero.kavern.data.api.dto.UserDto>

    /** Kavita 0.8.9 and newer. Older servers answer 404 and need [getReadingHistory]. */
    @GET("api/Stats/reading-history")
    suspend fun getReadingHistorySessions(
        @Query("PageNumber") pageNumber: Int = 1,
        @Query("PageSize") pageSize: Int = 100,
        @Query("TimeZoneId") timeZoneId: String? = null,
        // The server only matches activity in the libraries listed here, so an empty list returns nothing.
        @Query("Libraries") libraries: List<Int> = emptyList(),
    ): Response<List<com.iquintero.kavern.data.api.dto.ReadingHistorySessionDto>>

    @GET("api/Stats/user/reading-history")
    suspend fun getReadingHistory(
        @Query("userId") userId: Int? = null,
    ): Response<List<com.iquintero.kavern.data.api.dto.ReadHistoryEventDto>>

    @GET("api/Book/{chapterId}/book-page")
    suspend fun getBookPage(
        @retrofit2.http.Path("chapterId") chapterId: Int,
        @Query("page") page: Int,
    ): Response<String>

    @Streaming
    @GET("api/Reader/pdf")
    suspend fun getPdf(
        @Query("chapterId") chapterId: Int,
        @Query("apiKey") apiKey: String? = null,
        @Query("extractPdf") extractPdf: Boolean = false,
    ): Response<ResponseBody>

    @Streaming
    @GET("api/Download/chapter")
    suspend fun downloadChapterFile(
        @Query("chapterId") chapterId: Int,
    ): Response<ResponseBody>

    @GET("api/Reader/get-progress")
    suspend fun getReaderProgress(
        @Query("chapterId") chapterId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.ReaderProgressDto>

    @POST("api/reader/progress")
    suspend fun setReaderProgress(
        @Body body: com.iquintero.kavern.data.api.dto.ReaderProgressDto,
    ): Response<Unit>

    @GET("api/reader/next-chapter")
    suspend fun getNextChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("currentChapterId") currentChapterId: Int,
    ): Response<Int>

    @GET("api/reader/prev-chapter")
    suspend fun getPreviousChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("currentChapterId") currentChapterId: Int,
    ): Response<Int>

    @GET("api/reader/time-left-for-chapter")
    suspend fun getTimeLeftForChapter(
        @Query("seriesId") seriesId: Int,
        @Query("chapterId") chapterId: Int,
    ): Response<com.iquintero.kavern.data.api.dto.TimeLeftDto>

    @POST("api/reader/mark-read")
    suspend fun markSeriesRead(
        @Body body: com.iquintero.kavern.data.api.dto.MarkSeriesDto,
    ): Response<Unit>

    @POST("api/reader/mark-unread")
    suspend fun markSeriesUnread(
        @Body body: com.iquintero.kavern.data.api.dto.MarkSeriesDto,
    ): Response<Unit>

    @POST("api/reader/mark-volume-read")
    suspend fun markVolumeRead(
        @Body body: com.iquintero.kavern.data.api.dto.MarkVolumeReadDto,
    ): Response<Unit>

    @POST("api/reader/mark-volume-unread")
    suspend fun markVolumeUnread(
        @Body body: com.iquintero.kavern.data.api.dto.MarkVolumeReadDto,
    ): Response<Unit>

    @POST("api/reader/mark-multiple-read")
    suspend fun markMultipleRead(
        @Body body: com.iquintero.kavern.data.api.dto.MarkMultipleDto,
    ): Response<Unit>

    @POST("api/reader/mark-multiple-unread")
    suspend fun markMultipleUnread(
        @Body body: com.iquintero.kavern.data.api.dto.MarkMultipleDto,
    ): Response<Unit>
}

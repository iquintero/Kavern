package com.iquintero.kavern.ui.reader.renderer

import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.core.storage.AppConfig
import com.iquintero.kavern.core.storage.ImageReaderMode
import com.iquintero.kavern.core.storage.ReaderThemeMode
import com.iquintero.kavern.ui.reader.model.ReaderFontOption
import com.iquintero.kavern.ui.reader.viewmodel.ReaderUiState

interface BaseReader {
    val supportsTextSettings: Boolean

    @Composable
    fun Content(
        params: ReaderRenderParams,
        callbacks: ReaderRenderCallbacks,
    )
}

data class ReaderRenderParams(
    val uiState: ReaderUiState,
    val serverUrl: String?,
    val apiKey: String?,
    val config: AppConfig?,
    val fontSize: Float,
    val lineHeight: Float,
    val padding: Dp,
    val paddingVertical: Dp = 0.dp,
    val fontOption: ReaderFontOption,
    val textAlign: TextAlign,
    val themeMode: ReaderThemeMode,
    val pendingScrollY: Int?,
    val pendingScrollId: String?,
    val imageReaderMode: ImageReaderMode,
    val imagePrefetchPages: Int,
    val pdfRightToLeft: Boolean = false,
    val epubPaged: Boolean = true,
    /** False on e-ink displays: turn pages instantly instead of sliding. */
    val pageAnimations: Boolean = true,
    val openAtEnd: Boolean = false,
    val insetTopPx: Int = 0,
    val insetBottomPx: Int = 0,
    val enterDirection: Int = 0,
    val hasPrevPage: Boolean = false,
    val hasNextPage: Boolean = false,
)

data class ReaderRenderCallbacks(
    val onToggleOverlay: () -> Unit,
    val onSwipeNext: () -> Unit,
    val onSwipePrev: () -> Unit,
    val onImagePageVisible: (Int, Int, Int) -> Unit,
    val onWebtoonChapterBoundaryVisible: (Int, Int) -> Unit,
    val onConsumePendingScroll: () -> Unit,
    val onConsumeScrollId: () -> Unit,
    val onWebViewReady: (WebView) -> Unit,
    val onScrollIdle: (String?) -> Unit,
    val onPdfPageChanged: (Int, Int) -> Unit,
    val onEpubPageChanged: (Int, Int) -> Unit = { _, _ -> },
    val onConsumeOpenAtEnd: () -> Unit = {},
    val onConsumeEnter: () -> Unit = {},
    /** A word or phrase was selected in the EPUB and "Define" was chosen: (text, book language). */
    val onLookupWord: (String, String?) -> Unit = { _, _ -> },
)

package com.iquintero.kavern.ui.reader.screen

import com.iquintero.kavern.core.storage.ReaderThemeMode

/** Page background and text colour (CSS) for a reader theme. */
internal fun themeColorsFor(mode: ReaderThemeMode): Pair<String, String> =
    when (mode) {
        ReaderThemeMode.Light -> "#FFFFFF" to "#111111"
        ReaderThemeMode.Dark -> "#121212" to "#E0E0E0"
        ReaderThemeMode.DarkHighContrast -> "#0A0A0A" to "#FFFFFF"
        ReaderThemeMode.Sepia -> "#F6ECD9" to "#5B4636"
        ReaderThemeMode.SepiaHighContrast -> "#F6ECD9" to "#000000"
        ReaderThemeMode.Gray -> "#222222" to "#C8C8C8"
    }

/** Everything the reader's own stylesheet depends on. */
internal data class ReaderCssSettings(
    val fontFaceCss: String,
    val fontFamily: String,
    val fontSize: Float,
    val lineHeight: Float,
    val paddingPx: Float,
    /** Extra space above and below the text. Paged mode shrinks the page itself instead, so it ignores this. */
    val verticalPaddingPx: Float = 0f,
    val textAlignCss: String,
    val backgroundColor: String,
    val textColor: String,
    val paged: Boolean,
    /** Draw selected text as inverse video (text color as the background) instead of the system's tinted highlight. */
    val solidSelection: Boolean = false,
)

/** The stylesheet the reader appends to a book page, so the reader's settings win over the book's own CSS. */
internal fun readerCss(settings: ReaderCssSettings): String {
    val paddingTopPx = settings.paddingPx / 2f + if (settings.paged) 0f else settings.verticalPaddingPx
    val themeCss =
        """
        html, body {
            background: ${settings.backgroundColor} !important;
            color: ${settings.textColor} !important;
        }
        .book-content, .book-content * {
            color: ${settings.textColor} !important;
        }
        a { color: ${settings.textColor} !important; }
        """.trimIndent()
    return """
        ${settings.fontFaceCss}
        $themeCss
        .kavita-scale-width, .kavita-scale-width img,
        .cover_image img, .image_full img, .pc img {
            max-width: 100%;
            height: auto;
            display: block;
            margin: 0 auto;
        }
        a { text-decoration: none; color: inherit; pointer-events: none; }
        h1, h2, h3, h4, h5, h6 { text-decoration: none; }
        ${bookContentLayoutCss(settings.textAlignCss)}
        ${pagedContentCss(settings.paged)}
        ${selectionCss(settings)}
        body, .book-content {
            font-size: ${settings.fontSize}px !important;
            line-height: ${settings.lineHeight} !important;
            text-align: ${settings.textAlignCss} !important;
            font-family: '${settings.fontFamily}' !important;
            padding-left: ${settings.paddingPx}px !important;
            padding-right: ${settings.paddingPx}px !important;
            padding-top: ${paddingTopPx}px !important;
            padding-bottom: ${paddingTopPx}px !important;
        }
        """.trimIndent()
}

/**
 * On e-ink the system's translucent selection tint turns into a dark gray block that hides the selected words. Inverse
 * video, the page's own two colors swapped, stays readable on any panel.
 */
internal fun selectionCss(settings: ReaderCssSettings): String =
    if (settings.solidSelection) {
        "::selection { background: ${settings.textColor} !important; color: ${settings.backgroundColor} !important; }"
    } else {
        ""
    }

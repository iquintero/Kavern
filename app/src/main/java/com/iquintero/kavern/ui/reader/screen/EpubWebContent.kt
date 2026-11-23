package com.iquintero.kavern.ui.reader.screen

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.iquintero.kavern.core.logging.LoggingManager
import com.iquintero.kavern.core.network.isSameOrigin
import com.iquintero.kavern.core.storage.AppConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.DataNode
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.io.IOException
import java.net.URLConnection
import java.util.UUID

private const val HTTP_BAD_REQUEST = 400
private const val READER_ORIGIN = "https://appassets.androidplatform.net"
private const val FONT_PATH = "/_kavern/fonts/"
private const val OFFLINE_PATH = "/_kavern/book/"
private const val CONTENT_POLICY =
    "default-src 'none'; script-src 'none'; style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data:; font-src 'self' data:; media-src 'self'; base-uri 'self'; form-action 'none'"
internal const val READER_FONT_URL = "$READER_ORIGIN$FONT_PATH"

internal class EpubWebContent(
    context: Context,
    html: String,
    config: AppConfig?,
    fromOffline: Boolean,
    offlineAssetPaths: Set<String>,
) {
    private val server = config?.serverUrl?.toHttpUrlOrNull()?.takeUnless { fromOffline }
    private val kavita = config?.takeIf { server != null }?.let(::KavitaWebResources)
    private val offlinePrefix = "$OFFLINE_PATH${UUID.randomUUID()}/"
    private val paths = offlineAssetPaths.toList()
    private val offlineAssets = paths.withIndex().associate { (index, path) -> path to index.toString() }
    private val assets = WebViewAssetLoader.AssetsPathHandler(context)
    private val loader =
        WebViewAssetLoader
            .Builder()
            .addPathHandler(FONT_PATH) { path ->
                if (path.split('/').none { it == "." || it == ".." } && path.substringAfterLast('.') in setOf("ttf", "otf")) {
                    assets.handle("fonts/$path")
                } else {
                    blockedResource()
                }
            }.addPathHandler(offlinePrefix) { token ->
                val path = paths.getOrNull(token.toIntOrNull() ?: -1)
                path?.let { openImage(context, it) } ?: blockedResource()
            }.build()

    val documentUrl = "$READER_ORIGIN/_kavern/document/${UUID.randomUUID()}"

    @Volatile
    private var documentHtml = ""
    private val document =
        Jsoup.parse(html).apply {
            outputSettings().prettyPrint(false)
            select("script, iframe, frame, frameset, object, embed, base, meta[http-equiv], template, portal").remove()
            select("link").filterNot { it.attr("rel").equals("stylesheet", ignoreCase = true) }.forEach { it.remove() }
            getAllElements().forEach(::prepareElement)
            wrapInBookContent(this)
            server?.let { head().prependElement("base").attr("href", "$READER_ORIGIN${it.encodedPath}") }
        }

    fun load(
        view: WebView,
        css: String,
    ) {
        document.head().selectFirst("#reader-style")?.remove()
        document
            .head()
            .appendElement("style")
            .attr("id", "reader-style")
            .appendChild(DataNode(css))
        documentHtml = document.outerHtml()
        view.loadUrl(documentUrl)
    }

    private fun prepareElement(element: Element) {
        rewriteStyleElement(element, ::resourceUrl)
        if (element.hasAttr("style")) element.attr("style", withoutReaderOwnedDeclarations(element.attr("style")))
        element
            .attributes()
            .asList()
            .filter { it.key.startsWith("on", ignoreCase = true) || it.key == "srcdoc" }
            .forEach { element.removeAttr(it.key) }
        for (attribute in listOf("src", "href", "xlink:href", "poster")) {
            if (!element.hasAttr(attribute)) continue
            val value = element.attr(attribute).trim()
            val mapped =
                if (element.normalName() == "a" && attribute == "href" && value.startsWith("#")) {
                    // Resolve anchors against the reader document even when a base URL is set for book resources.
                    documentUrl + value
                } else {
                    resourceUrl(value)
                }
            if (mapped == null) element.removeAttr(attribute) else element.attr(attribute, mapped)
        }
    }

    fun isDocumentUrl(url: String?): Boolean = url?.substringBefore('#') == documentUrl

    private fun resourceUrl(value: String): String? {
        if (value.isEmpty()) return null
        offlineAssets[value]?.let { return "$READER_ORIGIN$offlinePrefix$it" }
        if (value.startsWith("#") || value.startsWith("data:image/", ignoreCase = true)) return value
        val origin = server ?: return null
        val url = origin.resolve(value)?.takeIf { origin.isSameOrigin(it) } ?: return null
        val clean = url.withoutApiKey()
        return "$READER_ORIGIN${clean.encodedPath}" + (clean.encodedQuery?.let { "?$it" } ?: "") +
            (clean.encodedFragment?.let { "#$it" } ?: "")
    }

    fun intercept(request: WebResourceRequest): WebResourceResponse {
        val uri = request.url
        if (request.method != "GET" || uri.scheme != "https" ||
            uri.authority != WebViewAssetLoader.DEFAULT_DOMAIN
        ) {
            return blockedResource()
        }
        if (request.isForMainFrame) {
            if (!isDocumentUrl(uri.toString())) return blockedResource()
            return WebResourceResponse(
                "text/html",
                "UTF-8",
                200,
                "OK",
                mapOf("Content-Security-Policy" to CONTENT_POLICY, "Cache-Control" to "no-store"),
                documentHtml.byteInputStream(),
            )
        }
        loader.shouldInterceptRequest(uri)?.let { return it }
        if (uri.path.orEmpty().startsWith("/_kavern/")) return blockedResource()
        val url =
            server
                ?.newBuilder()
                ?.encodedPath(uri.encodedPath ?: "/")
                ?.encodedQuery(uri.encodedQuery)
                ?.build()
                ?: return blockedResource()
        return kavita?.fetch(url) ?: blockedResource()
    }

    private fun openImage(
        context: Context,
        path: String,
    ): WebResourceResponse? {
        return try {
            val uri = Uri.parse(path)
            val type = if (uri.scheme == "content") context.contentResolver.getType(uri) else null
            val guessed = URLConnection.guessContentTypeFromName(uri.lastPathSegment)
            val input = if (uri.scheme == "content") context.contentResolver.openInputStream(uri) else File(uri.path ?: return null).inputStream()
            input?.let { WebResourceResponse(type?.takeIf { it.startsWith("image/") } ?: guessed ?: "application/octet-stream", null, it) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
    }
}

private val CSS_URL = Regex("""url\(\s*(["']?)([^"')]+)\1\s*\)""", RegexOption.IGNORE_CASE)

/**
 * Replaces each `url(...)` in [css] with what [map] returns for it. A url that [map] rejects (null) is left as it is,
 * which the content policy then blocks.
 */
internal fun rewriteCssUrls(
    css: String,
    map: (String) -> String?,
): String =
    CSS_URL.replace(css) { match ->
        val quote = match.groupValues[1]
        val mapped = map(match.groupValues[2].trim())
        if (mapped == null) match.value else "url($quote$mapped$quote)"
    }

/**
 * Kavita scopes every rule of a book's own CSS to `.book-content`, the element its web reader wraps pages in. Without
 * that wrapper none of those rules (heading and code fonts, figure and quote styling) would apply.
 */
internal fun rewriteStyleElement(
    element: Element,
    map: (String) -> String?,
) {
    if (element.normalName() != "style") return
    // Fonts and background images named in the book's own CSS must go through the same checked route as <img>.
    val css = element.data()
    val rewritten = withoutReaderOwnedStyles(rewriteCssUrls(css, map))
    if (rewritten != css) element.empty().appendChild(DataNode(rewritten))
}

// Braces are escaped everywhere: Android's regex engine rejects a bare closing brace that Java accepts.
private val CSS_BLOCK = Regex("""([^\{\}]*)\{([^\{\}]*)\}""")
private val CSS_DECLARATION = Regex("""(?:[^;()]|\([^)]*\))+""")
private val FONT_SIZE_VALUE = Regex("""^\s*(\d*\.?\d+)\s*(em|rem|%)\s*(!important)?\s*$""", RegexOption.IGNORE_CASE)
private val MONOSPACE_FONT = Regex("""mono|courier|consolas|menlo|code""", RegexOption.IGNORE_CASE)

/** Plain body text sits within this fraction of the reader's own size, so a book that nudges it (0.96em) is not obeyed. */
private const val BODY_SIZE_MIN = 0.9
private const val BODY_SIZE_MAX = 1.1
private const val PERCENT_PER_UNIT = 100.0

/**
 * Whether the reader's settings should win over this one CSS declaration of a book.
 *
 * The reader owns how body text looks (font, size, line height, alignment). A book's own rule would override it for
 * every paragraph class it names, so those declarations are dropped. Deliberate styling is kept: centered or right
 * aligned titles, sizes clearly larger or smaller than body text, and monospace fonts for code.
 */
internal fun isReaderOwnedDeclaration(
    property: String,
    value: String,
): Boolean {
    val v = value.trim()
    return when (property.trim().lowercase()) {
        "line-height" -> true
        "text-align" -> v.removeSuffix("!important").trim().lowercase() in setOf("justify", "left", "start", "inherit", "initial")
        "font-family" -> !MONOSPACE_FONT.containsMatchIn(v)
        "font-size" -> {
            val match = FONT_SIZE_VALUE.matchEntire(v)
            val keyword = v.removeSuffix("!important").trim().lowercase() in setOf("medium", "inherit", "initial", "unset")
            val relative =
                match?.let {
                    val number = it.groupValues[1].toDouble()
                    val size = if (it.groupValues[2] == "%") number / PERCENT_PER_UNIT else number
                    size in BODY_SIZE_MIN..BODY_SIZE_MAX
                } ?: false
            keyword || relative
        }
        else -> false
    }
}

/** [declarations] (the inside of a rule, or a `style` attribute) without the ones the reader's settings own. */
internal fun withoutReaderOwnedDeclarations(declarations: String): String =
    CSS_DECLARATION
        .findAll(declarations)
        .map { it.value }
        .filterNot {
            val colon = it.indexOf(':')
            colon > 0 && isReaderOwnedDeclaration(it.substring(0, colon), it.substring(colon + 1))
        }.joinToString(";") { it.trim() }

/** A book's [css] with the declarations the reader's settings own removed, leaving `@font-face` and `@page` alone. */
internal fun withoutReaderOwnedStyles(css: String): String =
    CSS_BLOCK.replace(css) { block ->
        val selector = block.groupValues[1]
        if (selector.contains("@font-face", ignoreCase = true) || selector.contains("@page", ignoreCase = true)) {
            block.value
        } else {
            "$selector{${withoutReaderOwnedDeclarations(block.groupValues[2])}}"
        }
    }

/**
 * Keeps the reader's own layout settings in charge once a book's CSS applies: the book's page width, margins and
 * background are dropped, and plain paragraphs follow the chosen text alignment.
 */
internal fun bookContentLayoutCss(textAlignCss: String): String =
    """
    .book-content {
        width: auto !important;
        max-width: none !important;
        margin: 0 !important;
        border: 0 !important;
        background: transparent !important;
    }
    .book-content p:not([class]) { text-align: $textAlignCss !important; }
    """.trimIndent()

internal fun wrapInBookContent(document: Document) {
    val body = document.body()
    val wrapper = Element("div").addClass("book-content")
    body.childNodes().toList().forEach { wrapper.appendChild(it) }
    body.appendChild(wrapper)
}

internal fun HttpUrl.withoutApiKey(): HttpUrl =
    newBuilder()
        .apply {
            queryParameterNames.filter { it.equals("apiKey", ignoreCase = true) }.forEach { removeAllQueryParameters(it) }
        }.build()

internal fun blockedResource() = WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), null)

@Suppress("DEPRECATION", "SetJavaScriptEnabled")
internal fun WebView.configureEpubWebView(
    content: () -> EpubWebContent,
    pageFinished: (WebView) -> Unit,
) {
    settings.javaScriptEnabled = true
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.allowFileAccessFromFileURLs = false
    settings.allowUniversalAccessFromFileURLs = false
    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
    // Blocked requests (content policy, bad URLs) show up here as console errors; keep them for the log export.
    webChromeClient =
        object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(message: android.webkit.ConsoleMessage): Boolean {
                val level = message.messageLevel()
                if (level == android.webkit.ConsoleMessage.MessageLevel.ERROR || level == android.webkit.ConsoleMessage.MessageLevel.WARNING) {
                    LoggingManager.w("EpubWebView", "console ${level.name}: ${message.message().take(300)}")
                }
                return true
            }
        }
    webViewClient =
        object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest,
            ): WebResourceResponse =
                content().intercept(request).also {
                    if (it.statusCode >= HTTP_BAD_REQUEST) {
                        LoggingManager.w("EpubWebView", "Blocked or failed ${request.url.path} (${it.statusCode})")
                    }
                }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest,
            ): Boolean =
                !(
                    request.isForMainFrame &&
                        request.method == "GET" &&
                        request.url.encodedFragment != null &&
                        content().isDocumentUrl(request.url.toString())
                )

            override fun onPageFinished(
                view: WebView,
                url: String?,
            ) {
                if (url == content().documentUrl) pageFinished(view)
            }
        }
}

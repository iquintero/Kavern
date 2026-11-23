package com.iquintero.kavern.ui.common

import android.view.KeyEvent
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iquintero.kavern.R
import com.iquintero.kavern.ui.theme.LocalEinkMode
import com.iquintero.kavern.ui.theme.einkOutline
import kotlinx.coroutines.launch

/** A page jump leaves a little of the previous screen in view, so the eye can find its place. */
private const val PAGE_FRACTION = 0.9f

/** How far, in pixels, one page jump scrolls a viewport of [viewportPx]; negative to go back. */
internal fun pageDelta(
    viewportPx: Int,
    forward: Boolean,
): Float = viewportPx * PAGE_FRACTION * if (forward) 1f else -1f

/** A scrollable list that can be moved one screen at a time. */
interface PageScroller {
    /** Whether there is more to show in that direction. Reads snapshot state, so a composable using it updates. */
    fun canPage(forward: Boolean): Boolean

    /** Scrolls one screen, instantly: a glide would be a smear on e-ink. */
    suspend fun page(forward: Boolean)
}

internal class StatePageScroller(
    private val canBack: () -> Boolean,
    private val canForward: () -> Boolean,
    private val viewportPx: () -> Int,
    private val scrollBy: suspend (Float) -> Unit,
) : PageScroller {
    override fun canPage(forward: Boolean) = if (forward) canForward() else canBack()

    override suspend fun page(forward: Boolean) {
        val viewport = viewportPx()
        if (viewport > 0 && canPage(forward)) scrollBy(pageDelta(viewport, forward))
    }
}

fun LazyListState.pageScroller(): PageScroller =
    StatePageScroller(
        canBack = { canScrollBackward },
        canForward = { canScrollForward },
        viewportPx = { layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset },
        scrollBy = { scrollBy(it) },
    )

fun LazyGridState.pageScroller(): PageScroller =
    StatePageScroller(
        canBack = { canScrollBackward },
        canForward = { canScrollForward },
        viewportPx = { layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset },
        scrollBy = { scrollBy(it) },
    )

private fun ScrollState.pageScroller(viewportPx: () -> Int): PageScroller =
    StatePageScroller(
        canBack = { canScrollBackward },
        canForward = { canScrollForward },
        viewportPx = viewportPx,
        scrollBy = { scrollBy(it) },
    )

/**
 * Lets the hardware page-up and page-down keys (the page buttons on e-readers) scroll whatever list is on screen.
 * The open reader claims those keys first, for turning book pages.
 */
internal object PageKeyEvents {
    private val presses = KeyPressTracker()

    @Volatile
    private var onKey: ((forward: Boolean) -> Unit)? = null

    fun claim(handler: (forward: Boolean) -> Unit) {
        onKey = handler
    }

    /**
     * Clears [handler], but only while it is still the current one. Moving between screens composes the new one before
     * the old one is disposed, so an unconditional clear would remove the new screen's handler.
     */
    fun release(handler: (forward: Boolean) -> Unit) {
        if (onKey === handler) onKey = null
    }

    internal fun isClaimed(): Boolean = onKey != null

    /** Claims page-up and page-down, acting on a fresh press only. Returns true when the key was claimed. */
    fun handle(event: KeyEvent): Boolean {
        val forward =
            when (event.keyCode) {
                KeyEvent.KEYCODE_PAGE_DOWN -> true
                KeyEvent.KEYCODE_PAGE_UP -> false
                else -> return false
            }
        val handler = onKey ?: return false
        if (presses.isNewPress(event.keyCode, event.action, event.repeatCount)) handler(forward)
        return true
    }
}

/**
 * Two buttons that page a list up and down, and the page keys that do the same. Only in E-reader mode, and each
 * button only while there is more to show in its direction. Put it last inside a Box that holds the list.
 */
@Composable
fun BoxScope.EinkPageControls(scroller: PageScroller) {
    if (!LocalEinkMode.current) return
    val scope = rememberCoroutineScope()
    val current by rememberUpdatedState(scroller)
    DisposableEffect(Unit) {
        val handler: (Boolean) -> Unit = { forward -> scope.launch { current.page(forward) } }
        PageKeyEvents.claim(handler)
        onDispose { PageKeyEvents.release(handler) }
    }
    Column(
        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
    ) {
        PageButton(up = true, enabled = scroller.canPage(forward = false)) { scope.launch { scroller.page(forward = false) } }
        PageButton(up = false, enabled = scroller.canPage(forward = true)) { scope.launch { scroller.page(forward = true) } }
    }
}

@Composable
private fun PageButton(
    up: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    if (!enabled) {
        // Keep the space, so the other button does not jump when this one appears or goes.
        Box(modifier = Modifier.size(PAGE_BUTTON_SIZE + PAGE_BUTTON_GAP))
        return
    }
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(bottom = PAGE_BUTTON_GAP).size(PAGE_BUTTON_SIZE).einkOutline(androidx.compose.foundation.shape.CircleShape),
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
    ) {
        Icon(
            imageVector = if (up) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            contentDescription = stringResource(if (up) R.string.general_page_up else R.string.general_page_down),
        )
    }
}

private val PAGE_BUTTON_SIZE = 52.dp
private val PAGE_BUTTON_GAP = 8.dp

/** A Box around a list that adds page buttons in E-reader mode. */
@Composable
fun PagedBox(
    scroller: PageScroller,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier) {
        content()
        EinkPageControls(scroller)
    }
}

/** [PagedBox] for a plain scrolling column, which has to measure its own viewport. */
@Composable
fun PagedBox(
    scrollState: ScrollState,
    modifier: Modifier = Modifier.fillMaxSize(),
    content: @Composable BoxScope.() -> Unit,
) {
    var viewportPx by remember { mutableIntStateOf(0) }
    val scroller = remember(scrollState) { scrollState.pageScroller { viewportPx } }
    PagedBox(scroller = scroller, modifier = modifier.onSizeChanged { viewportPx = it.height }, content = content)
}

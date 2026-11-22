package com.iquintero.kavern.ui.reader.screen

import org.json.JSONObject

/** Returns the reading position of a scrolling (not paged) page: an `id("...")` expression or `idx:<n>` into `body *`. */
internal const val EPUB_SCROLL_ANCHOR_JS =
    """
(function() {
    var raw = Array.prototype.slice.call(document.querySelectorAll('body *'));
    var elems = [];
    for (var i = 0; i < raw.length; i++) {
        var el = raw[i];
        var tag = el.tagName.toLowerCase();
        if (tag === 'script' || tag === 'style' || tag === 'head' || tag === 'meta') continue;
        var rect = el.getBoundingClientRect();
        var hasText = (el.innerText || '').trim().length > 0;
        if (rect.height < 2 && !hasText) continue;
        elems.push(el);
    }
    if (elems.length === 0) return null;
    var y = window.pageYOffset;
    var bestIdx = 0;
    var bestDist = 1e9;
    for (var i = 0; i < elems.length; i++) {
        var el = elems[i];
        var top = el.getBoundingClientRect().top + window.pageYOffset;
        var dist = Math.abs(top - y);
        if (dist < bestDist) {
            bestDist = dist;
            bestIdx = i;
        }
    }
    var best = elems[bestIdx];
    if (!best) return null;
    if (best.id) return 'id(\"' + best.id + '\")';
    return 'idx:' + bestIdx;
})();
"""

/** Scrolls to a position saved by [EPUB_SCROLL_ANCHOR_JS] or by paged mode. */
internal fun epubScrollToElementJs(id: String): String {
    val element =
        if (id.startsWith("idx:")) {
            val index = id.removePrefix("idx:").toIntOrNull() ?: -1
            "document.querySelectorAll('body *')[$index]"
        } else {
            val clean =
                id
                    .removePrefix("id(\"")
                    .removeSuffix("\")")
                    .removePrefix("id('")
                    .removeSuffix("')")
            "document.getElementById(${JSONObject.quote(clean)})"
        }
    return """
        (function() {
            var el = $element;
            if (el) window.scrollTo(0, el.getBoundingClientRect().top + window.pageYOffset);
        })();
        """.trimIndent()
}

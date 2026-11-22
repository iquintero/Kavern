package com.iquintero.kavern.ui.home

import com.iquintero.kavern.domain.model.Series

/** What the Home screen shows. Every series appears once, in the most useful place. */
data class HomeSections(
    /** The series you are most likely to want to pick up right now. */
    val resume: Series? = null,
    /** Other series that are started but not finished. */
    val inProgress: List<Series> = emptyList(),
    /** Series saved for later that have not been started. */
    val wantToRead: List<Series> = emptyList(),
    val recentlyAdded: List<Series> = emptyList(),
) {
    val isEmpty: Boolean
        get() = resume == null && inProgress.isEmpty() && wantToRead.isEmpty() && recentlyAdded.isEmpty()
}

/**
 * Splits the server lists into Home sections without repeating a series.
 *
 * A saved series that has been started counts as in progress, so it moves out of Want To Read.
 */
fun buildHomeSections(
    onDeck: List<Series>,
    wantToRead: List<Series>,
    recentlyAdded: List<Series>,
): HomeSections {
    val seen = mutableSetOf<Int>()

    fun List<Series>.unseen() = filter { seen.add(it.id) }
    val started = (onDeck + wantToRead.filter { (it.pagesRead ?: 0) > 0 }).unseen()
    return HomeSections(
        resume = started.firstOrNull(),
        inProgress = started.drop(1),
        wantToRead = wantToRead.unseen(),
        recentlyAdded = recentlyAdded.unseen(),
    )
}

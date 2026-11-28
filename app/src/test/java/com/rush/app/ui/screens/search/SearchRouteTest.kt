package com.rush.app.ui.screens.search

import com.google.common.truth.Truth.assertThat
import com.rush.app.data.local.ContentType
import com.rush.app.data.local.Duration
import com.rush.app.data.local.SearchFeature
import com.rush.app.data.local.SearchFilter
import com.rush.app.data.local.SortType
import com.rush.app.data.local.UploadDate
import com.rush.app.data.local.WatchState
import org.junit.Test

class SearchRouteTest {
    @Test
    fun `filters survive the trip through a search page's route`() {
        val filters =
            SearchFilter(
                contentType = ContentType.entries.last(),
                duration = Duration.entries.last(),
                uploadDate = UploadDate.entries.last(),
                sortType = SortType.entries.last(),
                features = SearchFeature.entries.take(2).toSet(),
                watchState = WatchState.entries.last(),
            )

        assertThat(searchFilterFromRouteArg(filters.toRouteArg())).isEqualTo(filters)
        assertThat(searchFilterFromRouteArg(SearchFilter.DEFAULT.toRouteArg())).isEqualTo(SearchFilter.DEFAULT)
    }

    @Test
    fun `missing or unreadable filters fall back to the default`() {
        assertThat(searchFilterFromRouteArg(null)).isEqualTo(SearchFilter.DEFAULT)
        assertThat(searchFilterFromRouteArg("")).isEqualTo(SearchFilter.DEFAULT)
        assertThat(searchFilterFromRouteArg("NOPE|NOPE|NOPE|NOPE|NOPE|NOPE")).isEqualTo(SearchFilter.DEFAULT)
    }

    @Test
    fun `a search page's route carries the words, the filters and how deep it is`() {
        val route = searchRoute("daft punk & co", SearchFilter.DEFAULT, depth = 3)

        assertThat(route).startsWith("search?query=daft%20punk%20%26%20co&filters=")
        assertThat(route).endsWith("&depth=3")
    }

    @Test
    fun `a plus sign in the words stays a plus sign`() {
        assertThat(searchRoute("c++ tutorial", SearchFilter.DEFAULT, depth = 1))
            .startsWith("search?query=c%2B%2B%20tutorial&filters=")
    }
}

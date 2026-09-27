package tv.own.owntv.features.browse

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.ui.components.NetflixPosterCard
import tv.own.owntv.ui.components.trapVerticalFocusExit
import tv.own.owntv.ui.theme.OwnTVTheme

/**
 * EXPERIMENTAL Netflix browse screen — category rows of posters, no left rail. Movies play on OK;
 * series are browse-only for now. UP escapes to the top nav via [trapVerticalFocusExit].
 */
@Composable
fun NetflixBrowseScreen(
    mediaType: MediaType,
    onPlay: (Long) -> Unit,
    onChildFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: NetflixBrowseViewModel = koinViewModel()
    val movieRows by vm.movieRows.collectAsStateWithLifecycle()
    val seriesRows by vm.seriesRows.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .trapVerticalFocusExit()
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (mediaType == MediaType.MOVIE) {
            items(movieRows, key = { it.title }) { row ->
                BrowseCategoryRow(row.title) {
                    items(row.items, key = { it.id }) { m ->
                        NetflixPosterCard(posterUrl = m.posterUrl, title = m.name, meta = nfMeta(m.year, m.rating?.toDouble()), modifier = Modifier.width(132.dp), onClick = { onPlay(m.id) })
                    }
                }
            }
        } else {
            items(seriesRows, key = { it.title }) { row ->
                BrowseCategoryRow(row.title) {
                    items(row.items, key = { it.id }) { s ->
                        NetflixPosterCard(posterUrl = s.posterUrl, title = s.name, meta = nfMeta(s.year, s.rating?.toDouble()), modifier = Modifier.width(132.dp), onClick = { onPlay(s.id) })
                    }
                }
            }
        }
    }
}

/** "2025 · ★ 8.0" caption for a poster's focus overlay; null when nothing to show. */
private fun nfMeta(year: Int?, rating: Double?): String? = listOfNotNull(
    year?.takeIf { it > 0 }?.toString(),
    rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
).joinToString(" · ").ifBlank { null }

/** A category title above a horizontal poster carousel. */
@Composable
private fun BrowseCategoryRow(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OwnTVTheme.colors.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

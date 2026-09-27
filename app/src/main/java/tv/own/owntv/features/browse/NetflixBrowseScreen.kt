package tv.own.owntv.features.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * EXPERIMENTAL Netflix browse screen — category rows of posters, no left rail. Focusing a poster
 * reveals its detail (genre/year/rating tags + synopsis) directly BELOW that row, the way Netflix
 * does; movies play on OK, series resume. UP escapes to the top nav via [trapVerticalFocusExit].
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
    val detail by vm.focusDetail.collectAsStateWithLifecycle()
    var focusedRow by remember { mutableStateOf<String?>(null) }
    // The id of whatever poster holds focus RIGHT NOW (updated immediately, no debounce). The detail
    // below only shows once the debounced lookup catches up to this id — so while you're scrolling
    // fast the panel stays blank instead of flashing stale metadata for the tile you just left.
    var focusedId by remember { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { if (it.hasFocus) onChildFocused() }
            .trapVerticalFocusExit()
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (mediaType == MediaType.MOVIE) {
            items(movieRows, key = { it.title }) { row ->
                CategoryRowWithDetail(
                    title = row.title,
                    detail = detail.takeIf { focusedRow == row.title && detail?.id == focusedId },
                ) {
                    items(row.items, key = { it.id }) { m ->
                        NetflixPosterCard(
                            posterUrl = m.posterUrl,
                            // The focused card shows a landscape image, so give it the better TMDB
                            // backdrop once resolved (a real 16:9 still) rather than the provider's.
                            backdropUrl = if (m.id == focusedId) detail?.backdrop ?: m.backdropUrl else m.backdropUrl,
                            title = m.name,
                            meta = nfMeta(m.year, m.rating?.toDouble()),
                            onClick = { onPlay(m.id) },
                            onFocus = { focusedRow = row.title; focusedId = m.id; vm.onFocusMovie(m) },
                        )
                    }
                }
            }
        } else {
            items(seriesRows, key = { it.title }) { row ->
                CategoryRowWithDetail(
                    title = row.title,
                    detail = detail.takeIf { focusedRow == row.title && detail?.id == focusedId },
                ) {
                    items(row.items, key = { it.id }) { s ->
                        NetflixPosterCard(
                            posterUrl = s.posterUrl,
                            backdropUrl = if (s.id == focusedId) detail?.backdrop ?: s.backdropUrl else s.backdropUrl,
                            title = s.name,
                            meta = nfMeta(s.year, s.rating?.toDouble()),
                            onClick = { onPlay(s.id) },
                            onFocus = { focusedRow = row.title; focusedId = s.id; vm.onFocusSeries(s) },
                        )
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

/** A category title, its poster carousel, and — while this row holds focus — the focused item's detail below it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRowWithDetail(
    title: String,
    detail: NetflixBrowseViewModel.FocusDetail?,
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = OwnTVTheme.colors.onSurface,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
        )
        // Pin the focused card to a fixed spot near the row's start and slide the whole row under it
        // (Netflix-style), instead of letting focus drift toward the right edge — which is also what
        // kept the last, widest card growing off the screen. See [LeadingEdgeBringIntoView].
        CompositionLocalProvider(LocalBringIntoViewSpec provides LeadingEdgeBringIntoView) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
        // Detail sits below the row and only for the focused row. A FIXED height (not a min) is what
        // stops the vertical bounce: the block is always this tall whether it's empty (scrolling) or
        // showing a 3-line synopsis, so rows below never move as metadata fades in and out.
        Column(modifier = Modifier.fillMaxWidth().height(116.dp).padding(start = 4.dp, top = 10.dp)) {
            if (detail != null) {
                if (detail.tags.isNotEmpty()) {
                    Text(
                        detail.tags.joinToString("  ·  "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = OwnTVTheme.colors.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                detail.plot?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OwnTVTheme.colors.onSurfaceVariant,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(0.7f),
                    )
                }
            }
        }
    }
}

/**
 * A [BringIntoViewSpec] that scrolls the focused item's leading (left) edge to the row start and keeps
 * it there — the selection stays put and the whole row moves under it, the way Netflix rails behave.
 * It reports the distance from the leading edge only (never the trailing edge), so a card growing wider
 * on focus expands into the space to its right instead of pushing itself off the screen edge, and its
 * mid-animation size changes never re-trigger a scroll.
 */
@OptIn(ExperimentalFoundationApi::class)
internal val LeadingEdgeBringIntoView = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset
}

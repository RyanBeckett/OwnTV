package tv.own.owntv.features.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.koin.androidx.compose.koinViewModel
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.ui.components.FocusHero
import tv.own.owntv.ui.components.NetflixPosterCard
import tv.own.owntv.ui.components.NetflixRowGap
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
    // The focused item's name, tracked immediately (no debounce) so the hero's title never lags.
    var focusedTitle by remember { mutableStateOf<String?>(null) }
    // Whether focus is anywhere in this content. When it leaves (e.g. up to the top nav) the hero's
    // selection rim fades, so it's clear the selection is no longer on the row.
    var contentFocused by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .onFocusChanged { contentFocused = it.hasFocus; if (it.hasFocus) onChildFocused() }
            .trapVerticalFocusExit()
            .focusGroup(),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (mediaType == MediaType.MOVIE) {
            items(movieRows, key = { it.title }) { row ->
                val isRowFocused = focusedRow == row.title
                CategoryRowWithDetail(
                    title = row.title,
                    heroTitle = focusedTitle.takeIf { isRowFocused },
                    heroBackdrop = detail?.backdrop?.takeIf { isRowFocused && detail?.id == focusedId },
                    heroSelected = contentFocused,
                    detail = detail.takeIf { isRowFocused && detail?.id == focusedId },
                ) {
                    items(row.items, key = { it.id }) { m ->
                        NetflixPosterCard(
                            posterUrl = m.posterUrl,
                            title = m.name,
                            onClick = { onPlay(m.id) },
                            onFocus = { focusedRow = row.title; focusedId = m.id; focusedTitle = m.name; vm.onFocusMovie(m) },
                        )
                    }
                }
            }
        } else {
            items(seriesRows, key = { it.title }) { row ->
                val isRowFocused = focusedRow == row.title
                CategoryRowWithDetail(
                    title = row.title,
                    heroTitle = focusedTitle.takeIf { isRowFocused },
                    heroBackdrop = detail?.backdrop?.takeIf { isRowFocused && detail?.id == focusedId },
                    heroSelected = contentFocused,
                    detail = detail.takeIf { isRowFocused && detail?.id == focusedId },
                ) {
                    items(row.items, key = { it.id }) { s ->
                        NetflixPosterCard(
                            posterUrl = s.posterUrl,
                            title = s.name,
                            onClick = { onPlay(s.id) },
                            onFocus = { focusedRow = row.title; focusedId = s.id; focusedTitle = s.name; vm.onFocusSeries(s) },
                        )
                    }
                }
            }
        }
    }
}

/** A category title, its poster carousel with the persistent focus hero, and the focused item's detail below. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryRowWithDetail(
    title: String,
    heroTitle: String?,
    heroBackdrop: String?,
    heroSelected: Boolean,
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
        // The strip of portrait posters with the row's ONE persistent hero overlaid at a FIXED spot.
        // The row's leading content padding = HeroPeek, so the focused tile always parks at that spot
        // (even the first tile, which just has empty space to its left instead of a previous-tile
        // sliver). The hero never moves — the row slides under it — so selection reads as one motion.
        Box {
            CompositionLocalProvider(LocalBringIntoViewSpec provides rememberLeadingEdgeBringIntoViewSpec()) {
                LazyRow(
                    // Restore the last-focused tile when returning to the row (e.g. after going up to
                    // the nav and back), instead of jumping to a different tile and scrolling the row.
                    modifier = Modifier.focusRestorer(),
                    horizontalArrangement = Arrangement.spacedBy(NetflixRowGap),
                    contentPadding = PaddingValues(start = HeroPeek),
                    content = content,
                )
            }
            if (heroTitle != null) {
                FocusHero(
                    backdrop = heroBackdrop,
                    title = heroTitle,
                    selected = heroSelected,
                    modifier = Modifier.align(Alignment.TopStart).offset(x = HeroPeek),
                )
            }
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
 * How far in from the row's start the focused item rests. The whole row moves under it, so the
 * selection stays in this fixed spot — and the gap to its left reveals a sliver of the previous item,
 * the way Netflix rails do. The very first item can't scroll before the start, so it sits flush-left
 * with nothing to its left; the hero's [heroInset] mirrors this (0 for the first item, [HeroPeek]
 * otherwise) so it always sits exactly over the focused item's slot.
 */
internal val HeroPeek: Dp = 64.dp

/**
 * A [BringIntoViewSpec] that parks the focused item's leading (left) edge [peek] in from the row
 * start. It uses the leading edge only (never the trailing edge), so a card growing wider on focus
 * expands into the space to its right rather than off the screen, and mid-animation size changes never
 * re-trigger a scroll. The first item clamps to the true start (nothing scrolls before it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun rememberLeadingEdgeBringIntoViewSpec(peek: Dp = HeroPeek): BringIntoViewSpec {
    val peekPx = with(LocalDensity.current) { peek.toPx() }
    return remember(peekPx) {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - peekPx
        }
    }
}

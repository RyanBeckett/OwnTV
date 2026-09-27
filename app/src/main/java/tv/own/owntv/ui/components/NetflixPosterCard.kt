package tv.own.owntv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.gradientWash

/** Shared height for the Netflix poster tiles and the [FocusHero] that overlays the focused one. */
val NetflixCardHeight: Dp = 248.dp

/** Horizontal gap between tiles in a Netflix row. */
val NetflixRowGap: Dp = 20.dp

/** How many portrait tiles the hero covers. Its width is exactly that many tiles laid out edge to
 *  edge, so it never half-covers a poster — the next visible tile sits a clean gap past its edge. */
private const val HERO_SPAN = 2

/**
 * A fixed-size portrait poster tile for the Netflix Home and Movies/Series rows. It never changes
 * width on focus — the row's single persistent [FocusHero] covers the focused tile (and one more),
 * ending on a tile boundary so it never half-covers a poster. That's deliberate: if the focused tile
 * grew its own width it would shove every tile to its right outward, so selection would read as the
 * row EXPANDING rather than sliding. With every tile fixed, moving selection is a pure slide and the
 * hero crossfades in place.
 */
@Composable
fun NetflixPosterCard(
    posterUrl: String?,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = NetflixCardHeight,
    onFocus: () -> Unit = {},
) {
    val colors = OwnTVTheme.colors
    val portraitWidth = height * 2 / 3

    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1f,
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        // No focus ring on the tiles: the persistent hero is the one focus indicator. A ring here would
        // jump onto the next tile before the row scrolls it under the hero — two cursors out of sync.
        showFocusBorder = false,
        modifier = modifier
            .height(height)
            .width(portraitWidth)
            .onFocusChanged { if (it.hasFocus) onFocus() },
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHigh),
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The single persistent landscape "hero" for a focused row, drawn once over the pinned-left gap the
 * focused tile opens. As selection moves along the row its [backdrop] changes and it CROSSFADES to
 * the new art in place — it never tears down or reloads from blank. It also HOLDS the last art it
 * had until the next one is ready, so a still-resolving backdrop shows the previous image (Netflix's
 * behaviour) rather than a gap. [title] is the focused item's name, shown over a bottom scrim.
 */
@Composable
fun FocusHero(
    backdrop: String?,
    title: String?,
    modifier: Modifier = Modifier,
    height: Dp = NetflixCardHeight,
    selected: Boolean = true,
) {
    val colors = OwnTVTheme.colors
    // Exactly HERO_SPAN tiles wide (tiles + the gaps between them), so the hero's right edge lands on
    // a tile boundary and never half-covers the next poster; that poster sits one clean gap beyond.
    val landscapeWidth = height * 2 / 3 * HERO_SPAN + NetflixRowGap * (HERO_SPAN - 1)
    // Hold the last non-blank backdrop so moving to an item whose art hasn't resolved yet keeps
    // showing the previous one instead of flashing empty; Coil crossfades when the new one arrives.
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(backdrop) { if (!backdrop.isNullOrBlank()) shown = backdrop }
    // The white rim is the selection cue: it fades out when focus leaves the content (e.g. up to the
    // top nav), so it's clear the selection is no longer on this row.
    val rimAlpha by animateFloatAsState(if (selected) 0.85f else 0f, label = "heroRim")

    Box(
        modifier = modifier
            .width(landscapeWidth)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHigh)
            .border(2.dp, Color.White.copy(alpha = rimAlpha), RoundedCornerShape(8.dp)),
    ) {
        shown?.let { url ->
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().gradientWash(
                vertical = true,
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.94f),
            ),
        )
        if (!title.isNullOrBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
            )
        }
    }
}

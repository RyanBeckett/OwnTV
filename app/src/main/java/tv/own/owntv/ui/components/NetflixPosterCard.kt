package tv.own.owntv.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
val NetflixCardHeight: Dp = 190.dp

/**
 * A portrait poster tile for the Netflix Home and Movies/Series rows. On focus it does not draw its
 * own hover art — it simply reserves the landscape width (animated, so the tiles to its right slide
 * open rather than jump), opening a gap at the row's pinned-left position that the row's single
 * persistent [FocusHero] fills. Keeping the wide art in one persistent element — instead of every
 * tile morphing into and out of a landscape card — is what stops the backdrop reloading and jumping
 * on each selection.
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
    val landscapeWidth = height * 16 / 9

    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1f,
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        modifier = modifier
            .height(height)
            .onFocusChanged { if (it.hasFocus) onFocus() },
    ) { focused ->
        // Grow the reserved width smoothly INTO focus (the tiles to the right slide open), collapse it
        // INSTANTLY out of focus so a shrinking neighbour can't drag the focused tile mid-animation.
        val cardWidth = remember { Animatable(portraitWidth, Dp.VectorConverter) }
        LaunchedEffect(focused) {
            if (focused) cardWidth.animateTo(landscapeWidth, animationSpec = tween(durationMillis = 220))
            else cardWidth.snapTo(portraitWidth)
        }
        Box(
            modifier = Modifier
                .width(cardWidth.value)
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHigh),
        ) {
            // The poster shows when idle; when focused this slot is covered by the row's FocusHero, so
            // the poster underneath is just a safe fallback for the instant before the hero paints.
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
) {
    val colors = OwnTVTheme.colors
    val landscapeWidth = height * 16 / 9
    // Hold the last non-blank backdrop so moving to an item whose art hasn't resolved yet keeps
    // showing the previous one instead of flashing empty; Coil crossfades when the new one arrives.
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(backdrop) { if (!backdrop.isNullOrBlank()) shown = backdrop }

    Box(
        modifier = modifier
            .width(landscapeWidth)
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceContainerHigh),
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

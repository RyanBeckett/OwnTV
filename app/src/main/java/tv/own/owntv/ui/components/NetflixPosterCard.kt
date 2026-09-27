package tv.own.owntv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.gradientWash

/**
 * The shared Netflix-style poster tile used by Home and the Movies/Series browse rows. Idle it is a
 * portrait poster; on focus it widens into a landscape "hover card" — same row height, so the row
 * never shifts vertically, only the neighbours slide sideways — swapping to the backdrop art (when
 * there is one) with a bottom scrim revealing the title and a meta line (year · rating). One
 * component so the focus behaviour is identical everywhere.
 */
@Composable
fun NetflixPosterCard(
    posterUrl: String?,
    backdropUrl: String?,
    title: String,
    meta: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 190.dp,
    onFocus: () -> Unit = {},
) {
    val colors = OwnTVTheme.colors
    // Constant height; width is what grows. Portrait 2:3 idle, landscape 16:9 on focus — so lower
    // rows never move (only the tiles beside this one slide over as it expands).
    val portraitWidth = height * 2 / 3
    val landscapeWidth = height * 16 / 9

    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        // The width animation is the "grow", so no extra scale-up on top of it.
        focusedScale = 1f,
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        modifier = modifier
            .height(height)
            .onFocusChanged { if (it.hasFocus) onFocus() },
    ) { focused ->
        val animatedWidth by animateDpAsState(
            targetValue = if (focused) landscapeWidth else portraitWidth,
            label = "nfCardWidth",
        )
        Box(
            modifier = Modifier
                .width(animatedWidth)
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHigh),
        ) {
            // Landscape backdrop while focused (falls back to the poster if there is no backdrop),
            // portrait poster otherwise. Crossfade so the art swap isn't a hard cut.
            val art = if (focused && !backdropUrl.isNullOrBlank()) backdropUrl else posterUrl
            Crossfade(targetState = art, label = "nfCardArt") { model ->
                if (!model.isNullOrBlank()) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (focused) {
                Box(
                    Modifier.fillMaxSize().gradientWash(
                        vertical = true,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    meta?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.primary)
                    }
                }
            }
        }
    }
}

package tv.own.owntv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
 * The shared Netflix-style poster tile used by Home and the Movies/Series browse rows. Idle it is
 * just the artwork; on focus it lifts (scale) and a bottom scrim reveals the title and a meta line
 * (year · rating) — the "hover card" behaviour. One component so the focus detail is identical
 * everywhere.
 */
@Composable
fun NetflixPosterCard(
    posterUrl: String?,
    title: String,
    meta: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 132.dp,
    onFocus: () -> Unit = {},
) {
    val colors = OwnTVTheme.colors
    FocusableSurface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        focusedScale = 1.12f,
        unfocusedContainerColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        // Own fixed width so a poster carousel lays out correctly regardless of the caller.
        modifier = modifier
            .width(width)
            .onFocusChanged { if (it.hasFocus) onFocus() },
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceContainerHigh),
        ) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (focused) {
                Box(
                    Modifier.fillMaxSize().gradientWash(
                        vertical = true,
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.94f),
                    ),
                )
                Column(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)) {
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

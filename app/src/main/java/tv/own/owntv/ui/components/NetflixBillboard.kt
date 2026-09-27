package tv.own.owntv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import tv.own.owntv.R
import androidx.compose.ui.res.stringResource
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.ui.theme.OwnTVTheme
import tv.own.owntv.ui.theme.gradientWash

/** The title a [NetflixBillboard] shows — the fields any movie or series poster can supply. */
data class BillboardItem(
    val id: Long,
    val title: String,
    val backdropUrl: String?,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double?,
    val plot: String?,
)

fun MovieEntity.toBillboard() = BillboardItem(id, name, backdropUrl, posterUrl, year, rating?.toDouble(), plot)
fun SeriesEntity.toBillboard() = BillboardItem(id, name, backdropUrl, posterUrl, year, rating?.toDouble(), plot)

/**
 * The Netflix "focus billboard": a full-width banner at the top of a browse screen that reflects the
 * poster currently under focus — backdrop, title, a tags line (year · rating) and a short synopsis,
 * plus Play. It crossfades as focus moves between posters, so hovering a title pulls its metadata in.
 */
@Composable
fun NetflixBillboard(
    item: BillboardItem?,
    onPlay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OwnTVTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(430.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (item == null) return@Box
        val art = item.backdropUrl?.takeIf { it.isNotBlank() } ?: item.posterUrl
        Crossfade(targetState = art, label = "nf-billboard-art") { url ->
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Box(
            Modifier.fillMaxSize().gradientWash(
                vertical = false,
                0f to Color.Black.copy(alpha = 0.85f),
                0.45f to Color.Black.copy(alpha = 0.35f),
                1f to Color.Transparent,
            ),
        )
        Box(
            Modifier.fillMaxSize().gradientWash(
                vertical = true,
                0.5f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.9f),
            ),
        )
        Crossfade(targetState = item, label = "nf-billboard-text") { it ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 44.dp, end = 44.dp, bottom = 32.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(
                    it.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    it.year?.takeIf { y -> y > 0 }?.let { y ->
                        Text(y.toString(), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    it.rating?.takeIf { r -> r > 0 }?.let { r ->
                        Text(stringResource(R.string.content_rating, r), style = MaterialTheme.typography.titleSmall, color = colors.primary, fontWeight = FontWeight.Bold)
                    }
                }
                it.plot?.takeIf { p -> p.isNotBlank() }?.let { p ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        p,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.55f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                OwnTVButton(
                    label = stringResource(R.string.content_action_play),
                    icon = OwnTVIcon.PLAY,
                    onClick = { onPlay(it.id) },
                    style = OwnTVButtonStyle.PRIMARY,
                )
            }
        }
    }
}

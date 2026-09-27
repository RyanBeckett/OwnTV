package tv.own.owntv.features.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.dao.TrendingDao
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
import tv.own.owntv.core.metadata.MetadataImages
import tv.own.owntv.core.metadata.MetadataRepository
import tv.own.owntv.core.model.MediaType
import tv.own.owntv.core.repository.activeProfileSources
import tv.own.owntv.core.settings.SettingsRepository

/**
 * EXPERIMENTAL Netflix browse — one horizontal poster row per category, replacing the rail+grid on
 * Movies and Series. Each row is the first page of that category's paging source (same trick as the
 * Netflix Home rows), so it needs no manual-order/contextKey plumbing.
 */
/** A 4-digit year or decade ("2024", "2010's", "1990s"), with any surrounding dash/space, for [baseName]. */
private val ERA = Regex("(?i)\\s*-?\\s*((19|20)\\d{2}['’]?s|(19|20)\\d{2})\\s*")

class NetflixBrowseViewModel(
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val categoryDao: CategoryDao,
    private val sourceDao: SourceDao,
    private val settings: SettingsRepository,
    private val metadata: MetadataRepository,
    private val trendingDao: TrendingDao,
) : ViewModel() {

    data class MovieRow(val title: String, val items: List<MovieEntity>)
    data class SeriesRow(val title: String, val items: List<SeriesEntity>)

    /**
     * Detail for the focused poster, shown below its row: genre/year/rating tags plus a synopsis.
     * [backdrop] is the best landscape image for the focused card's wide state — the TMDB backdrop
     * once resolved (a proper 16:9 still), falling back to the provider's own backdrop.
     */
    data class FocusDetail(val id: Long, val tags: List<String>, val plot: String?, val backdrop: String?)

    private sealed interface FocusReq {
        data class Movie(val m: MovieEntity) : FocusReq
        data class Series(val s: SeriesEntity) : FocusReq
    }

    private val _movieRows = MutableStateFlow<List<MovieRow>>(emptyList())
    val movieRows: StateFlow<List<MovieRow>> = _movieRows.asStateFlow()

    private val _seriesRows = MutableStateFlow<List<SeriesRow>>(emptyList())
    val seriesRows: StateFlow<List<SeriesRow>> = _seriesRows.asStateFlow()

    /** Featured "critically acclaimed" title at the top of each screen — a random high-rated pick per
     *  load, with its TMDB backdrop resolved so the billboard shows a proper 16:9 still. */
    private val _featuredMovie = MutableStateFlow<tv.own.owntv.ui.components.BillboardItem?>(null)
    val featuredMovie: StateFlow<tv.own.owntv.ui.components.BillboardItem?> = _featuredMovie.asStateFlow()
    private val _featuredSeries = MutableStateFlow<tv.own.owntv.ui.components.BillboardItem?>(null)
    val featuredSeries: StateFlow<tv.own.owntv.ui.components.BillboardItem?> = _featuredSeries.asStateFlow()

    /** Top 10 movies this week — TMDB weekly trending matched to the library, in rank order. */
    private val _top10Movies = MutableStateFlow<List<MovieEntity>>(emptyList())
    val top10Movies: StateFlow<List<MovieEntity>> = _top10Movies.asStateFlow()

    private val _focus = MutableStateFlow<FocusReq?>(null)

    /**
     * Detail for the focused poster. Two tiers so the panel never sits blank waiting on TMDB: tier 1
     * is built straight off the entity (year/rating/plot — no network) and shows the instant focus
     * settles; tier 2 upgrades it with the genre tags (and a fuller synopsis) once the resolve lands,
     * which only hits the network on a first, uncached focus. transformLatest cancels a superseded
     * resolve — including any in-flight request — the moment focus moves on.
     */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val focusDetail: StateFlow<FocusDetail?> = _focus
        .debounce(FOCUS_SETTLE_MS)
        .transformLatest { req ->
            emit(instantDetail(req))
            emit(resolveDetail(req))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var prefetchJob: kotlinx.coroutines.Job? = null

    fun onFocusMovie(m: MovieEntity) {
        _focus.value = FocusReq.Movie(m)
        val row = _movieRows.value.firstOrNull { r -> r.items.any { it.id == m.id } } ?: return
        val idx = row.items.indexOfFirst { it.id == m.id }
        prefetchAhead((1..PREFETCH_AHEAD).mapNotNull { row.items.getOrNull(idx + it) }) { metadata.resolveMovie(it) }
    }

    fun onFocusSeries(s: SeriesEntity) {
        _focus.value = FocusReq.Series(s)
        val row = _seriesRows.value.firstOrNull { r -> r.items.any { it.id == s.id } } ?: return
        val idx = row.items.indexOfFirst { it.id == s.id }
        prefetchAhead((1..PREFETCH_AHEAD).mapNotNull { row.items.getOrNull(idx + it) }) { metadata.resolveSeries(it) }
    }

    /**
     * Warm the metadata cache for the next few tiles so, by the time focus reaches them, their
     * backdrop URL is already resolved and the hero can load it at once instead of after a lookup.
     */
    private fun <T> prefetchAhead(items: List<T>, resolve: suspend (T) -> Unit) {
        if (items.isEmpty()) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch { items.forEach { runCatching { resolve(it) } } }
    }

    /**
     * Network-free detail straight off the entity (no genres yet) — the instant first paint. Backdrop
     * is deliberately null here: the wide card keeps showing the poster until the single resolved
     * backdrop is ready, so there's no provider→TMDB image re-swap for the eye to catch.
     */
    private fun instantDetail(req: FocusReq?): FocusDetail? = when (req) {
        null -> null
        is FocusReq.Movie -> FocusDetail(req.m.id, tags(emptyList(), req.m.year, req.m.rating?.toDouble()), req.m.plot?.takeIf { it.isNotBlank() }, backdrop = null)
        is FocusReq.Series -> FocusDetail(req.s.id, tags(emptyList(), req.s.year, req.s.rating?.toDouble()), req.s.plot?.takeIf { it.isNotBlank() }, backdrop = null)
    }

    private suspend fun resolveDetail(req: FocusReq?): FocusDetail? = when (req) {
        null -> null
        is FocusReq.Movie -> {
            val meta = runCatching { metadata.resolveMovie(req.m) }.getOrNull()
            FocusDetail(
                id = req.m.id,
                tags = tags(genres(meta?.genresJson), req.m.year ?: meta?.year, req.m.rating?.toDouble() ?: meta?.rating),
                plot = meta?.overview?.takeIf { it.isNotBlank() } ?: req.m.plot,
                backdrop = MetadataImages.backdrop(meta?.backdropPath, size = "w780") ?: req.m.backdropUrl,
            )
        }
        is FocusReq.Series -> {
            val meta = runCatching { metadata.resolveSeries(req.s) }.getOrNull()
            FocusDetail(
                id = req.s.id,
                tags = tags(genres(meta?.genresJson), req.s.year ?: meta?.year, req.s.rating?.toDouble() ?: meta?.rating),
                plot = meta?.overview?.takeIf { it.isNotBlank() } ?: req.s.plot,
                backdrop = MetadataImages.backdrop(meta?.backdropPath, size = "w780") ?: req.s.backdropUrl,
            )
        }
    }

    private fun genres(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    private fun tags(genres: List<String>, year: Int?, rating: Double?): List<String> =
        genres.take(3) + listOfNotNull(
            year?.takeIf { it > 0 }?.toString(),
            rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
        )

    /** A random title from the (rating-sorted) pool, preferring the acclaimed ones (rating >= 7.5). */
    private fun pickAcclaimed(pool: List<MovieEntity>): MovieEntity? =
        pool.filter { (it.rating?.toDouble() ?: 0.0) >= ACCLAIMED_MIN_RATING }.ifEmpty { pool }.randomOrNull()

    @JvmName("pickAcclaimedSeries")
    private fun pickAcclaimed(pool: List<SeriesEntity>): SeriesEntity? =
        pool.filter { (it.rating?.toDouble() ?: 0.0) >= ACCLAIMED_MIN_RATING }.ifEmpty { pool }.randomOrNull()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val aps = activeProfileSources(settings, sourceDao).first()
            val movieIds = aps.movieSourceIds.ifEmpty { listOf(-1L) }
            val seriesIds = aps.seriesSourceIds.ifEmpty { listOf(-1L) }

            // Featured hero: a random critically-acclaimed title, re-rolled each load. Its backdrop is
            // resolved from TMDB (the provider rarely stores one) in the background so it doesn't hold
            // up the rows below.
            launch {
                pickAcclaimed(firstPage(movieDao.pagingAllRating(movieIds), FEATURED_POOL))?.let { m ->
                    val meta = runCatching { metadata.resolveMovie(m) }.getOrNull()
                    _featuredMovie.value = tv.own.owntv.ui.components.BillboardItem(
                        id = m.id, title = m.name,
                        backdropUrl = MetadataImages.backdrop(meta?.backdropPath, size = "w1280") ?: m.backdropUrl,
                        posterUrl = m.posterUrl, year = m.year ?: meta?.year,
                        rating = m.rating?.toDouble() ?: meta?.rating,
                        plot = meta?.overview?.takeIf { it.isNotBlank() } ?: m.plot,
                    )
                }
            }
            launch {
                pickAcclaimed(firstPage(seriesDao.pagingAllRating(seriesIds), FEATURED_POOL))?.let { s ->
                    val meta = runCatching { metadata.resolveSeries(s) }.getOrNull()
                    _featuredSeries.value = tv.own.owntv.ui.components.BillboardItem(
                        id = s.id, title = s.name,
                        backdropUrl = MetadataImages.backdrop(meta?.backdropPath, size = "w1280") ?: s.backdropUrl,
                        posterUrl = s.posterUrl, year = s.year ?: meta?.year,
                        rating = s.rating?.toDouble() ?: meta?.rating,
                        plot = meta?.overview?.takeIf { it.isNotBlank() } ?: s.plot,
                    )
                }
            }

            // Top 10 movies this week: the weekly TMDB trending items matched to the library, ranked.
            val trendingMovies = trendingDao.getItemsForSources(aps.movieSourceIds)
                .filter { it.mediaType == MediaType.MOVIE }
                .sortedBy { it.trendingRank }
                .take(10)
            val movieById = movieDao.getByIds(trendingMovies.map { it.providerItemId }).associateBy { it.id }
            // Prefer this week's trending; fall back to the library's top-rated so the row always has
            // ten good recommendations even before the trending table has matched any movies.
            _top10Movies.value = trendingMovies.mapNotNull { movieById[it.providerItemId] }
                .ifEmpty { firstPage(movieDao.pagingAllRating(movieIds), 10) }

            val movieGroups = mergedGroups(categoryDao.observe(aps.movieSourceIds, MediaType.MOVIE).first())
            _movieRows.value = movieGroups.entries.take(MAX_ROWS).mapNotNull { (title, ids) ->
                ids.flatMap { firstPage(movieDao.pagingByCategory(it), ROW_SIZE) }
                    .distinctBy { it.id }
                    .take(ROW_SIZE)
                    .takeIf { it.isNotEmpty() }
                    ?.let { MovieRow(title, it) }
            }

            val seriesGroups = mergedGroups(categoryDao.observe(aps.seriesSourceIds, MediaType.SERIES).first())
            _seriesRows.value = seriesGroups.entries.take(MAX_ROWS).mapNotNull { (title, ids) ->
                ids.flatMap { firstPage(seriesDao.pagingByCategory(it), ROW_SIZE) }
                    .distinctBy { it.id }
                    .take(ROW_SIZE)
                    .takeIf { it.isNotEmpty() }
                    ?.let { SeriesRow(title, it) }
            }
        }
    }

    /**
     * Merge duplicate/year-fragmented categories into one row each, keyed by a base name with any
     * leading or trailing 4-digit year stripped ("Drama 2024" + "Drama 2025" -> "Drama", "2024 4K
     * Movies" + "2025 4K Movies" -> "4K Movies"). Adult categories are dropped. Ordering: the base
     * names that span the most year-fragments (the major genres) come first, otherwise the provider's
     * original order is preserved — a cleaner, less repetitive list than the raw category feed.
     */
    private fun mergedGroups(cats: List<tv.own.owntv.core.database.entity.CategoryEntity>): Map<String, List<Long>> {
        val groups = LinkedHashMap<String, MutableList<Long>>()
        cats.filterNot { AdultCategoryClassifier.isAdult(it.name) }
            .forEach { groups.getOrPut(baseName(it.name)) { mutableListOf() }.add(it.id) }
        // Stable sort by fragment count desc — single-category rows keep their first-appearance order.
        return groups.entries
            .withIndex()
            .sortedWith(compareByDescending<IndexedValue<Map.Entry<String, MutableList<Long>>>> { it.value.value.size }
                .thenBy { it.index })
            .associate { it.value.key to it.value.value }
    }

    /**
     * The row a category belongs to, with year/decade era tokens removed so all the yearly and
     * decade folders of a genre collapse into one: "Drama 2024", "Drama 2010's" and "2020's Comedy"
     * all lose their era; "1980's Classics".."1930's Classics" become "Classics". Anything mentioning
     * 4K is one "4K" row (so "4K Releases" and the yearly "4K Movies" folders merge). Era tokens are
     * stripped wherever they appear, and leftover separators (- & spaces) are trimmed off the ends.
     */
    private fun baseName(name: String): String {
        val s = name.trim()
        if (s.contains("4K", ignoreCase = true)) return "4K"
        val stripped = ERA.replace(s, " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim { it == '-' || it == '&' || it.isWhitespace() }
        return stripped.ifBlank { s }
    }

    private suspend fun <T : Any> firstPage(src: PagingSource<Int, T>, n: Int): List<T> =
        (src.load(PagingSource.LoadParams.Refresh(key = null, loadSize = n, placeholdersEnabled = false))
            as? PagingSource.LoadResult.Page)?.data ?: emptyList()

    companion object {
        const val MAX_ROWS = 15
        const val ROW_SIZE = 60
        const val PREFETCH_AHEAD = 4
        const val FEATURED_POOL = 25
        const val ACCLAIMED_MIN_RATING = 7.5
        // Settle delay before resolving the focus detail — far shorter than the core's 700ms focus
        // debounce. The screen blanks the panel while focus moves, so this only needs to outlast a
        // fast D-pad sweep; on settle the network-free tier-1 detail paints almost immediately.
        const val FOCUS_SETTLE_MS = 180L
    }
}

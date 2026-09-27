package tv.own.owntv.features.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import tv.own.owntv.core.content.AdultCategoryClassifier
import tv.own.owntv.core.database.dao.CategoryDao
import tv.own.owntv.core.database.dao.MovieDao
import tv.own.owntv.core.database.dao.SeriesDao
import tv.own.owntv.core.database.dao.SourceDao
import tv.own.owntv.core.database.entity.MovieEntity
import tv.own.owntv.core.database.entity.SeriesEntity
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
) : ViewModel() {

    data class MovieRow(val title: String, val items: List<MovieEntity>)
    data class SeriesRow(val title: String, val items: List<SeriesEntity>)

    private val _movieRows = MutableStateFlow<List<MovieRow>>(emptyList())
    val movieRows: StateFlow<List<MovieRow>> = _movieRows.asStateFlow()

    private val _seriesRows = MutableStateFlow<List<SeriesRow>>(emptyList())
    val seriesRows: StateFlow<List<SeriesRow>> = _seriesRows.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val aps = activeProfileSources(settings, sourceDao).first()

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
        const val ROW_SIZE = 20
    }
}

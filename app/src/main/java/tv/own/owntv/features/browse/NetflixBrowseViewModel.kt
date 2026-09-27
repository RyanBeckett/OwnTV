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

            val movieCats = categoryDao.observe(aps.movieSourceIds, MediaType.MOVIE).first()
                .filterNot { AdultCategoryClassifier.isAdult(it.name) }
                .take(MAX_ROWS)
            _movieRows.value = movieCats.mapNotNull { cat ->
                firstPage(movieDao.pagingByCategory(cat.id), ROW_SIZE)
                    .takeIf { it.isNotEmpty() }
                    ?.let { MovieRow(cat.name, it) }
            }

            val seriesCats = categoryDao.observe(aps.seriesSourceIds, MediaType.SERIES).first()
                .filterNot { AdultCategoryClassifier.isAdult(it.name) }
                .take(MAX_ROWS)
            _seriesRows.value = seriesCats.mapNotNull { cat ->
                firstPage(seriesDao.pagingByCategory(cat.id), ROW_SIZE)
                    .takeIf { it.isNotEmpty() }
                    ?.let { SeriesRow(cat.name, it) }
            }
        }
    }

    private suspend fun <T : Any> firstPage(src: PagingSource<Int, T>, n: Int): List<T> =
        (src.load(PagingSource.LoadParams.Refresh(key = null, loadSize = n, placeholdersEnabled = false))
            as? PagingSource.LoadResult.Page)?.data ?: emptyList()

    companion object {
        const val MAX_ROWS = 15
        const val ROW_SIZE = 20
    }
}

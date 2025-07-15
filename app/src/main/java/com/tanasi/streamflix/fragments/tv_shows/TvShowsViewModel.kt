package com.tanasi.streamflix.fragments.tv_shows

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanasi.streamflix.database.AppDatabase
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.models.Genre
import com.tanasi.streamflix.providers.TmdbProvider
import com.tanasi.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import com.tanasi.streamflix.adapters.AppAdapter

class TvShowsViewModel(database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    database.tvShowDao().getByIds(state.tvShows.map { it.id })
                        .collect { emit(it) }
                }
                else -> emit(emptyList<TvShow>())
            }
        },
    ) { state, tvShowsDb ->
        when (state) {
            is State.SuccessLoading -> {
                State.SuccessLoading(
                    tvShows = state.tvShows.map { tvShow ->
                        tvShowsDb.find { it.id == tvShow.id }
                            ?.takeIf { !tvShow.isSame(it) }
                            ?.let { tvShow.copy().merge(it) }
                            ?: tvShow
                    },
                    hasMore = state.hasMore
                )

            }
            else -> state
        }
    }

    private var page = 1
    var isFiltering = false
        private set
    private var filterYear: String? = null
    private var filterGenres: List<Genre> = emptyList()

    sealed class State {
        data object Loading : State()
        data object LoadingMore : State()
        data class SuccessLoading(val tvShows: List<TvShow>, val hasMore: Boolean) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        getTvShows()
    }


    fun getTvShows() = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val tvShows = UserPreferences.currentProvider!!.getTvShows()

            page = 1

            _state.emit(State.SuccessLoading(tvShows, true))
        } catch (e: Exception) {
            Log.e("TvShowsViewModel", "getTvShows: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    fun loadMoreTvShows() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.first()
        if (currentState is State.SuccessLoading) {
            _state.emit(State.LoadingMore)

            try {
                val tvShows = UserPreferences.currentProvider!!.getTvShows(page + 1)

                page += 1

                _state.emit(
                    State.SuccessLoading(
                        tvShows = currentState.tvShows + tvShows,
                        hasMore = tvShows.isNotEmpty(),
                    )
                )
            } catch (e: Exception) {
                Log.e("TvShowsViewModel", "loadMoreTvShows: ", e)
                _state.emit(State.FailedLoading(e))
            }
        }
    }

    fun getTvShowsFiltered(year: String?, genres: List<Genre>) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)
        try {
            filterYear = year
            filterGenres = genres
            isFiltering = genres.isNotEmpty() || (!year.isNullOrEmpty() && year != "All")
            page = 1
            val provider = UserPreferences.currentProvider!!
            val yearInt = year?.toIntOrNull()
            android.util.Log.d("TvShowsViewModel", "getTvShowsFiltered: year=$year, yearInt=$yearInt, genres=${genres.map { it.id to it.name }} provider=${provider.javaClass.simpleName}")
            val tvShows = when {
                genres.isNotEmpty() && provider is TmdbProvider -> {
                    provider.discoverByGenres(
                        genres = genres.map { it.id },
                        type = "TV Show",
                        year = year,
                        page = page
                    )
                }
                genres.isEmpty() && provider is TmdbProvider && !year.isNullOrEmpty() -> {
                    provider.discoverByGenres(
                        genres = emptyList(),
                        type = "TV Show",
                        year = year,
                        page = page
                    )
                }
                else -> {
                    provider.getTvShows()
                }
            }
            val filteredTvShows = tvShows
                .filterIsInstance<TvShow>()
                .filter { tvShow ->
                    val calYear = tvShow.released?.get(java.util.Calendar.YEAR)
                    val rawYear = tvShow.releasedRaw?.takeIf { it?.length ?: 0 >= 4 }?.substring(0, 4)?.toIntOrNull()
                    val match = yearInt == null || calYear == yearInt || rawYear == yearInt
                    if (!match) {
                        android.util.Log.d("TvShowsViewModel", "Filtered out: ${tvShow.title} releasedRaw=${tvShow.releasedRaw}")
                    }
                    match
                }
            android.util.Log.d("TvShowsViewModel", "getTvShowsFiltered: filteredResults=${filteredTvShows.size}")
            _state.emit(State.SuccessLoading(filteredTvShows, filteredTvShows.isNotEmpty()))
        } catch (e: Exception) {
            Log.e("TvShowsViewModel", "getTvShowsFiltered: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    fun loadMoreTvShowsFiltered() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.first()
        if (currentState is State.SuccessLoading && isFiltering) {
            _state.emit(State.LoadingMore)
            try {
                val provider = UserPreferences.currentProvider!!
                val nextPage = page + 1
                val tvShows = when {
                    filterGenres.isNotEmpty() && provider is TmdbProvider -> {
                        provider.discoverByGenres(
                            genres = filterGenres.map { it.id },
                            type = "TV Show",
                            year = filterYear,
                            page = nextPage
                        ).filterIsInstance<TvShow>()
                    }
                    (filterYear != null && filterYear != "All") && provider is TmdbProvider -> {
                        provider.discoverByGenres(
                            genres = emptyList(),
                            type = "TV Show",
                            year = filterYear,
                            page = nextPage
                        ).filterIsInstance<TvShow>()
                    }
                    else -> emptyList()
                }
                page = nextPage
                _state.emit(
                    State.SuccessLoading(
                        tvShows = currentState.tvShows + tvShows,
                        hasMore = tvShows.isNotEmpty(),
                    )
                )
            } catch (e: Exception) {
                Log.e("TvShowsViewModel", "loadMoreTvShowsFiltered: ", e)
                _state.emit(State.FailedLoading(e))
            }
        }
    }
}
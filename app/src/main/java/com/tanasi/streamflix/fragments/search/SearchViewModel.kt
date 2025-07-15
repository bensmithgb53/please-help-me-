package com.tanasi.streamflix.fragments.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanasi.streamflix.adapters.AppAdapter
import com.tanasi.streamflix.database.AppDatabase
import com.tanasi.streamflix.models.Genre
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import com.tanasi.streamflix.providers.Provider

class SearchViewModel(database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Idle)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessSearching -> {
                    val movies = state.results
                        .filterIsInstance<Movie>()
                    database.movieDao().getByIds(movies.map { it.id })
                        .collect { emit(it) }
                }
                else -> emit(emptyList<Movie>())
            }
        },
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessSearching -> {
                    val tvShows = state.results
                        .filterIsInstance<TvShow>()
                    database.tvShowDao().getByIds(tvShows.map { it.id })
                        .collect { emit(it) }
                }
                else -> emit(emptyList<TvShow>())
            }
        },
    ) { state, moviesDb, tvShowsDb ->
        when (state) {
            is State.SuccessSearching -> {
                State.SuccessSearching(
                    results = state.results.map { item ->
                        when (item) {
                            is Movie -> moviesDb.find { it.id == item.id }
                                ?.takeIf { !item.isSame(it) }
                                ?.let { item.copy().merge(it) }
                                ?: item
                            is TvShow -> tvShowsDb.find { it.id == item.id }
                                ?.takeIf { !item.isSame(it) }
                                ?.let { item.copy().merge(it) }
                                ?: item
                            else -> item
                        }
                    },
                    hasMore = state.hasMore
                )
            }
            else -> state
        }
    }

    var query = ""
    private var page = 1

    // Store last used filters for pagination
    private var lastQuery: String = ""
    private var lastType: String = "All"
    private var lastYear: String? = null
    private var lastGenres: List<Genre> = emptyList()
    private var lastProvider: Provider? = null
    private var lastService: String = "All"

    sealed class State {
        data object Idle : State()
        data object Searching : State()
        data object SearchingMore : State()
        data class SuccessSearching(val results: List<AppAdapter.Item>, val hasMore: Boolean) : State()
        data class FailedSearching(val error: Exception) : State()
    }

    fun search(query: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Searching)

        try {
            val results = UserPreferences.currentProvider!!.search(query, 1)
                .sortedBy {
                    when (it) {
                        is Genre -> it.name
                        else -> ""
                    }
                }

            this@SearchViewModel.query = query
            page = 1

            _state.emit(State.SuccessSearching(results, true))
        } catch (e: Exception) {
            Log.e("SearchViewModel", "search: ", e)
            _state.emit(State.FailedSearching(e))
        }
    }

    fun loadMore() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.first()
        if (currentState is State.SuccessSearching) {
            _state.emit(State.SearchingMore)
            try {
                val provider = lastProvider ?: UserPreferences.currentProvider!!
                val nextPage = page + 1
                val results = when {
                    lastGenres.isNotEmpty() && provider is com.tanasi.streamflix.providers.TmdbProvider -> {
                        provider.discoverByGenres(
                            genres = lastGenres.map { it.id },
                            type = lastType,
                            year = lastYear,
                            page = nextPage
                        )
                    }
                    else -> provider.search(lastQuery, nextPage)
                }
                page = nextPage
                _state.emit(
                    State.SuccessSearching(
                        results = currentState.results + results,
                        hasMore = results.isNotEmpty(),
                    )
                )
            } catch (e: Exception) {
                Log.e("SearchViewModel", "loadMore: ", e)
                _state.emit(State.FailedSearching(e))
            }
        }
    }

    fun searchWithFilters(query: String, type: String, year: String?, genres: List<Genre>, service: String) = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Searching)

        try {
            val provider = UserPreferences.currentProvider!!
            lastQuery = query
            lastType = type
            lastYear = year
            lastGenres = genres
            lastProvider = provider
            lastService = service
            page = 1
            val results = when {
                genres.isNotEmpty() && provider is com.tanasi.streamflix.providers.TmdbProvider -> {
                    provider.discoverByGenres(
                        genres = genres.map { it.id },
                        type = type,
                        year = year,
                        page = page,
                        service = service
                    )
                }
                else -> provider.search(query, 1)
                    .sortedBy {
                        when (it) {
                            is Genre -> it.name
                            else -> ""
                        }
                    }
                    .filter { item ->
                        val typeOk = when (type) {
                            "All" -> true
                            "Movie" -> item is Movie
                            "TV Show" -> item is TvShow
                            else -> true
                        }
                        val yearOk = when {
                            year.isNullOrEmpty() || year == "All" -> true
                            item is Movie && year != null -> {
                                val releasedYear = item.released?.toString()?.take(4)
                                releasedYear == year
                            }
                            item is TvShow && year != null -> {
                                val releasedYear = item.released?.toString()?.take(4)
                                releasedYear == year
                            }
                            else -> false
                        }
                        typeOk && yearOk
                    }
            }
            _state.emit(State.SuccessSearching(results, true))
        } catch (e: Exception) {
            Log.e("SearchViewModel", "searchWithFilters: ", e)
            _state.emit(State.FailedSearching(e))
        }
    }
}
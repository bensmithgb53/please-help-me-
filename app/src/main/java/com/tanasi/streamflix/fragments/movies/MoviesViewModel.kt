package com.tanasi.streamflix.fragments.movies

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanasi.streamflix.database.AppDatabase
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.models.Genre
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
import com.tanasi.streamflix.providers.TmdbProvider
import com.tanasi.streamflix.adapters.AppAdapter

class MoviesViewModel(database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: Flow<State> = _state

    private var page = 1
    private var filterYear: String? = null
    private var filterGenres: List<Genre> = emptyList()
    var isFiltering = false
        private set

    sealed class State {
        data object Loading : State()
        data object LoadingMore : State()
        data class SuccessLoading(val movies: List<Movie>, val hasMore: Boolean) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        // Only call getMovies if not filtering and no filterYear/Genres
        if (!isFiltering && (filterYear == null || filterYear == "All") && filterGenres.isEmpty()) {
            Log.d("MoviesViewModel", "init: calling getMovies() (isFiltering=$isFiltering, filterYear=$filterYear, filterGenres=$filterGenres)")
            getMovies()
        } else {
            Log.d("MoviesViewModel", "init: NOT calling getMovies() (isFiltering=$isFiltering, filterYear=$filterYear, filterGenres=$filterGenres)")
        }
    }

    fun getMovies() = viewModelScope.launch(Dispatchers.IO) {
        Log.d("MoviesViewModel", "getMovies: called")
        _state.emit(State.Loading)

        try {
            val movies = UserPreferences.currentProvider!!.getMovies()

            page = 1

            _state.emit(State.SuccessLoading(movies, true))
        } catch (e: Exception) {
            Log.e("MoviesViewModel", "getMovies: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    fun loadMoreMovies() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.first()
        if (currentState is State.SuccessLoading) {
            _state.emit(State.LoadingMore)

            try {
                val movies = UserPreferences.currentProvider!!.getMovies(page + 1)

                page += 1

                _state.emit(
                    State.SuccessLoading(
                        movies = currentState.movies + movies,
                        hasMore = movies.isNotEmpty(),
                    )
                )
            } catch (e: Exception) {
                Log.e("MoviesViewModel", "loadMoreMovies: ", e)
                _state.emit(State.FailedLoading(e))
            }
        }
    }

    fun getMoviesFiltered(year: String?, genres: List<Genre>) = viewModelScope.launch(Dispatchers.IO) {
        Log.d("MoviesViewModel", "getMoviesFiltered: called with year=$year, genres=${genres.map { it.id to it.name }}")
        _state.emit(State.Loading)
        try {
            filterYear = year
            filterGenres = genres
            isFiltering = genres.isNotEmpty() || (!year.isNullOrEmpty() && year != "All")
            page = 1
            val provider = UserPreferences.currentProvider!!
            val yearInt = year?.toIntOrNull()
            android.util.Log.d("MoviesViewModel", "getMoviesFiltered: year=$year, yearInt=$yearInt, genres=${genres.map { it.id to it.name }} provider=${provider.javaClass.simpleName}")
            val movies = when {
                genres.isNotEmpty() && provider is TmdbProvider -> {
                    provider.discoverByGenres(
                        genres = genres.map { it.id },
                        type = "Movie",
                        year = year,
                        page = page
                    )
                }
                genres.isEmpty() && provider is TmdbProvider && !year.isNullOrEmpty() -> {
                    provider.discoverByGenres(
                        genres = emptyList(),
                        type = "Movie",
                        year = year,
                        page = page
                    )
                }
                else -> {
                    provider.getMovies()
                }
            }
            val filteredMovies = movies
                .filterIsInstance<Movie>()
                .filter { movie ->
                    val calYear = movie.released?.get(java.util.Calendar.YEAR)
                    val rawYear = movie.releasedRaw?.takeIf { it?.length ?: 0 >= 4 }?.substring(0, 4)?.toIntOrNull()
                    val match = yearInt == null || calYear == yearInt || rawYear == yearInt
                    if (!match) {
                        android.util.Log.d("MoviesViewModel", "Filtered out: ${movie.title} releasedRaw=${movie.releasedRaw}")
                    }
                    match
                }
            android.util.Log.d("MoviesViewModel", "getMoviesFiltered: filteredResults=${filteredMovies.size}")
            _state.emit(State.SuccessLoading(filteredMovies, filteredMovies.isNotEmpty()))
        } catch (e: Exception) {
            Log.e("MoviesViewModel", "getMoviesFiltered: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }

    fun loadMoreMoviesFiltered() = viewModelScope.launch(Dispatchers.IO) {
        val currentState = _state.first()
        if (currentState is State.SuccessLoading && isFiltering) {
            _state.emit(State.LoadingMore)
            try {
                val provider = UserPreferences.currentProvider!!
                val nextPage = page + 1
                val movies = when {
                    filterGenres.isNotEmpty() && provider is TmdbProvider -> {
                        provider.discoverByGenres(
                            genres = filterGenres.map { it.id },
                            type = "Movie",
                            year = filterYear,
                            page = nextPage
                        ).filterIsInstance<Movie>()
                    }
                    (filterYear != null && filterYear != "All") && provider is TmdbProvider -> {
                        provider.discoverByGenres(
                            genres = emptyList(),
                            type = "Movie",
                            year = filterYear,
                            page = nextPage
                        ).filterIsInstance<Movie>()
                    }
                    else -> emptyList()
                }
                page = nextPage
                _state.emit(
                    State.SuccessLoading(
                        movies = currentState.movies + movies,
                        hasMore = movies.isNotEmpty(),
                    )
                )
            } catch (e: Exception) {
                Log.e("MoviesViewModel", "loadMoreMoviesFiltered: ", e)
                _state.emit(State.FailedLoading(e))
            }
        }
    }
}
package com.tanasi.streamflix.fragments.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tanasi.streamflix.database.AppDatabase
import com.tanasi.streamflix.models.Category
import com.tanasi.streamflix.models.Episode
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.models.WatchItem
import com.tanasi.streamflix.utils.UserPreferences
import com.tanasi.streamflix.utils.combine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

class HomeViewModel(database: AppDatabase) : ViewModel() {

    private val _state = MutableStateFlow<State>(State.Loading)
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: Flow<State> = combine(
        _state,
        combine(
            database.movieDao().getWatchingMovies(),
            database.episodeDao().getWatchingEpisodes(),
            database.episodeDao().getNextEpisodesToWatch(),
        ) { watchingMovies, watchingEpisodes, watchNextEpisodes ->
            // Populate watchHistory for movies from legacy fields
            watchingMovies.onEach { movie ->
                val engagementTime = movie.lastEngagementTimeUtcMillis
                val playbackPosition = movie.lastPlaybackPositionMillis
                val duration = movie.durationMillis
                if (engagementTime != null && playbackPosition != null && duration != null) {
                    movie.watchHistory = WatchItem.WatchHistory(
                        lastEngagementTimeUtcMillis = engagementTime,
                        lastPlaybackPositionMillis = playbackPosition,
                        durationMillis = duration
                    )
                }
            } + watchingEpisodes.onEach { episode ->
                episode.tvShow = episode.tvShow?.let { database.tvShowDao().getById(it.id) }
                episode.season = episode.season?.let { database.seasonDao().getById(it.id) }
                // Populate watchHistory for episodes from legacy fields
                val engagementTime = episode.lastEngagementTimeUtcMillis
                val playbackPosition = episode.lastPlaybackPositionMillis
                val duration = episode.durationMillis
                if (engagementTime != null && playbackPosition != null && duration != null) {
                    episode.watchHistory = WatchItem.WatchHistory(
                        lastEngagementTimeUtcMillis = engagementTime,
                        lastPlaybackPositionMillis = playbackPosition,
                        durationMillis = duration
                    )
                }
            } + watchNextEpisodes.onEach { episode ->
                episode.tvShow = episode.tvShow?.let { database.tvShowDao().getById(it.id) }
                episode.season = episode.season?.let { database.seasonDao().getById(it.id) }
                // Populate watchHistory for episodes from legacy fields
                val engagementTime = episode.lastEngagementTimeUtcMillis
                val playbackPosition = episode.lastPlaybackPositionMillis
                val duration = episode.durationMillis
                if (engagementTime != null && playbackPosition != null && duration != null) {
                    episode.watchHistory = WatchItem.WatchHistory(
                        lastEngagementTimeUtcMillis = engagementTime,
                        lastPlaybackPositionMillis = playbackPosition,
                        durationMillis = duration
                    )
                }
            }
        },
        database.movieDao().getFavorites(),
        database.tvShowDao().getFavorites(),
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val movies = state.categories
                        .flatMap { it.list }
                        .filterIsInstance<Movie>()
                    database.movieDao().getByIds(movies.map { it.id })
                        .collect { emit(it) }
                }
                else -> emit(emptyList<Movie>())
            }
        },
        _state.transformLatest { state ->
            when (state) {
                is State.SuccessLoading -> {
                    val tvShows = state.categories
                        .flatMap { it.list }
                        .filterIsInstance<TvShow>()
                    database.tvShowDao().getByIds(tvShows.map { it.id })
                        .collect { emit(it) }
                }
                else -> emit(emptyList<TvShow>())
            }
        },
    ) { state, continueWatching, favoritesMovies, favoriteTvShows, moviesDb, tvShowsDb ->
        when (state) {
            is State.SuccessLoading -> {
                // Log the continueWatching list before creating the category
                Log.d("HomeViewModel", "[CONTINUE_WATCHING] Items: ${continueWatching.size} -> ${continueWatching.map { it.toString() }}")

                val continueWatchingCategory = Category(
                    name = Category.CONTINUE_WATCHING,
                    list = continueWatching
                        .sortedByDescending {
                            it.watchHistory?.lastEngagementTimeUtcMillis
                                ?: it.watchedDate?.timeInMillis
                        }
                        .distinctBy {
                            when (it) {
                                is Episode -> it.tvShow?.id
                                is Movie -> it.id
                                else -> null
                            }
                        },
                )

                val categories = listOfNotNull(
                    state.categories
                        .find { it.name == Category.FEATURED }
                        ?.let { category ->
                            category.copy(
                                list = category.list.map { item ->
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
                                }
                            )
                        },

                    continueWatchingCategory,

                    Category(
                        name = Category.FAVORITE_MOVIES,
                        list = favoritesMovies
                            .reversed(),
                    ),

                    Category(
                        name = Category.FAVORITE_TV_SHOWS,
                        list = favoriteTvShows
                            .reversed(),
                    ),
                ) + state.categories
                    .filter { it.name != Category.FEATURED }
                    .map { category ->
                        category.copy(
                            list = category.list.map { item ->
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
                            }
                        )
                    }

                State.SuccessLoading(categories)
            }
            else -> state
        }
    }

    sealed class State {
        data object Loading : State()
        data class SuccessLoading(val categories: List<Category>) : State()
        data class FailedLoading(val error: Exception) : State()
    }

    init {
        getHome()
    }

    fun getHome() = viewModelScope.launch(Dispatchers.IO) {
        _state.emit(State.Loading)

        try {
            val categories = UserPreferences.currentProvider!!.getHome()

            _state.emit(State.SuccessLoading(categories))
        } catch (e: Exception) {
            Log.e("HomeViewModel", "getHome: ", e)
            _state.emit(State.FailedLoading(e))
        }
    }
}
package com.tanasi.streamflix.fragments.movie

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.tanasi.streamflix.adapters.AppAdapter
import com.tanasi.streamflix.database.AppDatabase
import com.tanasi.streamflix.databinding.FragmentMovieMobileBinding
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.ui.SpacingItemDecoration
import com.tanasi.streamflix.utils.dp
import com.tanasi.streamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import android.os.Parcelable

class MovieMobileFragment : Fragment() {

    private var _binding: FragmentMovieMobileBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<MovieMobileFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MovieViewModel(args.id, database) }

    private val appAdapter = AppAdapter()

    // Flag to prevent infinite auto-navigation loops
    private var hasAutoNavigated = false
    private var movieListState: Parcelable? = null
    private var lastLoadedId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMovieMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Auto-navigate to PlayerWebViewFragment if lastWatchedUrl is provided (from Continue Watching)
        if (args.lastWatchedUrl != null && !hasAutoNavigated) {
            val movie = database.movieDao().getById(args.id)
            if (movie != null) {
                // Clear the lastWatchedUrl argument to prevent infinite loops
                findNavController().currentBackStackEntry?.arguments?.putString("lastWatchedUrl", null)
                findNavController().currentBackStackEntry?.arguments?.putString("lastWatchedSourceId", null)
                
                findNavController().navigate(
                    MovieMobileFragmentDirections.actionMovieToPlayerWebView(
                        url = args.lastWatchedUrl ?: "",
                        id = movie.id,
                        title = movie.title,
                        subtitle = (movie.released as? java.util.Date)?.let { SimpleDateFormat("yyyy", Locale.ROOT).format(it) } ?: "",
                        videoType = com.tanasi.streamflix.models.Video.Type.Movie(
                            id = movie.id,
                            title = movie.title,
                            releaseDate = (movie.released as? java.util.Date)?.let { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(it) } ?: "",
                            poster = movie.poster ?: "",
                        ),
                        sourceId = args.lastWatchedSourceId
                    )
                )
                hasAutoNavigated = true
                return
            }
        }

        initializeMovie()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    MovieViewModel.State.Loading -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is MovieViewModel.State.SuccessLoading -> {
                        displayMovie(state.movie)
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is MovieViewModel.State.FailedLoading -> {
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.isLoading.apply {
                            pbIsLoading.visibility = View.GONE
                            gIsLoadingRetry.visibility = View.VISIBLE
                            btnIsLoadingRetry.setOnClickListener {
                                viewModel.getMovie(args.id)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        movieListState = binding.rvMovie.layoutManager?.onSaveInstanceState()
    }

    override fun onResume() {
        super.onResume()
        // Removed scroll state restore from here
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun initializeMovie() {
        binding.rvMovie.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(20.dp(requireContext()))
            )
            // Removed adapter-based state restore
        }
    }

    private fun displayMovie(movie: Movie) {
        // Clear scroll state if loading a different movie
        if (lastLoadedId != movie.id) {
            movieListState = null
            lastLoadedId = movie.id
        }
        Glide.with(requireContext())
            .load(movie.banner)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivMovieBanner)

        appAdapter.submitList(listOfNotNull(
            movie.apply { itemType = AppAdapter.Type.MOVIE_MOBILE },

            movie.takeIf { it.cast.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.MOVIE_CAST_MOBILE },

            movie.takeIf { it.recommendations.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.MOVIE_RECOMMENDATIONS_MOBILE },
        ))
        // Restore scroll state after data is set, only if valid
        if (movieListState != null && appAdapter.itemCount > 0) {
            try {
                binding.rvMovie.layoutManager?.onRestoreInstanceState(movieListState)
            } catch (e: Exception) {
                android.util.Log.w("MovieMobileFragment", "Failed to restore scroll state", e)
            }
            movieListState = null
        }
    }
}
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
import com.tanasi.streamflix.databinding.FragmentMovieTvBinding
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import android.os.Parcelable

// If you have a format extension, import it here, e.g.:
// import com.tanasi.streamflix.utils.format
// If not, fallback to SimpleDateFormat where needed.

class MovieTvFragment : Fragment() {

    private var _binding: FragmentMovieTvBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<MovieTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MovieViewModel(args.id, database) }

    private val appAdapter = AppAdapter()

    // Removed movieListState and lastLoadedId for TV fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMovieTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Auto-navigate to PlayerWebViewFragment if lastWatchedUrl is provided (from Continue Watching)
        if (args.lastWatchedUrl != null) {
            val movie = database.movieDao().getById(args.id)
            if (movie != null) {
                // Clear the lastWatchedUrl argument to prevent infinite loops
                findNavController().currentBackStackEntry?.arguments?.putString("lastWatchedUrl", null)
                findNavController().currentBackStackEntry?.arguments?.putString("lastWatchedSourceId", null)
                
                findNavController().navigate(
                    MovieTvFragmentDirections.actionMovieToPlayerWebView(
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
                            btnIsLoadingRetry.requestFocus()
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Removed scroll state restore from here
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
        binding.vgvMovie.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            setItemSpacing(80)
            // Removed adapter-based state restore
        }
    }

    private fun displayMovie(movie: Movie) {
        Glide.with(requireContext())
            .load(movie.banner)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivMovieBanner)

        // Clear focus before updating data
        binding.vgvMovie.clearFocus()

        appAdapter.submitList(listOfNotNull(
            movie.apply { itemType = AppAdapter.Type.MOVIE_TV },

            movie.takeIf { it.cast.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.MOVIE_CAST_TV },

            movie.takeIf { it.recommendations.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.MOVIE_RECOMMENDATIONS_TV },
        ))
        // No scroll state restore for TV
    }
}
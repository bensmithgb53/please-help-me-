package com.tanasi.streamflix.fragments.tv_show

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
import com.tanasi.streamflix.databinding.FragmentTvShowTvBinding
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.utils.viewModelsFactory
import kotlinx.coroutines.launch
import android.os.Parcelable

class TvShowTvFragment : Fragment() {

    private var _binding: FragmentTvShowTvBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<TvShowTvFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { TvShowViewModel(args.id, database) }

    private val appAdapter = AppAdapter()
    
    // Flag to prevent infinite auto-navigation loops
    private var hasAutoNavigated = false

    // Removed tvShowListState and lastLoadedId for TV fragment

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvShowTvBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeTvShow()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    TvShowViewModel.State.Loading -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    is TvShowViewModel.State.SuccessLoading -> {
                        displayTvShow(state.tvShow)
                        binding.isLoading.root.visibility = View.GONE
                        
                        // Auto-navigate to PlayerWebViewFragment if lastWatchedUrl is provided (from Continue Watching)
                        // Only run this after the TV show data is loaded with episodes
                        if (args.lastWatchedUrl != null && !hasAutoNavigated) {
                            android.util.Log.d("TvShowAutoNav", "args.lastWatchedUrl=${args.lastWatchedUrl}, args.lastWatchedSourceId=${args.lastWatchedSourceId}")
                            val tvShow = state.tvShow
                            android.util.Log.d("TvShowAutoNav", "tvShow found: ${tvShow != null}")
                            if (tvShow != null) {
                                android.util.Log.d("TvShowAutoNav", "tvShow seasons count: ${tvShow.seasons.size}")
                                val allEpisodes = tvShow.seasons.flatMap { it.episodes }
                                android.util.Log.d("TvShowAutoNav", "total episodes count: ${allEpisodes.size}")
                                
                                // Find the episode that was being watched (either by lastWatchedUrl or by having watchHistory)
                                val episodeWithUrl = allEpisodes.find { it.lastWatchedUrl == args.lastWatchedUrl }
                                android.util.Log.d("TvShowAutoNav", "episode with matching URL: ${episodeWithUrl?.id}")
                                
                                val episodeWithHistory = allEpisodes.find { it.watchHistory != null }
                                android.util.Log.d("TvShowAutoNav", "episode with watch history: ${episodeWithHistory?.id}")
                                
                                val firstEpisode = allEpisodes.firstOrNull()
                                android.util.Log.d("TvShowAutoNav", "first episode: ${firstEpisode?.id}")
                                
                                val episode = episodeWithUrl ?: episodeWithHistory ?: firstEpisode
                                
                                android.util.Log.d("TvShowAutoNav", "episode found: ${episode?.id}")
                                if (episode != null) {
                                    android.util.Log.d("TvShowAutoNav", "Auto-navigating to WebView for episode id=${episode.id}")
                                    
                                    // Clear the lastWatchedUrl argument to prevent infinite loops
                                    val currentArgs = args
                                    val newArgs = TvShowTvFragmentArgs(
                                        id = currentArgs.id,
                                        lastWatchedUrl = null,
                                        lastWatchedSourceId = null
                                    )
                                    
                                    findNavController().navigate(
                                        TvShowTvFragmentDirections.actionTvShowToPlayerWebView(
                                            url = args.lastWatchedUrl ?: "",
                                            id = episode.id,
                                            title = tvShow.title,
                                            subtitle = episode.season?.takeIf { it.number != 0 }?.let { season ->
                                                requireContext().getString(
                                                    com.tanasi.streamflix.R.string.player_subtitle_tv_show,
                                                    season.number,
                                                    episode.number,
                                                    episode.title ?: requireContext().getString(
                                                        com.tanasi.streamflix.R.string.episode_number,
                                                        episode.number
                                                    )
                                                )
                                            } ?: requireContext().getString(
                                                com.tanasi.streamflix.R.string.player_subtitle_tv_show_episode_only,
                                                episode.number,
                                                episode.title ?: requireContext().getString(
                                                    com.tanasi.streamflix.R.string.episode_number,
                                                    episode.number
                                                )
                                            ),
                                            videoType = com.tanasi.streamflix.models.Video.Type.Episode(
                                                id = episode.id,
                                                number = episode.number,
                                                title = episode.title,
                                                poster = episode.poster,
                                                tvShow = com.tanasi.streamflix.models.Video.Type.Episode.TvShow(
                                                    id = tvShow.id,
                                                    title = tvShow.title,
                                                    poster = tvShow.poster,
                                                    banner = tvShow.banner
                                                ),
                                                season = com.tanasi.streamflix.models.Video.Type.Episode.Season(
                                                    number = episode.season!!.number,
                                                    title = episode.season!!.title
                                                )
                                            ),
                                            sourceId = args.lastWatchedSourceId
                                        )
                                    )
                                    
                                    // Update the current destination arguments to clear lastWatchedUrl
                                    findNavController().currentBackStackEntry?.arguments?.putString("lastWatchedUrl", null)
                                    findNavController().currentBackStackEntry?.arguments?.putString("lastWatchedSourceId", null)
                                    
                                    hasAutoNavigated = true
                                    return@collect
                                } else {
                                    android.util.Log.d("TvShowAutoNav", "No episode found, cannot navigate to WebView")
                                }
                            }
                        }
                    }
                    is TvShowViewModel.State.FailedLoading -> {
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                        binding.isLoading.apply {
                            pbIsLoading.visibility = View.GONE
                            gIsLoadingRetry.visibility = View.VISIBLE
                            btnIsLoadingRetry.setOnClickListener {
                                viewModel.getTvShow(args.id)
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


    private fun initializeTvShow() {
        binding.vgvTvShow.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            setItemSpacing(80)
            // Removed adapter-based state restore
        }
    }

    private fun displayTvShow(tvShow: TvShow) {
        Glide.with(requireContext())
            .load(tvShow.banner)
            .transition(DrawableTransitionOptions.withCrossFade())
            .into(binding.ivTvShowBanner)

        // Clear focus before updating data
        binding.vgvTvShow.clearFocus()

        appAdapter.submitList(listOfNotNull(
            tvShow.apply { itemType = AppAdapter.Type.TV_SHOW_TV },
            tvShow.takeIf { it.seasons.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.TV_SHOW_SEASONS_TV },
            tvShow.takeIf { it.cast.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.TV_SHOW_CAST_TV },
            tvShow.takeIf { it.recommendations.isNotEmpty() }
                ?.copy()
                ?.apply { itemType = AppAdapter.Type.TV_SHOW_RECOMMENDATIONS_TV },
        ))
        // No scroll state restore for TV
    }
}
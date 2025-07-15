package com.tanasi.streamflix.fragments.tv_shows

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tanasi.streamflix.R
import com.tanasi.streamflix.adapters.AppAdapter
import com.tanasi.streamflix.database.AppDatabase
import com.tanasi.streamflix.databinding.FragmentTvShowsMobileBinding
import com.tanasi.streamflix.models.Genre
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.ui.SpacingItemDecoration
import com.tanasi.streamflix.utils.dp
import com.tanasi.streamflix.utils.viewModelsFactory
import com.tanasi.streamflix.utils.UserPreferences
import kotlinx.coroutines.launch
import java.util.Calendar

class TvShowsMobileFragment : Fragment() {

    private var _binding: FragmentTvShowsMobileBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { TvShowsViewModel(database) }

    private val appAdapter = AppAdapter()

    private var selectedYear: String? = null
    private var selectedGenres: List<Genre> = emptyList()
    private var allGenres: List<Genre> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTvShowsMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeTvShows()
        initializeFilters()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    TvShowsViewModel.State.Loading -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    TvShowsViewModel.State.LoadingMore -> appAdapter.isLoading = true
                    is TvShowsViewModel.State.SuccessLoading -> {
                        displayTvShows(state.tvShows, state.hasMore)
                        appAdapter.isLoading = false
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is TvShowsViewModel.State.FailedLoading -> {
                        Toast.makeText(
                            requireContext(),
                            state.error.message ?: "",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (appAdapter.isLoading) {
                            appAdapter.isLoading = false
                        } else {
                            binding.isLoading.apply {
                                pbIsLoading.visibility = View.GONE
                                gIsLoadingRetry.visibility = View.VISIBLE
                                btnIsLoadingRetry.setOnClickListener {
                                    viewModel.getTvShows()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


    private fun initializeTvShows() {
        binding.rvTvShows.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(10.dp(requireContext()))
            )
        }
    }

    private fun initializeFilters() {
        // Year Spinner
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (currentYear downTo (currentYear - 49)).map { it.toString() }.toMutableList()
        years.add(0, "All")
        val yearAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, years)
        yearAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerYear.adapter = yearAdapter
        binding.spinnerYear.setSelection(0)
        binding.spinnerYear.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: View?, position: Int, id: Long) {
                selectedYear = if (position == 0) null else years[position]
                viewModel.getTvShowsFiltered(selectedYear, selectedGenres)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Genre Multi-select Dialog
        binding.btnGenreSelect.setOnClickListener {
            if (allGenres.isEmpty()) {
                // Fetch genres from provider (like search page)
                lifecycleScope.launch {
                    allGenres = UserPreferences.currentProvider?.search("")?.filterIsInstance<Genre>() ?: emptyList()
                    showGenreDialog()
                }
            } else {
                showGenreDialog()
            }
        }
    }

    private fun showGenreDialog() {
        val genreNames = allGenres.map { it.name }.toTypedArray()
        val checkedItems = allGenres.map { selectedGenres.contains(it) }.toBooleanArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Genres")
            .setMultiChoiceItems(genreNames, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("OK") { _, _ ->
                selectedGenres = allGenres.filterIndexed { idx, _ -> checkedItems[idx] }
                viewModel.getTvShowsFiltered(selectedYear, selectedGenres)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun displayTvShows(tvShows: List<TvShow>, hasMore: Boolean) {
        appAdapter.submitList(tvShows.onEach { tvShow ->
            tvShow.itemType = AppAdapter.Type.TV_SHOW_GRID_MOBILE_ITEM
        })

        if (hasMore) {
            appAdapter.setOnLoadMoreListener {
                if (viewModel.isFiltering) viewModel.loadMoreTvShowsFiltered() else viewModel.loadMoreTvShows()
            }
        } else {
            appAdapter.setOnLoadMoreListener(null)
        }

        val noResultsView = view?.findViewById<View>(R.id.no_results_view)
        noResultsView?.visibility = if (tvShows.isEmpty() && viewModel.isFiltering) View.VISIBLE else View.GONE
    }
}
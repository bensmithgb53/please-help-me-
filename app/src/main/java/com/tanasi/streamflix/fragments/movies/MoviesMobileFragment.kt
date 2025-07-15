package com.tanasi.streamflix.fragments.movies

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
import com.tanasi.streamflix.databinding.FragmentMoviesMobileBinding
import com.tanasi.streamflix.models.Genre
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.ui.SpacingItemDecoration
import com.tanasi.streamflix.utils.dp
import com.tanasi.streamflix.utils.viewModelsFactory
import com.tanasi.streamflix.utils.UserPreferences
import kotlinx.coroutines.launch
import java.util.Calendar

class MoviesMobileFragment : Fragment() {

    private var _binding: FragmentMoviesMobileBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { MoviesViewModel(database) }

    private val appAdapter = AppAdapter()

    private var selectedYear: String? = null
    private var selectedGenres: List<Genre> = emptyList()
    private var allGenres: List<Genre> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMoviesMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeMovies()
        initializeFilters()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    MoviesViewModel.State.Loading -> binding.isLoading.apply {
                        root.visibility = View.VISIBLE
                        pbIsLoading.visibility = View.VISIBLE
                        gIsLoadingRetry.visibility = View.GONE
                    }
                    MoviesViewModel.State.LoadingMore -> appAdapter.isLoading = true
                    is MoviesViewModel.State.SuccessLoading -> {
                        displayMovies(state.movies, state.hasMore)
                        appAdapter.isLoading = false
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is MoviesViewModel.State.FailedLoading -> {
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
                                    viewModel.getMovies()
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


    private fun initializeMovies() {
        binding.rvMovies.apply {
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
                triggerFilteredMovies()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }

        // Genre Button
        binding.btnGenreSelect.setOnClickListener {
            if (allGenres.isEmpty()) {
                viewLifecycleOwner.lifecycleScope.launch {
                    allGenres = fetchAllGenres()
                    showGenreDialog()
                }
            } else {
                showGenreDialog()
            }
        }
    }

    private suspend fun fetchAllGenres(): List<Genre> {
        return try {
            UserPreferences.currentProvider?.search("")?.filterIsInstance<Genre>()?.sortedBy { it.name } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun showGenreDialog() {
        val genreNames = allGenres.map { it.name }.toTypedArray()
        val checkedItems = allGenres.map { g -> selectedGenres.any { it.id == g.id } }.toBooleanArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Genres")
            .setMultiChoiceItems(genreNames, checkedItems) { _, which, isChecked ->
                val genre = allGenres[which]
                selectedGenres = if (isChecked) {
                    selectedGenres + genre
                } else {
                    selectedGenres - genre
                }
            }
            .setPositiveButton("OK") { dialog, _ ->
                triggerFilteredMovies()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun triggerFilteredMovies() {
        viewModel.getMoviesFiltered(selectedYear, selectedGenres)
    }

    private fun displayMovies(movies: List<Movie>, hasMore: Boolean) {
        // Log the movies being displayed for debugging
        android.util.Log.d("MoviesMobileFragment", "Displaying movies: " + movies.joinToString { it.title + " (" + it.releasedRaw + ")" })

        appAdapter.submitList(movies.map { movie ->
            movie.copy().apply { itemType = AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM }
        })

        if (hasMore) {
            appAdapter.setOnLoadMoreListener {
                if (viewModel.isFiltering) viewModel.loadMoreMoviesFiltered() else viewModel.loadMoreMovies()
            }
        } else {
            appAdapter.setOnLoadMoreListener(null)
        }

        // Show 'No Results' message if list is empty and filtering
        val noResultsView = view?.findViewById<View>(R.id.no_results_view)
        noResultsView?.visibility = if (movies.isEmpty() && viewModel.isFiltering) View.VISIBLE else View.GONE
    }
}
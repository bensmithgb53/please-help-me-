package com.tanasi.streamflix.fragments.search

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.tanasi.streamflix.adapters.AppAdapter
import com.tanasi.streamflix.adapters.AutocompleteAdapter
import com.tanasi.streamflix.database.AppDatabase
import com.tanasi.streamflix.databinding.FragmentSearchMobileBinding
import com.tanasi.streamflix.models.Genre
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.models.TvShow
import com.tanasi.streamflix.ui.SpacingItemDecoration
import com.tanasi.streamflix.utils.dp
import com.tanasi.streamflix.utils.hideKeyboard
import com.tanasi.streamflix.utils.viewModelsFactory
import com.tanasi.streamflix.utils.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.fragment.findNavController

class SearchMobileFragment : Fragment() {

    private var _binding: FragmentSearchMobileBinding? = null
    private val binding get() = _binding!!

    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private val viewModel by viewModelsFactory { SearchViewModel(database) }

    private var appAdapter = AppAdapter()
    private var autocompleteAdapter = AutocompleteAdapter { item ->
        when (item) {
            is com.tanasi.streamflix.models.Movie -> {
                hideAutocomplete()
                findNavController().navigate(
                    SearchMobileFragmentDirections.actionSearchToMovie(
                        id = item.id,
                        lastWatchedUrl = null,
                        lastWatchedSourceId = null
                    )
                )
            }
            is com.tanasi.streamflix.models.TvShow -> {
                hideAutocomplete()
                findNavController().navigate(
                    SearchMobileFragmentDirections.actionSearchToTvShow(
                        id = item.id,
                        lastWatchedUrl = null,
                        lastWatchedSourceId = null
                    )
                )
            }
            else -> {
                val title = when (item) {
                    is com.tanasi.streamflix.models.Movie -> item.title
                    is com.tanasi.streamflix.models.TvShow -> item.title
                    else -> ""
                }
                binding.etSearch.setText(title)
                binding.etSearch.setSelection(title.length)
                hideAutocomplete()
                viewModel.search(title)
            }
        }
    }
    private var autocompleteJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchMobileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeSearch()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.state.flowWithLifecycle(lifecycle, Lifecycle.State.STARTED).collect { state ->
                when (state) {
                    SearchViewModel.State.Idle -> {
                        binding.isLoading.root.visibility = View.GONE
                        binding.rvSearch.adapter = AppAdapter().also {
                            appAdapter = it
                        }
                    }
                    SearchViewModel.State.Searching -> {
                        binding.isLoading.apply {
                            root.visibility = View.VISIBLE
                            pbIsLoading.visibility = View.VISIBLE
                            gIsLoadingRetry.visibility = View.GONE
                        }
                        binding.rvSearch.adapter = AppAdapter().also {
                            appAdapter = it
                        }
                    }
                    SearchViewModel.State.SearchingMore -> appAdapter.isLoading = true
                    is SearchViewModel.State.SuccessSearching -> {
                        displaySearch(state.results, state.hasMore)
                        appAdapter.isLoading = false
                        binding.isLoading.root.visibility = View.GONE
                    }
                    is SearchViewModel.State.FailedSearching -> {
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
                                    viewModel.search(viewModel.query)
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


    private fun initializeSearch() {
        binding.etSearch.apply {
            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_NEXT,
                    EditorInfo.IME_ACTION_DONE -> {
                        viewModel.search(text.toString())
                        hideKeyboard()
                        hideAutocomplete()
                        true
                    }
                    else -> false
                }
            }
            
            // Add text change listener for autocomplete
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val query = s?.toString() ?: ""
                    if (query.length >= 2) {
                        fetchAutocompleteSuggestions(query)
                    } else {
                        hideAutocomplete()
                    }
                }
            })
        }

        binding.btnSearchClear.setOnClickListener {
            binding.etSearch.setText("")
            viewModel.search("")
            hideAutocomplete()
        }

        // Initialize autocomplete RecyclerView
        binding.rvAutocomplete.apply {
            adapter = autocompleteAdapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }

        binding.rvSearch.apply {
            adapter = appAdapter.apply {
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            }
            addItemDecoration(
                SpacingItemDecoration(10.dp(requireContext()))
            )
        }
    }

    private fun fetchAutocompleteSuggestions(query: String) {
        autocompleteJob?.cancel()
        autocompleteJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250) // debounce
            try {
                val results = UserPreferences.currentProvider?.search(query, 1)
                    ?.filterIsInstance<com.tanasi.streamflix.adapters.AppAdapter.Item>()
                    ?.filter {
                        (it is com.tanasi.streamflix.models.Movie || it is com.tanasi.streamflix.models.TvShow) &&
                        (it as? com.tanasi.streamflix.models.Movie)?.title?.contains(query, ignoreCase = true) == true ||
                        (it as? com.tanasi.streamflix.models.TvShow)?.title?.contains(query, ignoreCase = true) == true
                    }
                    ?.take(10)
                    ?: emptyList()
                if (results.isNotEmpty()) {
                    autocompleteAdapter.updateSuggestions(results)
                    binding.rvAutocomplete.visibility = android.view.View.VISIBLE
                } else {
                    hideAutocomplete()
                }
            } catch (e: Exception) {
                hideAutocomplete()
            }
        }
    }

    private fun hideAutocomplete() {
        binding.rvAutocomplete.visibility = android.view.View.GONE
    }

    private fun displaySearch(list: List<AppAdapter.Item>, hasMore: Boolean) {
        // Filter out Genre items to remove colored genre boxes
        val filteredList = list.filterNot { it is Genre }
        
        appAdapter.submitList(filteredList.onEach {
            when (it) {
                is Movie -> it.itemType = AppAdapter.Type.MOVIE_GRID_MOBILE_ITEM
                is TvShow -> it.itemType = AppAdapter.Type.TV_SHOW_GRID_MOBILE_ITEM
            }
        })

        if (hasMore) {
            appAdapter.setOnLoadMoreListener { viewModel.loadMore() }
        } else {
            appAdapter.setOnLoadMoreListener(null)
        }
    }
}
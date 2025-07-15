package com.tanasi.streamflix.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.tanasi.streamflix.databinding.ItemAutocompleteSuggestionBinding

class AutocompleteAdapter(
    private val onSuggestionClick: (AppAdapter.Item) -> Unit
) : RecyclerView.Adapter<AutocompleteAdapter.ViewHolder>() {

    private var suggestions: List<AppAdapter.Item> = emptyList()

    fun updateSuggestions(newSuggestions: List<AppAdapter.Item>) {
        suggestions = newSuggestions
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAutocompleteSuggestionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(suggestions[position])
    }

    override fun getItemCount(): Int = suggestions.size

    inner class ViewHolder(
        private val binding: ItemAutocompleteSuggestionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: AppAdapter.Item) {
            val context = binding.root.context
            val (title, poster) = when (item) {
                is com.tanasi.streamflix.models.Movie -> item.title to item.poster
                is com.tanasi.streamflix.models.TvShow -> item.title to item.poster
                else -> "" to null
            }
            binding.tvSuggestionTitle.text = title
            val posterUrl = if (poster.isNullOrEmpty()) com.tanasi.streamflix.R.drawable.ic_movie_placeholder else poster
            Glide.with(context)
                .load(posterUrl)
                .placeholder(com.tanasi.streamflix.R.drawable.ic_movie_placeholder)
                .centerCrop()
                .into(binding.ivSuggestionPoster)
            binding.root.setOnClickListener {
                onSuggestionClick(item)
            }
        }
    }
} 
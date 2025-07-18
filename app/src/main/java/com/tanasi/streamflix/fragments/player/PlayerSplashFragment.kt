package com.tanasi.streamflix.fragments.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.tanasi.streamflix.R
import com.tanasi.streamflix.databinding.FragmentPlayerSplashBinding
import com.tanasi.streamflix.models.Video

class PlayerSplashFragment : Fragment() {

    private var _binding: FragmentPlayerSplashBinding? = null
    private val binding get() = _binding!!

    private val args by navArgs<PlayerSplashFragmentArgs>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPlayerSplashBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupClickListeners()
        setupTVNavigation()
        loadBackgroundImage()
    }

    private fun setupUI() {
        // Set title and subtitle
        binding.tvTitle.text = args.title
        binding.tvSubtitle.text = args.subtitle
    }

    private fun setupTVNavigation() {
        // Set initial focus for TV navigation
        binding.btnBack.requestFocus()
    }

    private fun loadBackgroundImage() {
        val backgroundImageUrl = when (val videoType = args.videoType) {
            is Video.Type.Movie -> videoType.poster
            is Video.Type.Episode -> videoType.tvShow.banner ?: videoType.tvShow.poster
        }

        if (!backgroundImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(backgroundImageUrl)
                .placeholder(R.drawable.glide_fallback_cover)
                .error(R.drawable.glide_fallback_cover)
                .centerCrop()
                .into(binding.ivBackground)
        }
    }

    private fun setupClickListeners() {
        // Back button
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        val videoType = args.videoType
        val movieId = (videoType as? Video.Type.Movie)?.id
        val tvShowId = (videoType as? Video.Type.Episode)?.tvShow?.id
        val seasonNumber = (videoType as? Video.Type.Episode)?.season?.number
        val episodeNumber = (videoType as? Video.Type.Episode)?.number

        // 111movies
        binding.btn111movies.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://111movies.com/movie/${videoType.id}"
                is Video.Type.Episode -> "https://111movies.com/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "111movies")
        }
        // Vidsrc.vip
        binding.btnVidsrcVip.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://vidsrc.vip/embed/movie/${videoType.id}"
                is Video.Type.Episode -> "https://vidsrc.vip/embed/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "vidsrc.vip")
        }
        // Vidsrc.su
        binding.btnVidsrcSu.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://vidsrc.su/embed/movie/${videoType.id}"
                is Video.Type.Episode -> "https://vidsrc.su/embed/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "vidsrc.su")
        }
        // Vidsrc.cc
        binding.btnVidsrcCc.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://vidsrc.cc/v2/embed/movie/${videoType.id}"
                is Video.Type.Episode -> "https://vidsrc.cc/v2/embed/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "vidsrc.cc")
        }
        // Vidlink.pro
        binding.btnVidlinkPro.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://vidlink.pro/movie/${videoType.id}"
                is Video.Type.Episode -> "https://vidlink.pro/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "vidlink.pro")
        }
        // Vidfast.pro
        binding.btnVidfastPro.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://vidfast.pro/movie/${videoType.id}"
                is Video.Type.Episode -> "https://vidfast.pro/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "vidfast.pro")
        }
        // Hyhd.org
        binding.btnHyhdOrg.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://hyhd.org/embed/${videoType.id}"
                is Video.Type.Episode -> "https://hyhd.org/embed/${videoType.tvShow.id}/${videoType.season.number}-${videoType.number}"
            }
            openInWebView(url, sourceId = "hyhd.org")
        }
        // Vidsrc.net
        binding.btnVidsrcNet.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://vidsrc.net/embed/${videoType.id}"
                is Video.Type.Episode -> "https://vidsrc.net/embed/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "vidsrc.net")
        }
        // Vidsrc.rip
        binding.btnVidsrcRip.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://vidsrc.rip/embed/movie/${videoType.id}"
                is Video.Type.Episode -> "https://vidsrc.rip/embed/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "vidsrc.rip")
        }
        // Vidjoy.pro
        binding.btnVidjoyPro.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://vidjoy.pro/embed/movie/${videoType.id}"
                is Video.Type.Episode -> "https://vidjoy.pro/embed/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "vidjoy.pro")
        }
        // filmku
        binding.btnFilmku.setOnClickListener {
            val url = when (videoType) {
                is Video.Type.Movie -> "https://filmku.stream/embed/${videoType.id}"
                is Video.Type.Episode -> "https://filmku.stream/embed/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            }
            openInWebView(url, sourceId = "filmku")
        }
    }

    private fun openInWebView(url: String, sourceId: String?) {
        val action = PlayerSplashFragmentDirections.actionPlayerSplashToPlayerWebView(
            url = url,
            id = args.id,
            title = args.title,
            subtitle = args.subtitle,
            videoType = args.videoType,
            sourceId = sourceId
        )
        findNavController().navigate(action)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 
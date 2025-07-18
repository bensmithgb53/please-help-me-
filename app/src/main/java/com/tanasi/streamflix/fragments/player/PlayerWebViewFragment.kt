package com.tanasi.streamflix.fragments.player

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.PermissionRequest
import android.view.KeyEvent
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.navArgs
import androidx.navigation.fragment.findNavController
import com.tanasi.streamflix.R
import com.tanasi.streamflix.database.AppDatabase
import com.tanasi.streamflix.models.Episode
import com.tanasi.streamflix.models.Movie
import com.tanasi.streamflix.models.Video
import com.tanasi.streamflix.models.WatchItem
import com.tanasi.streamflix.ui.CursorLayout
import java.util.Calendar
import android.util.Log
import java.lang.Runnable
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.content.res.Configuration
import java.util.regex.Pattern
import java.util.regex.Matcher
import java.io.BufferedReader
import java.io.InputStreamReader
import android.widget.Toast
import android.os.Build
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.app.AlertDialog
import com.tanasi.streamflix.BuildConfig
import android.webkit.ConsoleMessage

class PlayerWebViewFragment : Fragment() {

    private val args by navArgs<PlayerWebViewFragmentArgs>()
    private val database by lazy { AppDatabase.getInstance(requireContext()) }
    private lateinit var webView: WebView
    private lateinit var cursorLayout: CursorLayout
    // Remove all progress tracking fields
    private var foundVideoUrl = false
    private var videoUrl: String? = null
    private var videoHeaders: Map<String, String>? = null
    private var timeoutHandler: Handler? = null
    private var timeoutRunnable: Runnable? = null
    private lateinit var loadingTextView: TextView
    private var jsInjectionHandler: Handler? = null
    private var jsInjectionRunnable: Runnable? = null
    private var hasNavigatedToPlayer = false
    private var adBlockList: Set<String>? = null
    private var hasShownWebViewToUser = false

    // Custom WebView for better key handling
    private inner class VideoWebView(context: android.content.Context) : WebView(context) {
        override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
            if (event?.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        // Handle back button
                        parentFragmentManager.popBackStack()
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                        controlVideo("playPause")
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_UP -> {
                        controlVideo("volumeUp")
                        return true
                    }
                    KeyEvent.KEYCODE_VOLUME_DOWN -> {
                        controlVideo("volumeDown")
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        controlVideo("fastForward")
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        controlVideo("rewind")
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PLAY -> {
                        controlVideo("play")
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                        controlVideo("pause")
                        return true
                    }
                    KeyEvent.KEYCODE_MEDIA_STOP -> {
                        controlVideo("stop")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        controlVideo("rewind")
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        controlVideo("fastForward")
                        return true
                    }
                }
            }
            return super.dispatchKeyEvent(event)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_player_webview, container, false)
        
        // Use custom WebView
        webView = VideoWebView(requireContext())
        val webViewContainer = view.findViewById<ViewGroup>(R.id.webview_container)
        webViewContainer.addView(webView)
        // Ensure WebView is always interactive (no need to activate with remote)
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.requestFocus()
        webViewContainer.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        
        cursorLayout = view.findViewById<CursorLayout>(R.id.cursor_layout)
        
        val url = args.url
        
        // Initialize cursor layout for TV navigation
        cursorLayout.setCallback(object : CursorLayout.Callback {
            override fun onUserInteraction() {
                // Handle user interaction if needed
            }
        })
        
        // Hide the WebView from the user initially
        webView.visibility = View.GONE
        hasShownWebViewToUser = false
        
        // Add a loading message
        loadingTextView = TextView(requireContext()).apply {
            text = "Please wait… the video will start playing shortly."
            setTextColor(android.graphics.Color.WHITE)
            textSize = 20f
            setPadding(32, 32, 32, 32)
            setBackgroundColor(0xCC000000.toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        (view as ViewGroup).addView(loadingTextView)
        
        // Register the JavaScript interface before loading the page
        // Remove all progress tracking fields
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun log(message: String) {
                Log.d("PlayerWebViewFragment", "[JS] log: $message")
            }
            @android.webkit.JavascriptInterface
            fun videoStarted() {
                Log.d("PlayerWebViewFragment", "[JS] videoStarted event received")
            }
            @android.webkit.JavascriptInterface
            fun videoEnded() {
                Log.d("PlayerWebViewFragment", "[JS] videoEnded event received")
            }
            @android.webkit.JavascriptInterface
            fun controlVideo(action: String) {
                Log.d("PlayerWebViewFragment", "[JS] controlVideo: $action")
                when (action) {
                    "playPause" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){if(v.paused){v.muted=false; v.volume=1.0; v.play();}else{v.pause();}}")
                    "play" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.muted=false; v.volume=1.0; v.play();}")
                    "pause" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.pause();}")
                    "stop" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.pause(); v.currentTime=0;}")
                    "volumeUp" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.muted=false; v.volume=Math.min(1,v.volume+0.1);}")
                    "volumeDown" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.muted=false; v.volume=Math.max(0,v.volume-0.1);}")
                    "fastForward" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.currentTime+=10;}")
                    "rewind" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.currentTime-=10;}")
                }
            }
            @android.webkit.JavascriptInterface
            fun triggerPlay() {
                // Called from JS after auto-play
                Log.d("PlayerWebViewFragment", "[JS] triggerPlay called")
            }
        }, "AndroidProgress")
        
        // --- NEW: Load last watched position from DB ---
        val watchItem = when (val videoType = args.videoType as Video.Type) {
            is Video.Type.Movie -> database.movieDao().getById(videoType.id)
            is Video.Type.Episode -> database.episodeDao().getById(videoType.id)
        }
        // No progress tracking: always start from 0
        val lastPos = 0L
        // Remove all progress tracking fields
        // --- NEW: Always enable tracking ---
        // Remove all progress tracking fields
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("PlayerWebViewFragment", "[JS-INJECT] onPageFinished, scheduling JS injection")
                val delayMillis = getDelayForSource(args.sourceId)
                jsInjectionRunnable?.let { runnable ->
                    jsInjectionHandler?.removeCallbacks(runnable)
                }
                jsInjectionHandler = Handler(Looper.getMainLooper())
                jsInjectionRunnable = Runnable {
                    if (isAdded && webView.isAttachedToWindow) {
                        try {
                            injectProgressTracking()
                            injectVideoControls()
                            injectAutoPlayAndDropdownJS()
                            // --- NEW: Seek to last position after JS is injected ---
                            seekToLastPositionIfNeeded()
                        } catch (e: Exception) {
                            Log.w("PlayerWebViewFragment", "JS injection failed", e)
                        }
                    } else {
                        Log.w("PlayerWebViewFragment", "JS injection skipped: fragment not added or WebView not attached")
                    }
                }
                jsInjectionRunnable?.let { runnable ->
                    jsInjectionHandler?.postDelayed(runnable, delayMillis)
                }
            }
            override fun onLoadResource(view: WebView?, url: String) {
                processUrl(url, view)
                super.onLoadResource(view, url)
            }
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                processUrl(url, view)
                return false
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                processUrl(url, view, request.requestHeaders)
                return false
            }
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (isHostBlocked(url)) {
                    return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
                processUrl(url, view, request.requestHeaders)
                return super.shouldInterceptRequest(view, request)
            }
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                if (view == null || handler == null || error == null) return
                val builder = AlertDialog.Builder(requireContext())
                val errorMsg = when (error.primaryError) {
                    SslError.SSL_DATE_INVALID -> "The date of the certificate is invalid."
                    SslError.SSL_EXPIRED -> "The certificate has expired."
                    SslError.SSL_IDMISMATCH -> "Hostname mismatch."
                    SslError.SSL_INVALID -> "A generic SSL error occurred."
                    SslError.SSL_NOTYETVALID -> "The certificate is not yet valid."
                    SslError.SSL_UNTRUSTED -> "The certificate authority is not trusted."
                    else -> "SSL Certificate error."
                }
                val url = error.url ?: ""
                val message = StringBuilder(errorMsg)
                if (url.isNotEmpty()) {
                    message.append("\n\nURL: ").append(url)
                }
                builder.setTitle("SSL Certificate Error")
                    .setMessage(message.toString())
                    .setPositiveButton("Proceed") { dialog, _ ->
                        handler.proceed()
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        handler.cancel()
                        dialog.dismiss()
                    }
                    .setCancelable(false)
                    .show()
            }
        }
        
        // --- Enhance WebView compatibility for Cloudflare and modern sites ---
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.settings.setSupportMultipleWindows(true) // Allow popups if needed
        webView.settings.javaScriptCanOpenWindowsAutomatically = true
        webView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
        webView.settings.loadsImagesAutomatically = true
        webView.settings.blockNetworkImage = false
        webView.settings.blockNetworkLoads = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        webView.settings.setEnableSmoothTransition(true)
        webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        webView.settings.databaseEnabled = true
        webView.settings.setGeolocationEnabled(true)
        webView.settings.setNeedInitialFocus(true)
        webView.settings.setSupportZoom(true)
        webView.settings.builtInZoomControls = true
        webView.settings.displayZoomControls = false
        webView.settings.loadWithOverviewMode = true
        webView.settings.useWideViewPort = true
        // Enable cookies and third-party cookies
        android.webkit.CookieManager.getInstance().setAcceptCookie(true)
        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        // Hardware acceleration
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Network settings
        webView.settings.blockNetworkLoads = false
        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        
        // Set hardware acceleration hints
        webView.settings.setEnableSmoothTransition(true)
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                request.deny()
            }
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                val msg = consoleMessage.message()
                // Filter out noisy logs
                if (
                    msg.startsWith("[object Object]") ||
                    msg == "[object HTMLDivElement]" ||
                    msg.contains("already exists, skipping download.")
                ) {
                    return true // Suppress these logs
                }
                // Optionally, only log errors or your own debug messages
                Log.d("WebViewConsole", msg)
                return super.onConsoleMessage(consoleMessage)
            }
        }
        
        // Set up multiple key listeners for maximum compatibility
        setupKeyListeners(view)
        
        // Start timeout handler
        timeoutHandler = Handler(Looper.getMainLooper())
        timeoutRunnable = Runnable {
            if (!foundVideoUrl && !hasShownWebViewToUser) {
                // Show the WebView and make the cursor always visible and active
                webView.visibility = View.VISIBLE
                webView.requestFocus() // Force focus to WebView
                cursorLayout.isFocusable = true
                cursorLayout.isFocusableInTouchMode = true
                cursorLayout.visibility = View.VISIBLE // Always show cursor
                cursorLayout.showCursorAlways() // Ensure cursor is always visible and active
                loadingTextView.text = "Please press play if the video does not start automatically."
                hasShownWebViewToUser = true
            } else if (!foundVideoUrl) {
                loadingTextView.text = "Failed to extract video stream. Please try another source."
            }
        }
        timeoutHandler?.postDelayed(timeoutRunnable!!, 5000) // 5 seconds for hybrid approach
        
        webView.loadUrl(url)
        return view
    }

    private fun setupKeyListeners(view: View) {
        // Fragment root view key listener
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKeyEvent(keyCode, event)
                return@setOnKeyListener true
            }
            false
        }
        
        // WebView key listener
        webView.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKeyEvent(keyCode, event)
                return@setOnKeyListener true
            }
            false
        }
        
        // Cursor layout key listener
        cursorLayout.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                handleKeyEvent(keyCode, event)
                return@setOnKeyListener true
            }
            false
        }
    }

    fun handleKeyEvent(keyCode: Int, event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    // Handle back button
                    parentFragmentManager.popBackStack()
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    controlVideo("playPause")
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    controlVideo("volumeUp")
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    controlVideo("volumeDown")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    controlVideo("fastForward")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    controlVideo("rewind")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY -> {
                    controlVideo("play")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                    controlVideo("pause")
                    return true
                }
                KeyEvent.KEYCODE_MEDIA_STOP -> {
                    controlVideo("stop")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    controlVideo("rewind")
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    controlVideo("fastForward")
                    return true
                }
            }
        }
        return false
    }

    private fun controlVideo(action: String) {
        Log.d("PlayerWebViewFragment", "controlVideo: $action")
        when (action) {
            "playPause" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){if(v.paused){v.muted=false; v.volume=1.0; v.play();}else{v.pause();}}")
            "play" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.muted=false; v.volume=1.0; v.play();}")
            "pause" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.pause();}")
            "stop" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.pause(); v.currentTime=0;}")
            "volumeUp" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.muted=false; v.volume=Math.min(1,v.volume+0.1);}")
            "volumeDown" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.muted=false; v.volume=Math.max(0,v.volume-0.1);}")
            "fastForward" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.currentTime+=10;}")
            "rewind" -> evaluateVideoJS("var v=document.querySelector('video'); if(v){v.currentTime-=10;}")
        }
    }

    private fun evaluateVideoJS(jsCode: String) {
        webView.evaluateJavascript(jsCode) { result ->
            Log.d("PlayerWebViewFragment", "JS Result: $result")
        }
    }

    private fun injectVideoControls() {
        val jsCode = """
            (function() {
                // Add global video control functions
                window.videoControl = {
                    play: function() {
                        var v = document.querySelector('video');
                        if (v) {
                            v.muted = false;
                            v.volume = 1.0;
                            v.play();
                        }
                    },
                    pause: function() {
                        var v = document.querySelector('video');
                        if (v) v.pause();
                    },
                    playPause: function() {
                        var v = document.querySelector('video');
                        if (v) {
                            if (v.paused) {
                                v.muted = false;
                                v.volume = 1.0;
                                v.play();
                            } else {
                                v.pause();
                            }
                        }
                    },
                    volumeUp: function() {
                        var v = document.querySelector('video');
                        if (v) {
                            v.muted = false;
                            v.volume = Math.min(1, v.volume + 0.1);
                        }
                    },
                    volumeDown: function() {
                        var v = document.querySelector('video');
                        if (v) {
                            v.muted = false;
                            v.volume = Math.max(0, v.volume - 0.1);
                        }
                    },
                    fastForward: function() {
                        var v = document.querySelector('video');
                        if (v) v.currentTime += 10;
                    },
                    rewind: function() {
                        var v = document.querySelector('video');
                        if (v) v.currentTime -= 10;
                    }
                };
                
                // Listen for keyboard events
                document.addEventListener('keydown', function(e) {
                    switch(e.keyCode) {
                        case 32: // Space
                        case 13: // Enter
                            window.videoControl.playPause();
                            e.preventDefault();
                            break;
                        case 37: // Left arrow
                            window.videoControl.rewind();
                            e.preventDefault();
                            break;
                        case 39: // Right arrow
                            window.videoControl.fastForward();
                            e.preventDefault();
                            break;
                        case 38: // Up arrow
                            window.videoControl.volumeUp();
                            e.preventDefault();
                            break;
                        case 40: // Down arrow
                            window.videoControl.volumeDown();
                            e.preventDefault();
                            break;
                    }
                });
                
                // Auto-unmute and set max volume when video starts playing
                document.addEventListener('play', function(e) {
                    if (e.target.tagName === 'VIDEO') {
                        e.target.muted = false;
                        e.target.volume = 1.0;
                        if (window.AndroidProgress && window.AndroidProgress.videoStarted) {
                            window.AndroidProgress.videoStarted();
                        }
                    }
                }, true);
                
                if (window.AndroidProgress) {
                    window.AndroidProgress.log('Video controls injected');
                }
            })();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    private fun injectProgressTracking() {
        val jsCode = """
            (function() {
                function attachListeners(v) {
                    if (!v._listenersAttached) {
                        var events = [
                            'waiting', 'playing', 'canplay', 'canplaythrough', 'seeking', 'seeked', 'pause', 'play', 'stalled', 'ended', 'timeupdate', 'progress'
                        ];
                        events.forEach(function(e) {
                            v.addEventListener(e, function() {
                                if (window.AndroidProgress && window.AndroidProgress.log) {
                                    var buffered = (v.buffered && v.buffered.length) ? v.buffered.end(0) : 0;
                                    window.AndroidProgress.log('Video event: ' + e + ', currentTime: ' + v.currentTime + ', readyState: ' + v.readyState + ', buffered: ' + buffered);
                                }
                            });
                        });
                        v._listenersAttached = true;
                    }
                }
                function waitForVideo() {
                    var v = document.querySelector('video');
                    if (v) {
                        attachListeners(v);
                    } else {
                        setTimeout(waitForVideo, 500);
                    }
                }
                waitForVideo();
                // Also keep reporting progress
                setInterval(function() {
                    var v = document.querySelector('video');
                    if (v && window.AndroidProgress && window.AndroidProgress.updateProgress) {
                        window.AndroidProgress.updateProgress(
                            Math.floor(v.currentTime),
                            Math.floor(v.duration) || 0
                        );
                    }
                }, 15000); // Increased from 10000 to 15000 (15 seconds) to reduce database writes
            })();
        """.trimIndent()
        webView.evaluateJavascript(jsCode, null)
    }

    private fun injectAutoPlayAndDropdownJS() {
        val jsCode = """
            (function() {
                function log(msg) {
                    if (window.AndroidProgress && window.AndroidProgress.log) window.AndroidProgress.log(msg);
                }
                function simulateEvents(el) {
                    ['mousedown','mouseup','touchstart','touchend','click'].forEach(function(evt) {
                        try { el.dispatchEvent(new Event(evt, {bubbles:true,cancelable:true})); } catch(e) {}
                    });
                }
                function tryClickPlay(win, depth) {
                    if (depth > 3) return; // Prevent infinite recursion
                    var doc = win.document;
                    var selectors = [
                        'button[aria-label="Play"]',
                        'button[aria-label*="play"]',
                        'button[class*="play"]',
                        'button[id*="play"]',
                        'button',
                        '[role="button"]',
                        'video',
                        '[class*="play"]',
                        '[id*="play"]',
                        '[class*="start"]',
                        '[id*="start"]',
                        '[class*="watch"]',
                        '[id*="watch"]',
                        '[class*="video"]',
                        '[id*="video"]',
                        '[class*="btn"]',
                        '[id*="btn"]',
                        '[class*="source"]',
                        '[id*="source"]',
                        '[class*="stream"]',
                        '[id*="stream"]',
                        '[class*="player"]',
                        '[id*="player"]',
                        '[class*="start"]',
                        '[id*="start"]',
                        '[class*="go"]',
                        '[id*="go"]',
                        '[class*="vid"]',
                        '[id*="vid"]',
                        '[class*="main"]',
                        '[id*="main"]',
                        '[class*="center"]',
                        '[id*="center"]',
                        '[class*="movie"]',
                        '[id*="movie"]',
                        '[class*="episode"]',
                        '[id*="episode"]',
                        '[class*="ep"]',
                        '[id*="ep"]',
                        '[class*="src"]',
                        '[id*="src"]',
                        '[class*="file"]',
                        '[id*="file"]',
                        '[class*="media"]',
                        '[id*="media"]',
                        '[class*="container"]',
                        '[id*="container"]',
                        '[class*="content"]',
                        '[id*="content"]',
                        '[class*="main"]',
                        '[id*="main"]',
                        '[class*="center"]',
                        '[id*="center"]',
                        '[class*="movie"]',
                        '[id*="movie"]',
                        '[class*="episode"]',
                        '[id*="episode"]',
                        '[class*="ep"]',
                        '[id*="ep"]',
                        '[class*="src"]',
                        '[id*="src"]',
                        '[class*="file"]',
                        '[id*="file"]',
                        '[class*="media"]',
                        '[id*="media"]',
                        '[class*="container"]',
                        '[id*="container"]',
                        '[class*="content"]',
                        '[id*="content"]'
                    ];
                    selectors.forEach(function(sel) {
                        var btns = doc.querySelectorAll(sel);
                        btns.forEach(function(btn) {
                            try {
                                simulateEvents(btn);
                                log('Clicked play button: ' + sel);
                            } catch(e) {}
                        });
                    });
                    // Try clicking by text
                    var texts = ['play','start','watch','go','vid','main','center','movie','episode','ep','src','file','media','container','content','VidPlay','Continue','Resume'];
                    texts.forEach(function(txt) {
                        var btns = Array.from(doc.querySelectorAll('button, [role="button"]')).filter(function(el) {
                            return el.innerText && el.innerText.toLowerCase().includes(txt);
                        });
                        btns.forEach(function(btn) {
                            try {
                                simulateEvents(btn);
                                log('Clicked button by text: ' + btn.innerText);
                            } catch(e) {}
                        });
                    });
                    // Try to play all videos
                    var vids = doc.querySelectorAll('video');
                    vids.forEach(function(v) {
                        try {
                            v.muted = false;
                            v.volume = 1.0;
                            v.play();
                            log('Tried to play video element');
                        } catch(e) {}
                    });
                    // Recursively try in iframes
                    var iframes = doc.querySelectorAll('iframe');
                    iframes.forEach(function(iframe) {
                        try {
                            if (iframe.contentWindow) {
                                tryClickPlay(iframe.contentWindow, depth+1);
                            }
                        } catch(e) {}
                    });
                }
                function tryDropdowns(win, depth) {
                    if (depth > 3) return;
                    var doc = win.document;
                    var selects = doc.querySelectorAll('select');
                    selects.forEach(function(sel) {
                        if (sel.options.length > 1) {
                            sel.selectedIndex = 1;
                            sel.dispatchEvent(new Event('change', {bubbles:true}));
                            log('Dropdown changed: ' + sel.name);
                        }
                    });
                    // Recursively try in iframes
                    var iframes = doc.querySelectorAll('iframe');
                    iframes.forEach(function(iframe) {
                        try {
                            if (iframe.contentWindow) {
                                tryDropdowns(iframe.contentWindow, depth+1);
                            }
                        } catch(e) {}
                    });
                }
                function runAll() {
                    tryClickPlay(window, 0);
                    tryDropdowns(window, 0);
                    log('Auto-play JS injected and running');
                }
                // Retry logic
                var tries = 0;
                function retry() {
                    tries++;
                    runAll();
                    if (tries < 8) setTimeout(retry, 1200);
                }
                retry();
            })();
        """
        webView.evaluateJavascript(jsCode, null)
    }

    // Remove all saveWatchProgress, saveWatchProgressInternal, checkForAutoAdvance, and related methods
    private fun seekToLastPositionIfNeeded() {
        // Query your DB for last position for this videoId
        val lastPosition = 0L // get from DB
        if (lastPosition > 0) {
            webView.evaluateJavascript("var v=document.querySelector('video'); if(v){v.currentTime=" + lastPosition + ";}", null)
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // Resume WebView
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        // Remove all saveWatchProgress, saveWatchProgressInternal, checkForAutoAdvance, and related methods
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        
        // Pause WebView to save resources
        webView.onPause()
    }

    override fun onDestroyView() {
        jsInjectionRunnable?.let { runnable ->
            jsInjectionHandler?.removeCallbacks(runnable)
        }
        jsInjectionHandler = null
        jsInjectionRunnable = null
        try {
            if (webView.isAttachedToWindow) {
                try { webView.stopLoading() } catch (_: Exception) {}
                try { webView.removeAllViews() } catch (_: Exception) {}
                try { webView.clearCache(true) } catch (_: Exception) {}
                try { webView.clearHistory() } catch (_: Exception) {}
                try { webView.destroy() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("PlayerWebViewFragment", "WebView cleanup failed", e)
        }
        // No progress tracking: nothing to cancel
        // Remove all saveWatchProgress, saveWatchProgressInternal, checkForAutoAdvance, and related methods
        // Clear cookies for a fresh state
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (_: Exception) {}
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Enable WebView remote debugging in debug builds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
            Toast.makeText(requireContext(), "WebView remote debugging enabled", Toast.LENGTH_SHORT).show()
        }
        // Set focus to the cursor layout for TV navigation
        cursorLayout.requestFocus()
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Remove all saveWatchProgress, saveWatchProgressInternal, checkForAutoAdvance, and related methods
                parentFragmentManager.popBackStack()
            }
        })
        view.findViewById<View>(R.id.btn_back)?.setOnClickListener {
            // Remove all saveWatchProgress, saveWatchProgressInternal, checkForAutoAdvance, and related methods
            parentFragmentManager.popBackStack()
        }
        injectProgressTracking()
    }

    private fun isTv(): Boolean {
        val uiMode = requireContext().resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun loadExtractionPage(url: String) {
        // Clear WebView data before loading
        try { webView.clearCache(true) } catch (_: Exception) {}
        try { webView.clearHistory() } catch (_: Exception) {}
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (_: Exception) {}
        // Set User-Agent and Origin headers
        val extractionHeaders = mapOf(
            "User-Agent" to "Mozilla/5.0 (Android 10; Mobile; rv:68.0) Gecko/68.0 Firefox/68.0",
            "Origin" to "https://vidsrc.su"
        )
        webView.settings.userAgentString = extractionHeaders["User-Agent"]
        webView.loadUrl(url, extractionHeaders)
    }

    private fun onVideoUrlFound(url: String, headers: Map<String, String>?) {
        if (hasNavigatedToPlayer) {
            Log.d("PlayerWebViewFragment", "Navigation already performed, skipping.")
            return
        }
        hasNavigatedToPlayer = true
        // Clean up WebView and timeout
        timeoutHandler?.removeCallbacks(timeoutRunnable!!)
        loadingTextView.text = "Loading video…"
        // Hide the WebView again if it was shown
        webView.visibility = View.GONE
        val navController = findNavController()
        if (isTv()) {
            val action = PlayerWebViewFragmentDirections.actionPlayerWebViewToPlayerTv(
                id = args.id,
                title = args.title,
                subtitle = args.subtitle,
                videoType = args.videoType,
                videoUrl = url,
                headers = headers?.map { "${it.key}:${it.value}" }?.toTypedArray() ?: emptyArray()
            )
            try {
                Log.d("PlayerWebViewFragment", "Navigating to ExoPlayer TV with url: $url")
                if (isAdded && !isDetached) {
                    navController.navigate(action)
                } else {
                    Log.w("PlayerWebViewFragment", "Fragment not added or detached, navigation skipped")
                }
            } catch (e: Exception) {
                Log.e("PlayerWebViewFragment", "Navigation to ExoPlayer TV failed", e)
            }
        } else {
            val bundle = Bundle().apply {
                putString("id", args.id)
                putString("title", args.title)
                putString("subtitle", args.subtitle)
                putSerializable("videoType", args.videoType as java.io.Serializable)
                putString("videoUrl", url)
                putStringArray("headers", headers?.map { "${it.key}:${it.value}" }?.toTypedArray())
            }
            try {
                Log.d("PlayerWebViewFragment", "Navigating to ExoPlayer Mobile with url: $url")
                if (isAdded && !isDetached) {
                    navController.navigate(R.id.player, bundle)
                } else {
                    Log.w("PlayerWebViewFragment", "Fragment not added or detached, navigation skipped")
                }
            } catch (e: Exception) {
                Log.e("PlayerWebViewFragment", "Navigation to ExoPlayer Mobile failed", e)
            }
        }
        // Destroy WebView
        try { webView.stopLoading() } catch (_: Exception) {}
        try { webView.removeAllViews() } catch (_: Exception) {}
        try { webView.clearCache(true) } catch (_: Exception) {}
        try { webView.clearHistory() } catch (_: Exception) {}
        try { webView.destroy() } catch (_: Exception) {}
        // Clear cookies for a fresh state
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (_: Exception) {}
    }

    private fun getDelayForSource(sourceId: String?): Long {
        return when (sourceId) {
            "vidsrc.cc" -> 3000L // 3 seconds for vidsrc
            "111movies" -> 2000L // 2 seconds for 111movies
            // Add more source-specific delays as needed
            else -> 1500L // Default delay
        }
    }

    // Utility function for video MIME type detection
    private fun getVideoMimeType(uri: String?): String? {
        if (uri == null) return null
        val videoRegex = Pattern.compile("\\.(mp4|mp4v|mpv|m1v|m4v|mpg|mpg2|mpeg|xvid|webm|3gp|avi|mov|mkv|ogg|ogv|ogm|m3u8|mpd|ism(?:[vc]|/manifest)?)(?:[\\?#]|$)")
        val matcher = videoRegex.matcher(uri.lowercase())
        if (matcher.find()) {
            return when (matcher.group(1)) {
                "mp4", "mp4v", "m4v" -> "video/mp4"
                "mpv" -> "video/MPV"
                "m1v", "mpg", "mpg2", "mpeg" -> "video/mpeg"
                "xvid" -> "video/x-xvid"
                "webm" -> "video/webm"
                "3gp" -> "video/3gpp"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "mkv" -> "video/x-mkv"
                "ogg", "ogv", "ogm" -> "video/ogg"
                "m3u8" -> "application/x-mpegURL"
                "mpd" -> "application/dash+xml"
                "ism", "ism/manifest", "ismv", "ismc" -> "application/vnd.ms-sstr+xml"
                else -> null
            }
        }
        return null
    }

    // Helper to process URLs for video detection
    private fun processUrl(uri: String?, view: WebView? = null, headers: Map<String, String>? = null) {
        val mimeType = getVideoMimeType(uri)
        if (!foundVideoUrl && mimeType != null && uri != null) {
            foundVideoUrl = true
            videoUrl = uri

            // --- Inject required headers for new sources ---
            val host = try { android.net.Uri.parse(uri).host?.lowercase() ?: "" } catch (_: Exception) { "" }
            val customHeaders = when {
                host.contains("vidjoy.pro") -> mapOf(
                    "Referer" to "https://vidjoy.pro/",
                    "Origin" to "https://vidjoy.pro",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                host.contains("vidsrc.rip") -> mapOf(
                    "Referer" to "https://vidsrc.rip/",
                    "Origin" to "https://vidsrc.rip",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                host.contains("filmku.stream") -> mapOf(
                    "Referer" to "https://filmku.stream/",
                    "Origin" to "https://filmku.stream",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                )
                // Add more sources here as needed
                else -> headers ?: emptyMap()
            }
            videoHeaders = customHeaders

            Log.d("PlayerWebViewFragment", "[PROCESS_URL] Found video URL: $uri, mimeType: $mimeType")
            Handler(Looper.getMainLooper()).post {
                onVideoUrlFound(uri, videoHeaders)
            }
        }
    }

    private fun loadAdBlockList() {
        if (adBlockList != null) return
        try {
            val inputStream = requireContext().resources.openRawResource(R.raw.adblock_serverlist)
            val reader = BufferedReader(InputStreamReader(inputStream))
            adBlockList = reader.lineSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .toSet()
            reader.close()
        } catch (e: Exception) {
            adBlockList = emptySet()
        }
    }

    private fun isHostBlocked(url: String): Boolean {
        loadAdBlockList()
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase()?.trim() ?: return false
            adBlockList?.any { host == it || host.endsWith("." + it) } ?: false
        } catch (e: Exception) { false }
    }
} 
package com.example.zetayang.presentation.activity

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.zetayang.R
import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.presentation.adapter.EpisodeAdapter
import kotlinx.coroutines.launch

class EpisodeDetailActivity : AppCompatActivity() {
    
    private lateinit var playerView: PlayerView
    private lateinit var imgThumb: ImageView
    private lateinit var btnBack: ImageView
    private lateinit var tvEpisodeTitle: TextView
    private lateinit var recyclerEpisodes: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTotalEpisodes: TextView
    private lateinit var episodeHeader: LinearLayout
    private lateinit var ivExpandIcon: ImageView
    private lateinit var divider: View
    
    private var exoPlayer: ExoPlayer? = null
    private var bookId: String? = null
    private var currentVideoUrl: String? = null
    private var currentEpisodeIndex = 0
    private lateinit var episodeAdapter: EpisodeAdapter
    private var isExpanded = false
    
    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_DRAMA_TITLE = "extra_drama_title"
        const val EXTRA_COVER_URL = "extra_cover_url"
        const val EXTRA_VIDEO_URL = "extra_video_url"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system UI (navigation bar & status bar)
        hideSystemUI()
        
        setContentView(R.layout.activity_episode_detail)
        
        // Get data from intent
        bookId = intent.getStringExtra(EXTRA_BOOK_ID)
        val dramaTitle = intent.getStringExtra(EXTRA_DRAMA_TITLE) ?: "Drama"
        val coverUrl = intent.getStringExtra(EXTRA_COVER_URL)
        currentVideoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        
        initViews()
        setupUI(dramaTitle, coverUrl)
        loadEpisodes()
        
        // Play first video if available
        currentVideoUrl?.let { startExoPlayer(it) }
    }
    
    private fun hideSystemUI() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }
    
    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        imgThumb = findViewById(R.id.imgThumb)
        btnBack = findViewById(R.id.btnBack)
        tvEpisodeTitle = findViewById(R.id.tvEpisodeTitle)
        recyclerEpisodes = findViewById(R.id.recyclerEpisodes)
        progressBar = findViewById(R.id.progressBar)
        tvTotalEpisodes = findViewById(R.id.tvTotalEpisodes)
        episodeHeader = findViewById(R.id.episodeHeader)
        ivExpandIcon = findViewById(R.id.ivExpandIcon)
        divider = findViewById(R.id.divider)
    }
    
    private fun setupUI(title: String, coverUrl: String?) {
        tvEpisodeTitle.text = "Ep.1"
        
        // Load cover as thumbnail
        coverUrl?.let {
            imgThumb.load(it) {
                crossfade(true)
            }
        }
        
        // Back button
        btnBack.setOnClickListener {
            finish()
        }
        
        // Setup RecyclerView
        recyclerEpisodes.layoutManager = GridLayoutManager(this, 5)
        
        // Setup expand/collapse behavior
        episodeHeader.setOnClickListener {
            toggleEpisodeList()
        }
    }
    
    private fun toggleEpisodeList() {
        isExpanded = !isExpanded
        
        Log.d("EpisodeDetail", "🔄 Toggle clicked! isExpanded: $isExpanded")
        
        if (isExpanded) {
            // Expand - Show list
            Log.d("EpisodeDetail", "📖 Expanding list...")
            recyclerEpisodes.visibility = View.VISIBLE
            divider.visibility = View.VISIBLE
            ivExpandIcon.animate().rotation(180f).setDuration(200).start()
        } else {
            // Collapse - Hide list
            Log.d("EpisodeDetail", "📕 Collapsing list...")
            recyclerEpisodes.visibility = View.GONE
            divider.visibility = View.GONE
            ivExpandIcon.animate().rotation(0f).setDuration(200).start()
        }
    }
    
    private fun loadEpisodes() {
        val safeBookId = bookId ?: return
        
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val episodes = RetrofitClient.instance.getEpisodes(safeBookId)
                progressBar.visibility = View.GONE
                
                if (episodes.isNotEmpty()) {
                    tvTotalEpisodes.text = "≡  Total ${episodes.size} episode >"
                    
                    episodeAdapter = EpisodeAdapter(episodes) { videoUrl, position ->
                        // Play selected episode
                        currentEpisodeIndex = position
                        startExoPlayer(videoUrl)
                        
                        // Update episode title
                        tvEpisodeTitle.text = "Ep.${position + 1}"
                        
                        // Auto collapse setelah pilih episode
                        if (isExpanded) {
                            toggleEpisodeList()
                        }
                    }
                    
                    recyclerEpisodes.adapter = episodeAdapter
                    
                    // Set initial selected position
                    episodeAdapter.setSelectedPosition(0)
                } else {
                    Toast.makeText(this@EpisodeDetailActivity, "Episode kosong", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(this@EpisodeDetailActivity, "Gagal load: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EpisodeDetail", "Error: ${e.message}")
            }
        }
    }
    
    private fun startExoPlayer(url: String) {
        Log.d("EpisodeDetail", "🎬 Starting video: $url")
        
        // Release previous player
        exoPlayer?.release()
        
        try {
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(15000, 50000, 2500, 5000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
            
            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            
            val unsafeClient = RetrofitClient.getUnsafeOkHttpClient()
            val dataSourceFactory = DefaultDataSource.Factory(
                this,
                androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(unsafeClient)
            )
            
            exoPlayer = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .setLoadControl(loadControl)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                    playWhenReady = true
                    repeatMode = Player.REPEAT_MODE_ONE
                    setWakeMode(C.WAKE_MODE_NETWORK)
                    prepare()
                }
            
            playerView.player = exoPlayer
            
            exoPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> {
                            progressBar.visibility = View.VISIBLE
                        }
                        Player.STATE_READY -> {
                            progressBar.visibility = View.GONE
                        }
                    }
                }
                
                override fun onRenderedFirstFrame() {
                    // Hide thumbnail when video starts
                    imgThumb.animate()
                        .alpha(0f)
                        .setDuration(200)
                        .withEndAction {
                            imgThumb.visibility = View.GONE
                        }
                        .start()
                }
                
                override fun onPlayerError(error: PlaybackException) {
                    Log.e("EpisodeDetail", "❌ Error: ${error.message}")
                    Toast.makeText(this@EpisodeDetailActivity, "Gagal memutar video", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Log.e("EpisodeDetail", "❌ Exception: ${e.message}")
            e.printStackTrace()
        }
    }
    
    override fun onResume() {
        super.onResume()
        hideSystemUI()
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
    
    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
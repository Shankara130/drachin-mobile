package com.example.zetayang.presentation.adapter

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.zetayang.R
import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.data.model.DramaBook
import com.example.zetayang.domain.usecase.GetVideoUrlUseCase
import com.example.zetayang.presentation.sheet.EpisodeSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class VideoAdapter(
    private val dramas: List<DramaBook>,
    private val getVideoUrlUseCase: GetVideoUrlUseCase
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val urlCache = mutableMapOf<String, String>()
    private val preloadJobs = mutableMapOf<String, Job>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_feed, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val drama = dramas[position]
        holder.bind(drama, getVideoUrlUseCase, urlCache)
        
        // Preload 5 video berikutnya untuk smooth scrolling
        for (i in 1..5) {
            if (position + i < dramas.size) {
                preloadUrl(dramas[position + i].bookId)
            }
        }
    }

    private fun preloadUrl(bookId: String?) {
        if (bookId == null || urlCache.containsKey(bookId) || preloadJobs.containsKey(bookId)) {
            return
        }
        
        val job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // Timeout untuk prevent hanging forever
                withTimeout(15000) { // 15 detik max
                    val url = getVideoUrlUseCase.invoke(bookId)
                    val elapsed = System.currentTimeMillis() - startTime
                    
                    urlCache[bookId] = url
                    Log.d("VideoPreload", "✅ URL ready for $bookId in ${elapsed}ms")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("VideoPreload", "⏱️ Timeout for $bookId after 15s")
            } catch (e: Exception) {
                Log.e("VideoPreload", "❌ Failed to preload $bookId: ${e.message}")
            } finally {
                preloadJobs.remove(bookId)
            }
        }
        
        preloadJobs[bookId] = job
    }

    override fun getItemCount(): Int = dramas.size

    override fun onViewAttachedToWindow(holder: VideoViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder.absoluteAdapterPosition == 0) {
            holder.playVideo(holder.itemView.context)
        }
    }

    override fun onViewDetachedFromWindow(holder: VideoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.stopVideo()
    }

    class VideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val playerView: PlayerView = itemView.findViewById(R.id.playerView)
        private val imgThumb: ImageView = itemView.findViewById(R.id.imgThumb)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val btnEpisodes: Button = itemView.findViewById(R.id.btnEpisodes)
        private val tvDescription: TextView = itemView.findViewById(R.id.tvDescription)
        private val imgPosterSmall: ImageView = itemView.findViewById(R.id.imgPosterSmall)
        private val infoContainer: ViewGroup = itemView.findViewById(R.id.infoContainer)
        // Tambahkan ProgressBar di layout XML Anda
        private val progressBar: ProgressBar? = itemView.findViewById(R.id.progressBar)

        private var exoPlayer: ExoPlayer? = null
        private var currentBookId: String? = null
        private var useCase: GetVideoUrlUseCase? = null
        private var urlCacheRef: MutableMap<String, String>? = null 
        private var isExpanded = false
        private var loadJob: Job? = null

        fun bind(
            drama: DramaBook, 
            useCase: GetVideoUrlUseCase, 
            cache: MutableMap<String, String>
        ) {
            // Cancel previous loading job
            loadJob?.cancel()
            
            this.currentBookId = drama.bookId
            this.useCase = useCase
            this.urlCacheRef = cache

            val maxChar = 26
            val rawTitle = drama.bookName
            val displayTitle = if (rawTitle.length > maxChar) {
                "${rawTitle.take(maxChar)}... >"
            } else {
                "$rawTitle >"
            }
            tvTitle.text = displayTitle

            val deskripsi = drama.introduction ?: "Drama seru untuk ditonton"
            tvDescription.text = "Ep.1 | $deskripsi"
            
            isExpanded = false
            tvDescription.maxLines = 2
            tvDescription.ellipsize = TextUtils.TruncateAt.END
            
            tvDescription.setOnClickListener {
                TransitionManager.beginDelayedTransition(infoContainer)
                if (isExpanded) {
                    tvDescription.maxLines = 2
                    tvDescription.ellipsize = TextUtils.TruncateAt.END
                    isExpanded = false
                } else {
                    tvDescription.maxLines = 50
                    tvDescription.ellipsize = null
                    isExpanded = true
                }
            }

            val totalEps = drama.chapterCount ?: 0 
            btnEpisodes.text = "Tonton Semua ($totalEps episode)"

            imgThumb.visibility = View.VISIBLE
            imgThumb.alpha = 1f
            imgThumb.load(drama.coverUrl)
            imgPosterSmall.load(drama.coverUrl) { crossfade(true) }

            playerView.player = null
            progressBar?.visibility = View.GONE
            
            // Preload URL jika belum ada di cache
            if (currentBookId != null && !cache.containsKey(currentBookId)) {
                loadJob = CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val startTime = System.currentTimeMillis()
                        val url = useCase.invoke(currentBookId!!)
                        val elapsed = System.currentTimeMillis() - startTime
                        
                        cache[currentBookId!!] = url
                        Log.d("VideoBind", "URL loaded in ${elapsed}ms")
                    } catch (e: Exception) {
                        Log.e("VideoBind", "Failed to load URL: ${e.message}")
                    }
                }
            }
            
            btnEpisodes.setOnClickListener {
                if (currentBookId != null) {
                    // Navigate to EpisodeDetailActivity
                    val intent = android.content.Intent(
                        itemView.context,
                        com.example.zetayang.presentation.activity.EpisodeDetailActivity::class.java
                    ).apply {
                        putExtra(com.example.zetayang.presentation.activity.EpisodeDetailActivity.EXTRA_BOOK_ID, currentBookId)
                        putExtra(com.example.zetayang.presentation.activity.EpisodeDetailActivity.EXTRA_DRAMA_TITLE, drama.bookName)
                        putExtra(com.example.zetayang.presentation.activity.EpisodeDetailActivity.EXTRA_COVER_URL, drama.coverUrl)
                        putExtra(com.example.zetayang.presentation.activity.EpisodeDetailActivity.EXTRA_VIDEO_URL, urlCacheRef?.get(currentBookId))
                    }
                    itemView.context.startActivity(intent)
                }
            }
        }

        fun playVideo(context: Context) {
            val bookId = currentBookId ?: return
            val safeUseCase = useCase ?: return
            val cache = urlCacheRef

            // Show loading indicator
            progressBar?.visibility = View.VISIBLE

            loadJob = CoroutineScope(Dispatchers.IO).launch {
                var url = cache?.get(bookId)
                
                if (url == null) {
                    try {
                        val startTime = System.currentTimeMillis()
                        
                        // Timeout 15 detik untuk fetch URL
                        url = withTimeout(15000) {
                            safeUseCase.invoke(bookId)
                        }
                        
                        val elapsed = System.currentTimeMillis() - startTime
                        
                        cache?.put(bookId, url)
                        Log.d("VideoPlay", "⏱️ URL fetched in ${elapsed}ms")
                    } catch (e: TimeoutCancellationException) {
                        Log.e("VideoPlay", "⏱️ Timeout after 15s")
                        withContext(Dispatchers.Main) {
                            progressBar?.visibility = View.GONE
                            Toast.makeText(context, "Server lambat, coba lagi", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    } catch (e: Exception) {
                        Log.e("VideoPlay", "❌ Error fetching URL: ${e.message}")
                        withContext(Dispatchers.Main) {
                            progressBar?.visibility = View.GONE
                            Toast.makeText(context, "Gagal memuat video", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION && url != null) {
                        startExoPlayer(context, url)
                    } else {
                        progressBar?.visibility = View.GONE
                    }
                }
            }
        }

        private fun startExoPlayer(context: Context, url: String) {
            // Prevent duplicate initialization
            if (exoPlayer?.isPlaying == true) {
                Log.d("ExoPlayer", "⚠️ Already playing, skipping...")
                return
            }
            
            Log.d("VideoPlay", "🎬 Starting ExoPlayer with URL: $url")
            
            exoPlayer?.release()

            try {
                // Optimized LoadControl
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        15000,  // Min buffer sebelum playback
                        50000,  // Max buffer
                        2500,   // Buffer untuk mulai play
                        5000    // Buffer setelah rebuffer
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val renderersFactory = DefaultRenderersFactory(context)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

                val unsafeClient = RetrofitClient.getUnsafeOkHttpClient()
                val dataSourceFactory = DefaultDataSource.Factory(
                    context, 
                    androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(unsafeClient)
                )

                exoPlayer = ExoPlayer.Builder(context, renderersFactory)
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
                                Log.d("ExoPlayer", "⏳ Buffering...")
                                progressBar?.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                Log.d("ExoPlayer", "✅ Ready to play")
                                progressBar?.visibility = View.GONE
                            }
                            Player.STATE_ENDED -> {
                                Log.d("ExoPlayer", "🏁 Playback ended")
                            }
                            Player.STATE_IDLE -> {
                                Log.d("ExoPlayer", "💤 Idle")
                            }
                        }
                    }
                    
                    override fun onRenderedFirstFrame() { 
                        Log.d("ExoPlayer", "🎥 First frame rendered")
                        progressBar?.visibility = View.GONE
                        imgThumb.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction {
                                imgThumb.visibility = View.GONE
                            }
                            .start()
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("ExoPlayer", "❌ Error: ${error.message}")
                        progressBar?.visibility = View.GONE
                        Toast.makeText(context, "Gagal memutar video", Toast.LENGTH_SHORT).show()
                    }
                })
            } catch (e: Exception) {
                Log.e("ExoPlayer", "❌ Exception: ${e.message}")
                progressBar?.visibility = View.GONE
                e.printStackTrace()
            }
        }

        fun stopVideo() {
            loadJob?.cancel()
            exoPlayer?.release()
            exoPlayer = null
            imgThumb.visibility = View.VISIBLE
            imgThumb.alpha = 1f
            progressBar?.visibility = View.GONE
        }
        
        fun pauseVideo() {
            exoPlayer?.pause()
            Log.d("VideoAdapter", "⏸️ Video paused at position $absoluteAdapterPosition")
        }
    }
}
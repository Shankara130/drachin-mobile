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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoAdapter(
    private val dramas: List<DramaBook>,
    private val getVideoUrlUseCase: GetVideoUrlUseCase
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    private val urlCache = mutableMapOf<String, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_feed, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val drama = dramas[position]
        holder.bind(drama, getVideoUrlUseCase, urlCache)
        
        if (position + 1 < dramas.size) {
            val nextBookId = dramas[position + 1].bookId
            preloadUrl(nextBookId)
        }
    }

    private fun preloadUrl(bookId: String?) {
        if (bookId == null || urlCache.containsKey(bookId)) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = getVideoUrlUseCase.invoke(bookId)
                urlCache[bookId] = url
                Log.d("Preload", "URL ready for $bookId")
            } catch (e: Exception) {
                // Ignore error on preload
            }
        }
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

        private var exoPlayer: ExoPlayer? = null
        private var currentBookId: String? = null
        private var useCase: GetVideoUrlUseCase? = null
        
        private var urlCacheRef: MutableMap<String, String>? = null 
        private var isExpanded = false

        fun bind(
            drama: DramaBook, 
            useCase: GetVideoUrlUseCase, 
            cache: MutableMap<String, String>
        ) {
            this.currentBookId = drama.bookId
            this.useCase = useCase
            this.urlCacheRef = cache

            val maxChar = 26
            val rawTitle = drama.bookName
            val displayTitle = if (rawTitle.length > maxChar) "${rawTitle.take(maxChar)}... >" else "$rawTitle >"
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
            
            if (currentBookId != null && !cache.containsKey(currentBookId)) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val url = useCase.invoke(currentBookId!!)
                        cache[currentBookId!!] = url
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
            
            btnEpisodes.setOnClickListener {
                 if (currentBookId != null) {
                    val activity = itemView.context as? AppCompatActivity
                    val sheet = EpisodeSheet(currentBookId!!) { newUrl ->
                        startExoPlayer(itemView.context, newUrl)
                    }
                    if (activity != null) {
                        sheet.show(activity.supportFragmentManager, "EpSheet")
                    }
                }
            }
        }

        fun playVideo(context: Context) {
            val bookId = currentBookId ?: return
            val safeUseCase = useCase ?: return
            val cache = urlCacheRef

            CoroutineScope(Dispatchers.IO).launch {
                var url = cache?.get(bookId)
                
                if (url == null) {
                    try {
                        url = safeUseCase.invoke(bookId)
                        cache?.put(bookId, url)
                    } catch (e: Exception) {
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    if (bindingAdapterPosition != RecyclerView.NO_POSITION && url != null) {
                         startExoPlayer(context, url)
                    }
                }
            }
        }

        private fun startExoPlayer(context: Context, url: String) {
            if (exoPlayer != null) exoPlayer?.release()

            try {
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(1000, 2000, 250, 500)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()

                val renderersFactory = DefaultRenderersFactory(context)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)

                val unsafeClient = RetrofitClient.getUnsafeOkHttpClient()
                val dataSourceFactory = DefaultDataSource.Factory(context, androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(unsafeClient))

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
                    override fun onRenderedFirstFrame() { 
                         imgThumb.animate().alpha(0f).setDuration(200).withEndAction {
                             imgThumb.visibility = View.GONE
                         }.start()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(context, "Gagal: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                })
            } catch (e: Exception) { e.printStackTrace() }
        }

        fun stopVideo() {
            exoPlayer?.release()
            exoPlayer = null
            imgThumb.visibility = View.VISIBLE
            imgThumb.alpha = 1f
        }
    }
}
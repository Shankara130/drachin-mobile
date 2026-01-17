package com.example.zetayang.presentation.adapter

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
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
import com.example.zetayang.data.model.Episode

class EpisodeVideoAdapter(
    private val episodes: List<Episode>,
    private val coverUrl: String?
) : RecyclerView.Adapter<EpisodeVideoAdapter.EpisodeVideoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeVideoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_episode_video, parent, false)
        return EpisodeVideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeVideoViewHolder, position: Int) {
        val episode = episodes[position]
        holder.bind(episode, coverUrl)
    }

    override fun getItemCount(): Int = episodes.size

    override fun onViewAttachedToWindow(holder: EpisodeVideoViewHolder) {
        super.onViewAttachedToWindow(holder)
        holder.playVideo()
    }

    override fun onViewDetachedFromWindow(holder: EpisodeVideoViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.pauseVideo()
    }

    class EpisodeVideoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val playerView: PlayerView = itemView.findViewById(R.id.playerView)
        private val imgThumb: ImageView = itemView.findViewById(R.id.imgThumb)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)
        
        private var exoPlayer: ExoPlayer? = null
        private var videoUrl: String? = null

        fun bind(episode: Episode, coverUrl: String?) {
            videoUrl = episode.getBestVideoUrl()
            
            // Load thumbnail
            coverUrl?.let {
                imgThumb.load(it) {
                    crossfade(true)
                }
            }
            
            imgThumb.visibility = View.VISIBLE
            imgThumb.alpha = 1f
            progressBar.visibility = View.GONE
        }

        fun playVideo() {
            val url = videoUrl ?: return
            val context = itemView.context
            
            Log.d("EpisodeVideo", "🎬 Playing: $url")
            progressBar.visibility = View.VISIBLE
            
            exoPlayer?.release()
            
            try {
                val loadControl = DefaultLoadControl.Builder()
                    .setBufferDurationsMs(15000, 50000, 2500, 5000)
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
                            Player.STATE_BUFFERING -> progressBar.visibility = View.VISIBLE
                            Player.STATE_READY -> progressBar.visibility = View.GONE
                        }
                    }
                    
                    override fun onRenderedFirstFrame() {
                        progressBar.visibility = View.GONE
                        imgThumb.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction { imgThumb.visibility = View.GONE }
                            .start()
                    }
                    
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("EpisodeVideo", "❌ Error: ${error.message}")
                        progressBar.visibility = View.GONE
                        Toast.makeText(context, "Gagal memutar video", Toast.LENGTH_SHORT).show()
                    }
                })
            } catch (e: Exception) {
                Log.e("EpisodeVideo", "❌ Exception: ${e.message}")
                progressBar.visibility = View.GONE
                e.printStackTrace()
            }
        }

        fun pauseVideo() {
            exoPlayer?.pause()
        }

        fun releaseVideo() {
            exoPlayer?.release()
            exoPlayer = null
            imgThumb.visibility = View.VISIBLE
            imgThumb.alpha = 1f
            progressBar.visibility = View.GONE
        }
    }
}
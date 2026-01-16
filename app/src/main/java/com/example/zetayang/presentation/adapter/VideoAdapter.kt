package com.example.zetayang.presentation.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.example.zetayang.R
import com.example.zetayang.data.model.DramaBook
import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.domain.usecase.GetVideoUrlUseCase
import com.example.zetayang.presentation.sheet.EpisodeSheet

class VideoAdapter(
    private val dramas: List<DramaBook>,
    private val getVideoUrlUseCase: GetVideoUrlUseCase
) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_feed, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(dramas[position], getVideoUrlUseCase)
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
        private val infoContainer: LinearLayout = itemView.findViewById(R.id.infoContainer)
        private val btnEpisodes: Button = itemView.findViewById(R.id.btnEpisodes)
        
        private var exoPlayer: ExoPlayer? = null
        private var currentBookId: String? = null
        private var useCase: GetVideoUrlUseCase? = null

        fun bind(drama: DramaBook, useCase: GetVideoUrlUseCase) {
            this.currentBookId = drama.bookId
            this.useCase = useCase
            
            tvTitle.text = drama.bookName
            imgThumb.visibility = View.VISIBLE
            imgThumb.load(drama.coverUrl)
            playerView.player = null

             ViewCompat.setOnApplyWindowInsetsListener(infoContainer) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val bottomNavHeight = (80 * view.context.resources.displayMetrics.density).toInt()
                view.updatePadding(bottom = bars.bottom + bottomNavHeight) 
                insets
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

            CoroutineScope(Dispatchers.IO).launch {
                val url = safeUseCase.invoke(bookId)
                withContext(Dispatchers.Main) {
                    startExoPlayer(context, url)
                }
            }
        }

        private fun startExoPlayer(context: Context, url: String) {
            if (exoPlayer != null) return

            try {
                 val unsafeClient = RetrofitClient.getUnsafeOkHttpClient()
                val dataSourceFactory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(unsafeClient))

                exoPlayer = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                        prepare()
                        playWhenReady = true
                        repeatMode = Player.REPEAT_MODE_ONE
                    }

                playerView.player = exoPlayer
                
                exoPlayer?.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() { imgThumb.visibility = View.GONE }
                    override fun onPlayerError(error: PlaybackException) {
                        Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_SHORT).show()
                        imgThumb.visibility = View.VISIBLE
                    }
                })
            } catch (e: Exception) { e.printStackTrace() }
        }

        fun stopVideo() {
            exoPlayer?.release()
            exoPlayer = null
            imgThumb.visibility = View.VISIBLE
        }
    }
}
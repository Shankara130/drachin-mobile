package com.example.zetayang

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
import androidx.appcompat.app.AppCompatActivity
import coil.load
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class VideoAdapter(private val dramas: List<DramaBook>) : RecyclerView.Adapter<VideoAdapter.VideoViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_video_feed, parent, false)
        return VideoViewHolder(view)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(dramas[position])
    }

    override fun getItemCount(): Int = dramas.size

    // Autoplay Video Pertama saat layar muncul
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
        
        private var exoPlayer: ExoPlayer? = null
        private var currentBookId: String? = null

        fun bind(drama: DramaBook) {
            tvTitle.text = drama.bookName
            currentBookId = drama.bookId
            
            imgThumb.visibility = View.VISIBLE
            imgThumb.load(drama.coverUrl)
            playerView.player = null

            // Safe Area untuk judul (agar tidak tertutup navigasi HP)
            ViewCompat.setOnApplyWindowInsetsListener(infoContainer) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                val bottomNavHeight = (80 * view.context.resources.displayMetrics.density).toInt()
                view.updatePadding(bottom = bars.bottom + bottomNavHeight) 
                insets
            }

            // --- LOGIC TOMBOL EPISODE ---
            val btnEpisodes: Button = itemView.findViewById(R.id.btnEpisodes)
            
            btnEpisodes.setOnClickListener {
                if (currentBookId != null) {
                    // Tampilkan Bottom Sheet
                    val activity = itemView.context as? AppCompatActivity
                    
                    val sheet = EpisodeSheet(currentBookId!!) { newVideoUrl ->
                        // Callback: Video baru dipilih dari Sheet
                        println("Ganti Video ke: $newVideoUrl")
                        startExoPlayer(itemView.context, newVideoUrl)
                    }
                    
                    activity?.supportFragmentManager?.let { fragmentManager ->
                        sheet.show(fragmentManager, "EpisodeSheet")
                    }
                }
            }
        }

        fun playVideo(context: Context) {
            if (currentBookId == null) return

            // Panggil API dengan tipe data yang sudah rapi (List<Episode>)
            RetrofitClient.instance.getEpisodes(currentBookId!!).enqueue(object : Callback<List<Episode>> {
                override fun onResponse(call: Call<List<Episode>>, response: Response<List<Episode>>) {
                    val episodes = response.body()
                    
                    if (!episodes.isNullOrEmpty()) {
                        // Ambil Episode 1 (index 0)
                        val firstEp = episodes[0]
                        val videoUrl = firstEp.getBestVideoUrl() // Pakai fungsi pintar di Episode.kt
                        
                        if (videoUrl.isNotEmpty()) {
                            startExoPlayer(context, videoUrl)
                        } else {
                            // Link Cadangan
                            startExoPlayer(context, "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                        }
                    }
                }

                override fun onFailure(call: Call<List<Episode>>, t: Throwable) {
                    // Silent fail agar user tidak terganggu, atau log error
                    println("Gagal load episode: ${t.message}")
                }
            })
        }

        private fun startExoPlayer(context: Context, url: String) {
            if (exoPlayer != null) return

            try {
                // Koneksi "Sakti" (Bypass SSL)
                val unsafeClient = RetrofitClient.getUnsafeOkHttpClient()
                val dataSourceFactory = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(unsafeClient))

                exoPlayer = ExoPlayer.Builder(context)
                    .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                    .build()
                    .apply {
                        setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                        prepare()
                        playWhenReady = true
                        repeatMode = Player.REPEAT_MODE_ONE // Looping video
                    }

                playerView.player = exoPlayer
                
                exoPlayer?.addListener(object : Player.Listener {
                    override fun onRenderedFirstFrame() {
                        // Sembunyikan cover saat video benar-benar jalan
                        imgThumb.visibility = View.GONE
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        super.onPlayerError(error)
                        // Tampilkan pesan jika error (misal link expired)
                        Toast.makeText(context, "Gagal memutar: ${error.message}", Toast.LENGTH_SHORT).show()
                        imgThumb.visibility = View.VISIBLE
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopVideo() {
            exoPlayer?.release()
            exoPlayer = null
            imgThumb.visibility = View.VISIBLE
        }
    }
}
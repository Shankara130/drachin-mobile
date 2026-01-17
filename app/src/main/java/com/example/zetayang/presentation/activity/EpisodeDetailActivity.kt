package com.example.zetayang.presentation.activity

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.zetayang.R
import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.data.model.Episode
import com.example.zetayang.presentation.adapter.EpisodeAdapter
import com.example.zetayang.presentation.adapter.EpisodeVideoAdapter
import kotlinx.coroutines.launch

class EpisodeDetailActivity : AppCompatActivity() {
    
    private lateinit var episodeViewPager: ViewPager2
    private lateinit var btnBack: ImageView
    private lateinit var tvEpisodeTitle: TextView
    private lateinit var recyclerEpisodes: RecyclerView
    private lateinit var tvTotalEpisodes: TextView
    private lateinit var episodeHeader: LinearLayout
    private lateinit var ivExpandIcon: ImageView
    private lateinit var divider: View
    
    private var bookId: String? = null
    private var coverUrl: String? = null
    private var currentEpisodeIndex = 0
    private lateinit var episodeListAdapter: EpisodeAdapter
    private lateinit var videoAdapter: EpisodeVideoAdapter
    private var episodes: List<Episode> = emptyList()
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
        coverUrl = intent.getStringExtra(EXTRA_COVER_URL)
        
        initViews()
        setupUI(dramaTitle)
        loadEpisodes()
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
        episodeViewPager = findViewById(R.id.episodeViewPager)
        btnBack = findViewById(R.id.btnBack)
        tvEpisodeTitle = findViewById(R.id.tvEpisodeTitle)
        recyclerEpisodes = findViewById(R.id.recyclerEpisodes)
        tvTotalEpisodes = findViewById(R.id.tvTotalEpisodes)
        episodeHeader = findViewById(R.id.episodeHeader)
        ivExpandIcon = findViewById(R.id.ivExpandIcon)
        divider = findViewById(R.id.divider)
        
        // Setup vertical orientation
        episodeViewPager.orientation = ViewPager2.ORIENTATION_VERTICAL
    }
    
    private fun setupUI(title: String) {
        tvEpisodeTitle.text = "Ep.1"
        
        // Back button
        btnBack.setOnClickListener {
            finish()
        }
        
        // Setup RecyclerView for episode list
        recyclerEpisodes.layoutManager = GridLayoutManager(this, 5)
        
        // Setup expand/collapse behavior
        episodeHeader.setOnClickListener {
            toggleEpisodeList()
        }
        
        // ViewPager page change listener
        episodeViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentEpisodeIndex = position
                tvEpisodeTitle.text = "Ep.${position + 1}"
                
                // Update selected episode in list
                if (::episodeListAdapter.isInitialized) {
                    episodeListAdapter.setSelectedPosition(position)
                }
                
                Log.d("EpisodeDetail", "📺 Switched to episode ${position + 1}")
            }
        })
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
        
        lifecycleScope.launch {
            try {
                episodes = RetrofitClient.instance.getEpisodes(safeBookId)
                
                if (episodes.isNotEmpty()) {
                    tvTotalEpisodes.text = "≡  Total ${episodes.size} episode >"
                    
                    // Setup ViewPager adapter
                    videoAdapter = EpisodeVideoAdapter(episodes, coverUrl)
                    episodeViewPager.adapter = videoAdapter
                    
                    // Setup episode list adapter
                    episodeListAdapter = EpisodeAdapter(episodes) { _, position ->
                        // Jump to selected episode
                        episodeViewPager.setCurrentItem(position, true)
                        
                        // Auto collapse
                        if (isExpanded) {
                            toggleEpisodeList()
                        }
                    }
                    
                    recyclerEpisodes.adapter = episodeListAdapter
                    episodeListAdapter.setSelectedPosition(0)
                    
                    Log.d("EpisodeDetail", "✅ Loaded ${episodes.size} episodes")
                } else {
                    Toast.makeText(this@EpisodeDetailActivity, "Episode kosong", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@EpisodeDetailActivity, "Gagal load: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EpisodeDetail", "Error: ${e.message}")
            }
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
        // Video akan auto-pause oleh adapter saat view detached
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cleanup akan di-handle oleh adapter
    }
}
package com.example.zetayang

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView

import com.example.zetayang.presentation.adapter.VideoAdapter
import com.example.zetayang.presentation.fragment.DiscoverFragment
import com.example.zetayang.presentation.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fragmentContainer: FrameLayout
    
    private val viewModel: MainViewModel by viewModels() 
    private var adapter: VideoAdapter? = null
    private var currentPosition = 0
    
    // Flag untuk mencegah multiple observer calls
    private var isFirstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system navigation bar
        hideSystemUI()
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)
        fragmentContainer = findViewById(R.id.fragmentContainer)
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        // Observe home feed data
        viewModel.homeFeed.observe(this) { dramaList ->
            if (dramaList.isNullOrEmpty()) {
                Log.w("MainActivity", "⚠️ Empty drama list received")
                return@observe
            }
            
            Log.d("MainActivity", "📱 Received ${dramaList.size} dramas | isFirstLoad: $isFirstLoad | currentPos: $currentPosition")
            
            // Debug: Log first 3 drama titles
            dramaList.take(3).forEachIndexed { index, drama ->
                Log.d("MainActivity", "  [$index] ${drama.bookName} (${drama.bookId})")
            }
            
            // CRITICAL: Remove duplicates at UI level (extra safety)
            val deduplicatedList = dramaList
                .distinctBy { it.bookId }
                .toList()
            
            if (deduplicatedList.size != dramaList.size) {
                val duplicateCount = dramaList.size - deduplicatedList.size
                Log.e("MainActivity", "❌ Removed $duplicateCount duplicates at UI level")
                
                // Find duplicates
                val duplicates = dramaList
                    .groupBy { it.bookId }
                    .filter { it.value.size > 1 }
                    .mapValues { it.value.size }
                Log.e("MainActivity", "Duplicate bookIds: $duplicates")
            } else {
                Log.d("MainActivity", "✅ No duplicates in UI data")
            }
            
            if (isFirstLoad) {
                // FIRST LOAD - Create adapter with deduplicated list
                adapter = VideoAdapter(deduplicatedList, viewModel.getVideoUrlUseCase)
                viewPager.adapter = adapter
                viewPager.doOnLayout { playVideoAt(0) }
                isFirstLoad = false
                Log.d("MainActivity", "✅ First load complete - ${deduplicatedList.size} unique dramas")
            } else {
                // LOAD MORE - Update adapter
                val savedPosition = currentPosition
                
                // Update with deduplicated list
                adapter?.updateDramas(deduplicatedList)
                
                // Restore position
                viewPager.post {
                    if (savedPosition < deduplicatedList.size) {
                        viewPager.setCurrentItem(savedPosition, false)
                        Log.d("MainActivity", "✅ Position restored to $savedPosition/${deduplicatedList.size}")
                    }
                }
            }
        }

        // Observe errors
        viewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "❌ Error: $errorMsg")
        }

        // Observe loading state
        viewModel.isLoading.observe(this) { isLoading ->
            if (isLoading) {
                Log.d("MainActivity", "⏳ Loading...")
            } else {
                Log.d("MainActivity", "✅ Loading complete")
            }
        }

        // Load initial data
        Log.d("MainActivity", "🚀 Starting app - forcing fresh load...")
        
        // CRITICAL: Reset ViewModel state first
        viewModel.resetData()
        
        // Small delay to ensure reset completes
        viewPager.postDelayed({
            Log.d("MainActivity", "🚀 Loading fresh data from API...")
            viewModel.loadHomeFeed()
            
            // Debug: Check ViewModel state after load
            viewPager.postDelayed({
                val count = viewModel.getLoadedDramasCount()
                Log.d("MainActivity", "📊 ViewModel has $count dramas loaded")
            }, 3000)
        }, 100)

        // ViewPager scroll listener for infinite scroll
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                playVideoAt(position)
                
                // Load more when near end (80% threshold)
                val totalItems = adapter?.itemCount ?: 0
                val threshold = (totalItems * 0.8).toInt()
                
                Log.d("MainActivity", "📍 Position: $position/$totalItems (threshold: $threshold)")
                
                if (position >= threshold && !viewModel.isCurrentlyLoading()) {
                    loadMoreVideos()
                }
            }
        })
        
        // Bottom Navigation Click Listener
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    showHomeFeed()
                    true
                }
                R.id.nav_discover -> {
                    showDiscoverFragment()
                    true
                }
                R.id.nav_profile -> {
                    pauseCurrentVideo()
                    Toast.makeText(this, "Profile (Coming Soon)", Toast.LENGTH_SHORT).show()
                    false
                }
                else -> false
            }
        }
    }
    
    private fun showHomeFeed() {
        viewPager.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
        
        // Clear all fragments
        supportFragmentManager.fragments.forEach {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
        
        // Resume video when switching back to home
        playVideoAt(currentPosition)
    }
    
    private fun showDiscoverFragment() {
        // IMPORTANT: Pause ALL videos before hiding viewPager
        pauseAllVideos()
        
        viewPager.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE
        
        val fragment = DiscoverFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun playVideoAt(position: Int) {
        val recyclerView = viewPager.getChildAt(0) as RecyclerView
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)
        
        if (viewHolder is VideoAdapter.VideoViewHolder) {
            viewHolder.playVideo(this)
        } else {
            viewPager.postDelayed({ playVideoAt(position) }, 100)
        }
    }
    
    private fun pauseCurrentVideo() {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(currentPosition)
        
        if (viewHolder is VideoAdapter.VideoViewHolder) {
            viewHolder.pauseVideo()
            Log.d("MainActivity", "⏸️ Paused video at position $currentPosition")
        }
    }
    
    private fun pauseAllVideos() {
        val recyclerView = viewPager.getChildAt(0) as? RecyclerView ?: return
        
        for (i in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(i))
            if (holder is VideoAdapter.VideoViewHolder) {
                holder.pauseVideo()
            }
        }
        Log.d("MainActivity", "⏸️⏸️ Paused ALL videos")
    }
    
    private fun loadMoreVideos() {
        Log.d("MainActivity", "🔄 Load more triggered at position $currentPosition")
        Toast.makeText(this, "Mengacak video...", Toast.LENGTH_SHORT).show()
        
        // Shuffle existing data (NO API call)
        viewModel.loadMoreHomeFeed()
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
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }
    
    override fun onPause() {
        super.onPause()
        pauseCurrentVideo()
        Log.d("MainActivity", "⏸️ Activity paused - video stopped")
    }
    
    override fun onResume() {
        super.onResume()
        if (viewPager.visibility == View.VISIBLE) {
            viewPager.postDelayed({
                playVideoAt(currentPosition)
                Log.d("MainActivity", "▶️ Activity resumed - video playing at position $currentPosition")
            }, 100)
        }
    }
    
    override fun onStop() {
        super.onStop()
        pauseAllVideos()
        Log.d("MainActivity", "🛑 Activity stopped - all videos stopped")
    }
}
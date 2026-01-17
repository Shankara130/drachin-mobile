package com.example.zetayang

import android.os.Bundle
import android.view.View
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
import com.example.zetayang.presentation.viewmodel.MainViewModel

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    
    private val viewModel: MainViewModel by viewModels() 
    private var adapter: VideoAdapter? = null
    private var currentPosition = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Hide system navigation bar
        hideSystemUI()
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL

        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = bars.bottom, left = bars.left, right = bars.right)
            insets
        }

        viewModel.homeFeed.observe(this) { dramaList ->
            if (!dramaList.isNullOrEmpty()) {
                adapter = VideoAdapter(dramaList, viewModel.getVideoUrlUseCase)
                viewPager.adapter = adapter
                viewPager.doOnLayout { playVideoAt(0) }
            }
        }

        viewModel.error.observe(this) { errorMsg ->
            Toast.makeText(this, "Error: $errorMsg", Toast.LENGTH_LONG).show()
        }

        viewModel.loadHomeFeed()

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                playVideoAt(position)
            }
        })
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
        }
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
        // Pause video when activity is paused (e.g., when navigating to another activity)
        pauseCurrentVideo()
    }
    
    override fun onResume() {
        super.onResume()
        // Resume video when coming back
        playVideoAt(currentPosition)
    }
}
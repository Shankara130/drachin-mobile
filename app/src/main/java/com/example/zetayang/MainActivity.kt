package com.example.zetayang

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout // Penting untuk autoplay
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var bottomNav: BottomNavigationView
    private var adapter: VideoAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        viewPager = findViewById(R.id.viewPager)
        bottomNav = findViewById(R.id.bottomNav)

        // --- 1. SET ORIENTASI VERTIKAL (TIKTOK STYLE) ---
        viewPager.orientation = ViewPager2.ORIENTATION_VERTICAL

        // --- 2. SETUP SAFE AREA BOTTOM NAV ---
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                bottom = bars.bottom,
                left = bars.left,
                right = bars.right
            )
            insets
        }

        // --- 3. LOGIKA NAVIGASI ---
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    Toast.makeText(this, "Home", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_discover -> {
                    Toast.makeText(this, "Discover", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_profile -> {
                    Toast.makeText(this, "Profile", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }

        // --- 4. LOAD DATA & AUTOPLAY ---
        RetrofitClient.instance.getHomeFeed().enqueue(object : Callback<List<DramaBook>> {
            override fun onResponse(call: Call<List<DramaBook>>, response: Response<List<DramaBook>>) {
                val dramaList = response.body() ?: emptyList()
                if (dramaList.isNotEmpty()) {
                    adapter = VideoAdapter(dramaList)
                    viewPager.adapter = adapter
                    
                    // Gunakan doOnLayout agar playVideo dipanggil HANYA saat layar sudah siap
                    // viewPager.doOnLayout {
                    //     playVideoAt(0)
                    // }
                }
            }

            override fun onFailure(call: Call<List<DramaBook>>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })

        // Listener saat scroll ganti video
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                playVideoAt(position)
            }
        })
    }

    private fun playVideoAt(position: Int) {
        // Trik mengambil akses ke RecyclerView di dalam ViewPager2
        val recyclerView = viewPager.getChildAt(0) as RecyclerView
        
        // Coba cari ViewHolder (Layar Video) pada posisi tersebut
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(position)

        if (viewHolder is VideoAdapter.VideoViewHolder) {
            // BERHASIL: Layar ketemu, jalankan video!
            viewHolder.playVideo(this)
        } else {
            // GAGAL: Layar belum siap (masih loading/rendering).
            // SOLUSI: Coba panggil fungsi ini lagi setelah 100 milidetik (Retry Loop)
            viewPager.postDelayed({ 
                playVideoAt(position) 
            }, 100)
        }
    }
}
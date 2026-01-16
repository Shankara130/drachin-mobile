package com.example.zetayang

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class EpisodeSheet(
    private val bookId: String,
    private val onVideoSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Kita buat layout secara programmatik agar tidak perlu file XML baru lagi (biar cepat)
        val context = requireContext()
        
        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        }

        // Judul "Pilih Episode"
        val title = android.widget.TextView(context).apply {
            text = "Pilih Episode"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(title)

        // Loading Bar
        val progressBar = ProgressBar(context)
        rootLayout.addView(progressBar)

        // RecyclerView (Grid)
        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 5) // 5 Kolom per baris
        }
        rootLayout.addView(recyclerView)

        // Load Data API
        loadEpisodes(progressBar, recyclerView)

        return rootLayout
    }

    private fun loadEpisodes(progressBar: ProgressBar, recyclerView: RecyclerView) {
        RetrofitClient.instance.getEpisodes(bookId).enqueue(object : Callback<List<Episode>> {
            override fun onResponse(call: Call<List<Episode>>, response: Response<List<Episode>>) {
                progressBar.visibility = View.GONE
                val episodes = response.body()
                
                if (!episodes.isNullOrEmpty()) {
                    recyclerView.adapter = EpisodeAdapter(episodes) { url ->
                        // Saat item diklik: Tutup Sheet, Mainkan Video
                        onVideoSelected(url)
                        dismiss()
                    }
                } else {
                    Toast.makeText(context, "Episode kosong", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<List<Episode>>, t: Throwable) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Gagal load episode", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
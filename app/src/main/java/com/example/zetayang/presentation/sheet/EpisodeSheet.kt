package com.example.zetayang.presentation.sheet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.presentation.adapter.EpisodeAdapter

class EpisodeSheet(
    private val bookId: String,
    private val onVideoSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val context = requireContext()
        val rootLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(30, 30, 30, 30)
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        }

        val title = android.widget.TextView(context).apply {
            text = "Pilih Episode"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        rootLayout.addView(title)

        val progressBar = ProgressBar(context)
        rootLayout.addView(progressBar)

        val recyclerView = RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 5)
        }
        rootLayout.addView(recyclerView)

        loadEpisodes(progressBar, recyclerView)
        return rootLayout
    }

    private fun loadEpisodes(progressBar: ProgressBar, recyclerView: RecyclerView) {
        lifecycleScope.launch {
            try {
                val episodes = RetrofitClient.instance.getEpisodes(bookId)
                progressBar.visibility = View.GONE
                
                if (episodes.isNotEmpty()) {
                    recyclerView.adapter = EpisodeAdapter(episodes) { url ->
                        onVideoSelected(url)
                        dismiss()
                    }
                } else {
                    Toast.makeText(context, "Episode kosong", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Gagal load: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
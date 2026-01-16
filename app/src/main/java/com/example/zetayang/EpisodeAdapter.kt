package com.example.zetayang

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EpisodeAdapter(
    private val episodes: List<Episode>,
    private val onEpisodeClick: (String) -> Unit // Callback mengirim URL ke VideoPlayer
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_number, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episode = episodes[position]
        // Tampilkan nomor urut (Index + 1) atau nama episode
        holder.tvNumber.text = "${position + 1}"

        holder.itemView.setOnClickListener {
            // Gunakan fungsi pintar getBestVideoUrl() yang sudah kita buat
            val url = episode.getBestVideoUrl()
            if (url.isNotEmpty()) {
                onEpisodeClick(url)
            }
        }
    }

    override fun getItemCount(): Int = episodes.size

    class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvNumber: TextView = itemView.findViewById(R.id.tvEpisodeNumber)
    }
}
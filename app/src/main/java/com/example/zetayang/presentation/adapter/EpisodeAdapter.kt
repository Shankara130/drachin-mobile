package com.example.zetayang.presentation.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

import com.example.zetayang.R
import com.example.zetayang.data.model.Episode


class EpisodeAdapter(
    private val episodes: List<Episode>,
    private val onEpisodeClick: (String, Int) -> Unit // Callback dengan URL dan position
) : RecyclerView.Adapter<EpisodeAdapter.EpisodeViewHolder>() {

    private var selectedPosition = 0 // Track episode yang sedang diputar

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_number, parent, false)
        return EpisodeViewHolder(view)
    }

    override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
        val episode = episodes[position]
        val episodeNumber = position + 1
        
        // Tampilkan nomor episode
        holder.tvNumber.text = episodeNumber.toString()
        
        // Highlight episode yang sedang diputar
        if (position == selectedPosition) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#FF6B35")) // Orange
            holder.tvNumber.setTextColor(Color.WHITE)
        } else {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#333333")) // Dark gray
            holder.tvNumber.setTextColor(Color.parseColor("#CCCCCC")) // Light gray
        }

        holder.itemView.setOnClickListener {
            // Update selected position
            val oldPosition = selectedPosition
            selectedPosition = position
            
            // Refresh tampilan
            notifyItemChanged(oldPosition)
            notifyItemChanged(selectedPosition)
            
            // Callback dengan URL
            val url = episode.getBestVideoUrl()
            if (url.isNotEmpty()) {
                onEpisodeClick(url, position)
            }
        }
    }

    override fun getItemCount(): Int = episodes.size
    
    fun setSelectedPosition(position: Int) {
        val oldPosition = selectedPosition
        selectedPosition = position
        notifyItemChanged(oldPosition)
        notifyItemChanged(selectedPosition)
    }

    class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: CardView = itemView.findViewById(R.id.cardEpisode)
        val tvNumber: TextView = itemView.findViewById(R.id.tvEpisodeNumber)
    }
}
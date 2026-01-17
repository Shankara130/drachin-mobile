package com.example.zetayang.presentation.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.zetayang.R
import com.example.zetayang.data.model.DramaBook
import com.example.zetayang.presentation.activity.EpisodeDetailActivity

class DiscoverAdapter(
    private val dramas: List<DramaBook>
) : RecyclerView.Adapter<DiscoverAdapter.DiscoverViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiscoverViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_discover_drama, parent, false)
        return DiscoverViewHolder(view)
    }

    override fun onBindViewHolder(holder: DiscoverViewHolder, position: Int) {
        holder.bind(dramas[position])
    }

    override fun getItemCount(): Int = dramas.size

    class DiscoverViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView.findViewById(R.id.cardDrama)
        private val imgCover: ImageView = itemView.findViewById(R.id.imgCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)
        private val tvViews: TextView = itemView.findViewById(R.id.tvViews)
        private val badgeNew: TextView? = itemView.findViewById(R.id.badgeNew)

        fun bind(drama: DramaBook) {
            // Load cover image with fallback
            val coverUrl = drama.coverUrl
            if (!coverUrl.isNullOrEmpty()) {
                imgCover.load(coverUrl) {
                    crossfade(true)
                    placeholder(R.drawable.placeholder_drama)
                    error(R.drawable.placeholder_drama)
                }
            } else {
                imgCover.setImageResource(R.drawable.placeholder_drama)
            }

            // Set title with fallback
            tvTitle.text = drama.bookName ?: "Drama #${drama.bookId}"

            // Set subtitle (genre/category) with null safety
            val genres = drama.tags?.take(2)?.joinToString(" | ") ?: "Drama"
            val chapterCount = drama.chapterCount ?: 0
            tvSubtitle.text = "$genres | $chapterCount epis..."

            // Set views with null safety
            tvViews.text = formatViews(drama.playCount ?: "0")

            // Show "Baru" badge for dramas with high rank
            badgeNew?.visibility = if (drama.rank?.rankType == 1) View.VISIBLE else View.GONE

            // Click listener
            cardView.setOnClickListener {
                // Only navigate if bookName is not null
                if (drama.bookName != null) {
                    val intent = Intent(itemView.context, EpisodeDetailActivity::class.java).apply {
                        putExtra(EpisodeDetailActivity.EXTRA_BOOK_ID, drama.bookId)
                        putExtra(EpisodeDetailActivity.EXTRA_DRAMA_TITLE, drama.bookName)
                        putExtra(EpisodeDetailActivity.EXTRA_COVER_URL, drama.coverUrl)
                    }
                    itemView.context.startActivity(intent)
                }
            }
        }

        private fun formatViews(views: String): String {
            return try {
                val count = views.toLongOrNull() ?: 0
                when {
                    count >= 1_000_000 -> "👁 ${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
                    count >= 1_000 -> "👁 ${count / 1_000}.${(count % 1_000) / 100}K"
                    else -> "👁 $count"
                }
            } catch (e: Exception) {
                "👁 $views"
            }
        }
    }
}
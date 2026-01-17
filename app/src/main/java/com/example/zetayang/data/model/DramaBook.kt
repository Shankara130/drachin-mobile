package com.example.zetayang.data.model

import com.google.gson.annotations.SerializedName

data class DramaBook(
    @SerializedName("bookId")
    val bookId: String,

    @SerializedName("bookName")
    val bookName: String? = null, // Make nullable

    @SerializedName("coverWap")
    val coverUrl: String? = null, // Make nullable

    @SerializedName("chapterCount")
    val chapterCount: Int? = null, // Make nullable

    @SerializedName("introduction")
    val introduction: String? = null, // Already nullable

    @SerializedName("playCount")
    val playCount: String? = "0", // Make nullable with default

    @SerializedName("tags")
    val tags: List<String>? = null,
    
    @SerializedName("rankVo")
    val rank: RankVo? = null
) {
    /**
     * Get safe display name
     */
    fun getDisplayName(): String {
        return bookName ?: "Drama #$bookId"
    }
    
    /**
     * Get safe cover URL
     */
    fun getSafeCoverUrl(): String {
        return coverUrl ?: ""
    }
    
    /**
     * Get safe play count
     */
    fun getSafePlayCount(): String {
        return playCount ?: "0"
    }
}

// Helper class for the 'rankVo' field
data class RankVo(
    @SerializedName("rankType")
    val rankType: Int? = null,
    @SerializedName("recCopy")
    val recCopy: String? = null
)
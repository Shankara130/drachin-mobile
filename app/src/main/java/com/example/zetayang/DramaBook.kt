package com.example.zetayang

import com.google.gson.annotations.SerializedName

data class DramaBook(
    @SerializedName("bookId")
    val bookId: String,

    @SerializedName("bookName")
    val bookName: String,

    @SerializedName("coverWap")
    val coverUrl: String, // mapped 'coverWap' to 'coverUrl' for cleaner code

    @SerializedName("chapterCount")
    val chapterCount: Int,

    @SerializedName("introduction")
    val introduction: String,

    @SerializedName("playCount")
    val playCount: String,

    @SerializedName("tags")
    val tags: List<String>? = null,
    
    @SerializedName("rankVo")
    val rank: RankVo? = null
)

// Helper class for the 'rankVo' field seen in the logs
data class RankVo(
    @SerializedName("rankType")
    val rankType: Int,
    @SerializedName("recCopy")
    val recCopy: String
)
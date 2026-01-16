package com.example.zetayang
import com.google.gson.annotations.SerializedName

data class Episode(
    @SerializedName("chapterId") val chapterId: String? = null,
    @SerializedName("chapterName") val name: String? = null,
    
    // Ini struktur berlapis yang kita temukan tadi
    @SerializedName("cdnList") val cdnList: List<CdnData>? = null
) {
    // Fungsi Pintar untuk langsung ambil link video terbaik
    fun getBestVideoUrl(): String {
        if (cdnList.isNullOrEmpty()) return ""

        // Ambil penyedia CDN pertama
        val firstCdn = cdnList[0]
        val videos = firstCdn.videoPathList

        if (videos.isNullOrEmpty()) return ""

        // 1. Prioritas: Cari kualitas 720p (HD & Ringan)
        val hdVideo = videos.find { it.quality == 720 }?.videoPath
        if (!hdVideo.isNullOrEmpty()) return hdVideo

        // 2. Alternatif: Cari kualitas 540p (Standard)
        val sdVideo = videos.find { it.quality == 540 }?.videoPath
        if (!sdVideo.isNullOrEmpty()) return sdVideo

        // 3. Terakhir: Ambil video apapun yang ada isinya
        return videos.firstOrNull { !it.videoPath.isNullOrEmpty() }?.videoPath ?: ""
    }
}

// Data Pendukung (Anak-anak dari Episode)
data class CdnData(
    @SerializedName("videoPathList") val videoPathList: List<VideoPath>? = null
)

data class VideoPath(
    @SerializedName("quality") val quality: Int,
    @SerializedName("videoPath") val videoPath: String?
)
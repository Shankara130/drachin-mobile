package com.example.zetayang

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface DramaApiService {
    @GET("dramabox/foryou")
    fun getHomeFeed(): Call<List<DramaBook>>
    
    // Kita kembalikan ke List<Episode> karena Class Episode sudah diperbaiki
    @GET("dramabox/allepisode")
    fun getEpisodes(@Query("bookId") bookId: String): Call<List<Episode>>
}
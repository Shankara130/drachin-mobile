package com.example.zetayang.data.api

import retrofit2.http.GET
import retrofit2.http.Query
import com.google.gson.JsonElement

import com.example.zetayang.data.model.DramaBook
import com.example.zetayang.data.model.Episode

interface DramaApiService {
    @GET("dramabox/foryou")
    suspend fun getHomeFeed(): List<DramaBook>

    @GET("dramabox/allepisode")
    suspend fun getEpisodes(@Query("bookId") bookId: String): List<Episode>
}
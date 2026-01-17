package com.example.zetayang.data.api

import retrofit2.http.GET
import retrofit2.http.Query

import com.example.zetayang.data.model.DramaBook
import com.example.zetayang.data.model.Episode

interface DramaApiService {
    
    /**
     * Get For You Feed (Home Feed)
     * Endpoint: /api/dramabox/foryou
     */
    @GET("dramabox/foryou")
    suspend fun getHomeFeed(): List<DramaBook>

    /**
     * Get Latest Dramas
     * Endpoint: /api/dramabox/latest
     */
    @GET("dramabox/latest")
    suspend fun getLatestDramas(): List<DramaBook>

    /**
     * Get Trending Dramas
     * Endpoint: /api/dramabox/trending
     */
    @GET("dramabox/trending")
    suspend fun getTrendingDramas(): List<DramaBook>

    /**
     * Search Dramas by Query
     * Endpoint: /api/dramabox/search?query=romance
     */
    @GET("dramabox/search")
    suspend fun searchDramas(@Query("query") query: String): List<DramaBook>

    /**
     * Get All Episodes for a Drama
     * Endpoint: /api/dramabox/allepisode?bookId=41000116666
     */
    @GET("dramabox/allepisode")
    suspend fun getEpisodes(@Query("bookId") bookId: String): List<Episode>

    /**
     * Get Stream URL for Specific Episode
     * Endpoint: /api/dramabox/stream?bookId=41000116666&episode=1
     * WARNING: This endpoint has RATE LIMITING
     */
    @GET("dramabox/stream")
    suspend fun getStreamUrl(
        @Query("bookId") bookId: String,
        @Query("episode") episode: Int
    ): Episode
}
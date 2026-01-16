package com.example.zetayang.data.repository

import android.util.Log

import com.example.zetayang.domain.repository.DramaRepository
import com.example.zetayang.data.api.DramaApiService
import com.example.zetayang.data.model.DramaBook

class DramaRepositoryImpl(private val api: DramaApiService) : DramaRepository {

    override suspend fun getHomeFeed(): Result<List<DramaBook>> {
        return try {
            val data = api.getHomeFeed()
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getEpisodeUrl(bookId: String): String {
        return try {
            val episodes = api.getEpisodes(bookId)
            
            if (episodes.isNotEmpty()) {
                return episodes[0].getBestVideoUrl()
            }
            ""
        } catch (e: Exception) {
            Log.e("Repository", "Error fetching url: ${e.message}")
            ""
        }
    }
}
package com.example.zetayang.domain.repository

import com.example.zetayang.data.model.DramaBook

interface DramaRepository {
    suspend fun getHomeFeed(): Result<List<DramaBook>>
    suspend fun getEpisodeUrl(bookId: String): String
}
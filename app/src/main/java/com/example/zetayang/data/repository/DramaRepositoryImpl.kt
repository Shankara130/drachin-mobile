package com.example.zetayang.data.repository

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.example.zetayang.domain.repository.DramaRepository
import com.example.zetayang.data.api.DramaApiService
import com.example.zetayang.data.model.DramaBook

class DramaRepositoryImpl(private val api: DramaApiService) : DramaRepository {

    // Mutex untuk mencegah multiple concurrent requests
    private val apiMutex = Mutex()
    private var lastRequestTime = 0L
    private val MIN_REQUEST_INTERVAL = 3000L // 3 detik antar request
    
    // Cache untuk ALL dramas - loaded once
    private var allDramasCache: List<DramaBook>? = null
    private var cacheLoadTime = 0L
    private val CACHE_LIFETIME = 300_000L // 5 menit

    /**
     * Get Home Feed - LOAD ALL DRAMAS FROM ALL ENDPOINTS
     * Load ONCE, cache forever in this session
     */
    override suspend fun getHomeFeed(): Result<List<DramaBook>> {
        return apiMutex.withLock {
            try {
                // Check cache first
                if (allDramasCache != null) {
                    val cacheAge = System.currentTimeMillis() - cacheLoadTime
                    if (cacheAge < CACHE_LIFETIME) {
                        Log.d("Repository", "✅ Using cached data (${allDramasCache!!.size} dramas)")
                        return Result.success(allDramasCache!!)
                    }
                }
                
                // Enforce minimum interval between requests
                val timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime
                if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                    val waitTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest
                    Log.d("Repository", "⏱️ Rate limit: waiting ${waitTime}ms...")
                    delay(waitTime)
                }
                
                Log.d("Repository", "📥 Fetching ALL dramas from multiple endpoints...")
                
                // Use LinkedHashMap to ensure uniqueness by bookId
                val allDramas = LinkedHashMap<String, DramaBook>()
                
                // 1. Get ForYou dramas
                try {
                    Log.d("Repository", "📥 Fetching ForYou...")
                    val forYou = api.getHomeFeed()
                    forYou.forEach { drama ->
                        allDramas[drama.bookId] = drama
                    }
                    Log.d("Repository", "✅ ForYou: ${forYou.size} dramas | Total unique: ${allDramas.size}")
                    delay(MIN_REQUEST_INTERVAL)
                } catch (e: Exception) {
                    Log.e("Repository", "❌ ForYou failed: ${e.message}")
                }
                
                // 2. Get Trending dramas
                try {
                    Log.d("Repository", "📥 Fetching Trending...")
                    val trending = api.getTrendingDramas()
                    trending.forEach { drama ->
                        allDramas[drama.bookId] = drama
                    }
                    Log.d("Repository", "✅ Trending: ${trending.size} dramas | Total unique: ${allDramas.size}")
                    delay(MIN_REQUEST_INTERVAL)
                } catch (e: Exception) {
                    Log.e("Repository", "❌ Trending failed: ${e.message}")
                }
                
                // 3. Get Latest dramas
                try {
                    Log.d("Repository", "📥 Fetching Latest...")
                    val latest = api.getLatestDramas()
                    latest.forEach { drama ->
                        allDramas[drama.bookId] = drama
                    }
                    Log.d("Repository", "✅ Latest: ${latest.size} dramas | Total unique: ${allDramas.size}")
                } catch (e: Exception) {
                    Log.e("Repository", "❌ Latest failed: ${e.message}")
                }
                
                lastRequestTime = System.currentTimeMillis()
                
                val finalList = allDramas.values.toList()
                
                // Filter out dramas with null critical fields
                val validDramas = finalList.filter { drama ->
                    val isValid = drama.bookName != null && drama.coverUrl != null
                    if (!isValid) {
                        Log.w("Repository", "⚠️ Filtered out drama with null fields: bookId=${drama.bookId}, bookName=${drama.bookName}, coverUrl=${drama.coverUrl}")
                    }
                    isValid
                }
                
                // Cache the result
                allDramasCache = validDramas
                cacheLoadTime = System.currentTimeMillis()
                
                Log.d("Repository", "🎉 FINAL: ${validDramas.size} VALID dramas (filtered ${finalList.size - validDramas.size} with null fields)")
                
                Result.success(validDramas)
            } catch (e: Exception) {
                lastRequestTime = System.currentTimeMillis()
                
                if (e.message?.contains("429") == true) {
                    Log.e("Repository", "🚫 Rate limited! Waiting 10 seconds...")
                    delay(10000)
                }
                
                Log.e("Repository", "❌ Error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Get Episode URL - dengan aggressive rate limiting
     */
    override suspend fun getEpisodeUrl(bookId: String): String {
        return apiMutex.withLock {
            try {
                // EXTRA LONG delay untuk episode URL
                val timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime
                if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                    val waitTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest
                    delay(waitTime)
                }
                
                Log.d("Repository", "📥 Fetching episodes for $bookId...")
                val episodes = api.getEpisodes(bookId)
                lastRequestTime = System.currentTimeMillis()
                
                if (episodes.isNotEmpty()) {
                    val url = episodes[0].getBestVideoUrl()
                    Log.d("Repository", "✅ Got video URL for $bookId")
                    return url
                }
                
                Log.w("Repository", "⚠️ No episodes found for $bookId")
                ""
            } catch (e: Exception) {
                lastRequestTime = System.currentTimeMillis()
                
                if (e.message?.contains("429") == true) {
                    Log.e("Repository", "🚫 Rate limited on episode fetch!")
                    return ""
                }
                
                Log.e("Repository", "❌ Error fetching url for $bookId: ${e.message}")
                ""
            }
        }
    }
    
    /**
     * Clear cache - force reload next time
     */
    fun clearCache() {
        allDramasCache = null
        Log.d("Repository", "🗑️ Cache cleared")
    }
}
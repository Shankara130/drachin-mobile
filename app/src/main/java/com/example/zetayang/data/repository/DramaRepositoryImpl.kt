package com.example.zetayang.data.repository

import android.util.Log
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CompletableDeferred

import com.example.zetayang.domain.repository.DramaRepository
import com.example.zetayang.data.api.DramaApiService
import com.example.zetayang.data.model.DramaBook

class DramaRepositoryImpl(private val api: DramaApiService) : DramaRepository {

    // Mutex untuk mencegah multiple concurrent requests
    private val apiMutex = Mutex()
    private var lastRequestTime = 0L
    private val MIN_REQUEST_INTERVAL = 1500L // Turunkan ke 1.5 detik
    
    // Cache untuk ALL dramas dari SEMUA endpoint
    private var allDramasCache: List<DramaBook>? = null
    private var cacheLoadTime = 0L
    private val CACHE_LIFETIME = 600_000L // 10 menit
    
    // Episode URL Cache & Request Deduplication
    private val episodeUrlCache = mutableMapOf<String, String>()
    private val ongoingRequests = mutableMapOf<String, CompletableDeferred<String>>()
    private val episodeMutex = Mutex()

    /**
     * Get Home Feed - SUPER OPTIMIZED: Load dari SEMUA endpoint secara parallel
     * Menggabungkan: ForYou + Latest + Trending = BANYAK drama unik!
     */
    override suspend fun getHomeFeed(): Result<List<DramaBook>> {
        return apiMutex.withLock {
            try {
                Log.d("Repository", "🔍 getHomeFeed() called")
                
                // Check cache first
                if (allDramasCache != null) {
                    val cacheAge = System.currentTimeMillis() - cacheLoadTime
                    if (cacheAge < CACHE_LIFETIME) {
                        Log.d("Repository", "✅ Using cached data (${allDramasCache!!.size} dramas, age: ${cacheAge/1000}s)")
                        return Result.success(allDramasCache!!)
                    } else {
                        Log.d("Repository", "⏰ Cache expired (age: ${cacheAge/1000}s), reloading...")
                    }
                } else {
                    Log.d("Repository", "📭 No cache found, loading from API...")
                }
                
                Log.d("Repository", "🚀 Loading dramas from MULTIPLE endpoints (PARALLEL)...")
                val startTime = System.currentTimeMillis()
                
                // STRATEGY: Load 3 endpoints in PARALLEL untuk speed + diversity
                val allDramas = coroutineScope {
                    val forYouDeferred = async {
                        try {
                            delay(100) // Slight stagger to avoid burst
                            val result = api.getHomeFeed()
                            Log.d("Repository", "✅ ForYou: ${result.size} dramas")
                            result
                        } catch (e: Exception) {
                            Log.e("Repository", "❌ ForYou failed: ${e.message}")
                            emptyList<DramaBook>()
                        }
                    }
                    
                    val latestDeferred = async {
                        try {
                            delay(200) // Stagger 200ms
                            val result = api.getLatestDramas()
                            Log.d("Repository", "✅ Latest: ${result.size} dramas")
                            result
                        } catch (e: Exception) {
                            Log.e("Repository", "❌ Latest failed: ${e.message}")
                            emptyList<DramaBook>()
                        }
                    }
                    
                    val trendingDeferred = async {
                        try {
                            delay(300) // Stagger 300ms
                            val result = api.getTrendingDramas()
                            Log.d("Repository", "✅ Trending: ${result.size} dramas")
                            result
                        } catch (e: Exception) {
                            Log.e("Repository", "❌ Trending failed: ${e.message}")
                            emptyList<DramaBook>()
                        }
                    }
                    
                    // Wait for all and combine
                    val forYou = forYouDeferred.await()
                    val latest = latestDeferred.await()
                    val trending = trendingDeferred.await()
                    
                    // Combine all sources
                    forYou + latest + trending
                }
                
                lastRequestTime = System.currentTimeMillis()
                
                Log.d("Repository", "📦 Combined ${allDramas.size} dramas from all sources")
                
                // Use LinkedHashMap untuk GUARANTEED uniqueness by bookId
                val uniqueDramas = LinkedHashMap<String, DramaBook>()
                var filteredCount = 0
                var duplicateCount = 0
                
                allDramas.forEach { drama ->
                    // Filter: Only add if both bookName and coverUrl exist
                    if (drama.bookName != null && drama.coverUrl != null) {
                        if (!uniqueDramas.containsKey(drama.bookId)) {
                            uniqueDramas[drama.bookId] = drama
                        } else {
                            duplicateCount++
                        }
                    } else {
                        filteredCount++
                        Log.w("Repository", "⚠️ Filtered drama ${drama.bookId} (null fields)")
                    }
                }
                
                val validDramas = uniqueDramas.values.toList()
                val loadTime = System.currentTimeMillis() - startTime
                
                // Cache the result
                allDramasCache = validDramas
                cacheLoadTime = System.currentTimeMillis()
                
                Log.d(
                    "Repository", 
                    "🎉 Loaded ${validDramas.size} UNIQUE dramas in ${loadTime}ms " +
                    "(filtered $filteredCount, skipped $duplicateCount duplicates)"
                )
                
                // Verify no duplicates in final result
                val bookIds = validDramas.map { it.bookId }
                val finalDuplicates = bookIds.size - bookIds.toSet().size
                if (finalDuplicates > 0) {
                    Log.e("Repository", "❌ FOUND $finalDuplicates DUPLICATES IN FINAL LIST!")
                } else {
                    Log.d("Repository", "✅ VERIFIED: Zero duplicates in final list")
                }
                
                Result.success(validDramas)
            } catch (e: Exception) {
                lastRequestTime = System.currentTimeMillis()
                
                if (e.message?.contains("429") == true) {
                    Log.e("Repository", "🚫 Rate limited! Waiting 5 seconds...")
                    delay(5000)
                }
                
                Log.e("Repository", "❌ Error: ${e.message}")
                Result.failure(e)
            }
        }
    }

    /**
     * Get Episode URL - WITH REQUEST DEDUPLICATION
     * Prevents multiple simultaneous requests for the same bookId
     */
    override suspend fun getEpisodeUrl(bookId: String): String {
        // Check cache first (outside mutex for performance)
        episodeUrlCache[bookId]?.let {
            Log.d("Repository", "⚡ Cache hit for $bookId")
            return it
        }
        
        return episodeMutex.withLock {
            // Double-check cache after acquiring lock
            episodeUrlCache[bookId]?.let {
                Log.d("Repository", "⚡ Cache hit (after lock) for $bookId")
                return it
            }
            
            // Check if request is already ongoing
            val existingRequest = ongoingRequests[bookId]
            if (existingRequest != null) {
                Log.d("Repository", "🔄 Request already in progress for $bookId, waiting...")
                // Wait for the existing request to complete
                return try {
                    existingRequest.await()
                } catch (e: Exception) {
                    Log.e("Repository", "❌ Waiting for existing request failed: ${e.message}")
                    ""
                }
            }
            
            // Create new deferred for this request
            val deferred = CompletableDeferred<String>()
            ongoingRequests[bookId] = deferred
            
            try {
                // Enforce rate limiting
                val timeSinceLastRequest = System.currentTimeMillis() - lastRequestTime
                if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                    val waitTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest
                    Log.d("Repository", "⏱️ Rate limit: waiting ${waitTime}ms for $bookId...")
                    delay(waitTime)
                }
                
                Log.d("Repository", "📥 Fetching episodes for $bookId...")
                val episodes = api.getEpisodes(bookId)
                lastRequestTime = System.currentTimeMillis()
                
                val url = if (episodes.isNotEmpty()) {
                    episodes[0].getBestVideoUrl()
                } else {
                    Log.w("Repository", "⚠️ No episodes found for $bookId")
                    ""
                }
                
                // Cache the result
                if (url.isNotEmpty()) {
                    episodeUrlCache[bookId] = url
                    Log.d("Repository", "✅ Got video URL for $bookId (cached)")
                }
                
                // Complete the deferred
                deferred.complete(url)
                
                url
            } catch (e: Exception) {
                lastRequestTime = System.currentTimeMillis()
                
                if (e.message?.contains("429") == true) {
                    Log.e("Repository", "🚫 Rate limited on episode fetch for $bookId!")
                }
                
                Log.e("Repository", "❌ Error fetching url for $bookId: ${e.message}")
                
                // Complete with error
                deferred.completeExceptionally(e)
                
                ""
            } finally {
                // Remove from ongoing requests
                ongoingRequests.remove(bookId)
            }
        }
    }
    
    /**
     * Search dramas by keyword - NEW FEATURE!
     */
    suspend fun searchDramas(query: String): Result<List<DramaBook>> {
        return try {
            Log.d("Repository", "🔍 Searching for: $query")
            val results = api.searchDramas(query)
            
            // Filter valid dramas
            val validResults = results.filter { 
                it.bookName != null && it.coverUrl != null 
            }
            
            Log.d("Repository", "✅ Found ${validResults.size} results for '$query'")
            Result.success(validResults)
        } catch (e: Exception) {
            Log.e("Repository", "❌ Search failed: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Clear cache - force reload next time
     */
    fun clearCache() {
        allDramasCache = null
        episodeUrlCache.clear()
        ongoingRequests.clear()
        Log.d("Repository", "🗑️ All caches cleared")
    }
    
    /**
     * Clear only episode URL cache (keep drama list cache)
     */
    fun clearEpisodeCache() {
        episodeUrlCache.clear()
        Log.d("Repository", "🗑️ Episode URL cache cleared")
    }
    
    /**
     * Get cache statistics for debugging
     */
    fun getCacheStats(): String {
        return """
            Drama Cache: ${allDramasCache?.size ?: 0} dramas
            Episode URL Cache: ${episodeUrlCache.size} URLs
            Ongoing Requests: ${ongoingRequests.size} requests
        """.trimIndent()
    }
}
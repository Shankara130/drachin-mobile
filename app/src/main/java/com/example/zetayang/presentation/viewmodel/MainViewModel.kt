package com.example.zetayang.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.data.repository.DramaRepositoryImpl
import com.example.zetayang.domain.usecase.GetVideoUrlUseCase
import com.example.zetayang.data.model.DramaBook

class MainViewModel : ViewModel() {

    // Dependency Injection manual
    private val api = RetrofitClient.instance
    val repository = DramaRepositoryImpl(api) // Make public for cache stats
    val getVideoUrlUseCase = GetVideoUrlUseCase(repository)

    private val _homeFeed = MutableLiveData<List<DramaBook>>()
    val homeFeed: LiveData<List<DramaBook>> = _homeFeed

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // Use LinkedHashMap to GUARANTEE uniqueness
    private val uniqueDramasMap = LinkedHashMap<String, DramaBook>()
    
    private var isLoadingMore = false
    private var hasLoadedInitialData = false
    private var shuffleCount = 0

    /**
     * Load initial data - FAST & DEDUPLICATED from MULTIPLE sources
     */
    fun loadHomeFeed() {
        Log.d("MainViewModel", "📞 loadHomeFeed() called | hasLoaded: $hasLoadedInitialData | mapSize: ${uniqueDramasMap.size}")
        
        // Jika sudah ada data, emit immediately
        if (hasLoadedInitialData && uniqueDramasMap.isNotEmpty()) {
            Log.d("MainViewModel", "✅ Using cached data (${uniqueDramasMap.size} dramas)")
            _homeFeed.value = uniqueDramasMap.values.toList()
            return
        }
        
        if (isLoadingMore) {
            Log.d("MainViewModel", "⚠️ Already loading...")
            return
        }
        
        Log.d("MainViewModel", "🚀 Starting fresh load from Repository...")
        isLoadingMore = true
        _isLoading.value = true

        viewModelScope.launch {
            Log.d("MainViewModel", "🔄 Calling repository.getHomeFeed()...")
            
            repository.getHomeFeed()
                .onSuccess { dramas ->
                    Log.d("MainViewModel", "📦 Repository returned ${dramas.size} dramas")
                    
                    // Clear existing data
                    uniqueDramasMap.clear()
                    
                    // Add to LinkedHashMap (GARANTEED unique by bookId)
                    var addedCount = 0
                    var skippedCount = 0
                    
                    dramas.forEach { drama ->
                        if (!uniqueDramasMap.containsKey(drama.bookId)) {
                            uniqueDramasMap[drama.bookId] = drama
                            addedCount++
                        } else {
                            skippedCount++
                            Log.w("MainViewModel", "⚠️ Skipped duplicate: ${drama.bookId}")
                        }
                    }
                    
                    hasLoadedInitialData = true
                    
                    // Convert to list and emit
                    val finalList = uniqueDramasMap.values.toList()
                    _homeFeed.value = finalList
                    
                    Log.d(
                        "MainViewModel", 
                        "✅ Loaded $addedCount dramas | Skipped $skippedCount duplicates | Final: ${finalList.size} UNIQUE"
                    )
                    
                    // Log cache statistics
                    Log.d("MainViewModel", "📊 Cache Stats:\n${repository.getCacheStats()}")
                    
                    // CRITICAL: Verify no duplicates in final list
                    val bookIds = finalList.map { it.bookId }
                    val uniqueIds = bookIds.toSet()
                    if (bookIds.size != uniqueIds.size) {
                        Log.e("MainViewModel", "❌ DUPLICATES IN FINAL LIST: ${bookIds.size - uniqueIds.size}")
                        
                        // Find and log duplicates
                        val duplicates = bookIds.groupingBy { it }.eachCount().filter { it.value > 1 }
                        Log.e("MainViewModel", "Duplicate bookIds: $duplicates")
                    } else {
                        Log.d("MainViewModel", "✅✅ VERIFIED: Zero duplicates in final list")
                    }
                }
                .onFailure { throwable ->
                    Log.e("MainViewModel", "❌ Repository failed: ${throwable.message}")
                    _error.value = throwable.message
                }
                .also {
                    isLoadingMore = false
                    _isLoading.value = false
                }
        }
    }

    /**
     * Load more - SHUFFLE existing unique data
     */
    fun loadMoreHomeFeed() {
        if (isLoadingMore) {
            Log.w("MainViewModel", "⚠️ Already loading, skipping...")
            return
        }
        
        if (uniqueDramasMap.isEmpty()) {
            Log.w("MainViewModel", "⚠️ No data to shuffle")
            return
        }
        
        isLoadingMore = true

        viewModelScope.launch {
            // Small delay untuk smooth UX
            delay(300)
            
            // Create shuffled list from unique map
            val shuffledList = uniqueDramasMap.values.toMutableList().apply {
                shuffle()
            }
            
            shuffleCount++
            
            // Emit shuffled list
            _homeFeed.value = shuffledList
            
            Log.d(
                "MainViewModel", 
                "🔀 Shuffle #$shuffleCount: ${shuffledList.size} dramas"
            )
            
            // Verify no duplicates after shuffle
            val bookIds = shuffledList.map { it.bookId }
            val uniqueIds = bookIds.toSet()
            if (bookIds.size != uniqueIds.size) {
                Log.e("MainViewModel", "❌ Duplicates after shuffle: ${bookIds.size - uniqueIds.size}")
            } else {
                Log.d("MainViewModel", "✅ Shuffle verified: Zero duplicates")
            }
            
            isLoadingMore = false
        }
    }
    
    /**
     * Search dramas - NEW FEATURE!
     */
    fun searchDramas(query: String) {
        if (query.isBlank()) {
            Log.w("MainViewModel", "⚠️ Empty search query")
            return
        }
        
        _isLoading.value = true
        
        viewModelScope.launch {
            repository.searchDramas(query)
                .onSuccess { results ->
                    Log.d("MainViewModel", "🔍 Search results: ${results.size} dramas")
                    
                    // Add to unique map (don't clear existing)
                    results.forEach { drama ->
                        if (!uniqueDramasMap.containsKey(drama.bookId)) {
                            uniqueDramasMap[drama.bookId] = drama
                        }
                    }
                    
                    // Emit search results
                    _homeFeed.value = results
                }
                .onFailure { throwable ->
                    Log.e("MainViewModel", "❌ Search failed: ${throwable.message}")
                    _error.value = "Search failed: ${throwable.message}"
                }
                .also {
                    _isLoading.value = false
                }
        }
    }

    /**
     * Get current loaded dramas count
     */
    fun getLoadedDramasCount(): Int = uniqueDramasMap.size

    /**
     * Check if currently loading
     */
    fun isCurrentlyLoading(): Boolean = isLoadingMore

    /**
     * Force reload - clear cache and reload from API
     */
    fun forceReload() {
        Log.d("MainViewModel", "🔄 Force reload - clearing cache...")
        repository.clearCache()
        uniqueDramasMap.clear()
        hasLoadedInitialData = false
        shuffleCount = 0
        loadHomeFeed()
    }

    /**
     * Reset data
     */
    fun resetData() {
        uniqueDramasMap.clear()
        hasLoadedInitialData = false
        isLoadingMore = false
        shuffleCount = 0
        Log.d("MainViewModel", "🔄 Data reset | hasLoaded: $hasLoadedInitialData | mapSize: ${uniqueDramasMap.size}")
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): String {
        return """
            ViewModel Unique Dramas: ${uniqueDramasMap.size}
            Has Loaded Initial: $hasLoadedInitialData
            Shuffle Count: $shuffleCount
            ${repository.getCacheStats()}
        """.trimIndent()
    }
}
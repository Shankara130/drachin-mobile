package com.example.zetayang.presentation.viewmodel

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
    private val repository = DramaRepositoryImpl(api)
    val getVideoUrlUseCase = GetVideoUrlUseCase(repository)

    private val _homeFeed = MutableLiveData<List<DramaBook>>()
    val homeFeed: LiveData<List<DramaBook>> = _homeFeed

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    // ORIGINAL list dari repository - NEVER SHUFFLED
    private var originalDramaList: List<DramaBook> = emptyList()
    
    private var isLoadingMore = false
    private var hasLoadedInitialData = false
    private var shuffleCount = 0

    /**
     * Load initial data - LOAD ALL DRAMAS ONCE
     * Repository akan fetch dari 3 endpoint: ForYou, Trending, Latest
     */
    fun loadHomeFeed() {
        // Jika sudah pernah load, emit cached data
        if (hasLoadedInitialData && originalDramaList.isNotEmpty()) {
            android.util.Log.d("MainViewModel", "✅ Using cached data (${originalDramaList.size} dramas)")
            _homeFeed.value = originalDramaList
            return
        }
        
        if (isLoadingMore) {
            android.util.Log.d("MainViewModel", "⚠️ Already loading...")
            return
        }
        
        isLoadingMore = true
        _isLoading.value = true

        viewModelScope.launch {
            android.util.Log.d("MainViewModel", "🔄 Loading ALL dramas from repository...")
            
            repository.getHomeFeed()
                .onSuccess { dramas ->
                    // Repository already ensures uniqueness
                    originalDramaList = dramas
                    hasLoadedInitialData = true
                    
                    // Emit original list
                    _homeFeed.value = dramas
                    
                    android.util.Log.d(
                        "MainViewModel", 
                        "✅ Loaded ${dramas.size} UNIQUE dramas from ALL endpoints"
                    )
                    
                    // Verify uniqueness
                    val uniqueIds = dramas.map { it.bookId }.toSet()
                    if (uniqueIds.size == dramas.size) {
                        android.util.Log.d("MainViewModel", "✅✅ VERIFIED: No duplicates!")
                    } else {
                        android.util.Log.e("MainViewModel", "❌ DUPLICATES FOUND: ${dramas.size - uniqueIds.size} duplicates")
                    }
                }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    android.util.Log.e("MainViewModel", "❌ Failed to load: ${throwable.message}")
                }
                .also {
                    isLoadingMore = false
                    _isLoading.value = false
                }
        }
    }

    /**
     * Load more - SHUFFLE existing data
     * TIDAK ada API call baru
     */
    fun loadMoreHomeFeed() {
        if (isLoadingMore) {
            android.util.Log.w("MainViewModel", "⚠️ Already loading, skipping...")
            return
        }
        
        if (originalDramaList.isEmpty()) {
            android.util.Log.w("MainViewModel", "⚠️ No data to shuffle")
            return
        }
        
        isLoadingMore = true

        viewModelScope.launch {
            // Small delay untuk smooth UX
            delay(500)
            
            // Create shuffled COPY (NEVER modify original)
            val shuffledCopy = originalDramaList.toMutableList().apply {
                shuffle()
            }
            
            shuffleCount++
            
            // Emit shuffled copy
            _homeFeed.value = shuffledCopy
            
            android.util.Log.d(
                "MainViewModel", 
                "🔀 Shuffle #$shuffleCount: ${shuffledCopy.size} dramas"
            )
            
            // Verify no duplicates after shuffle
            val uniqueIds = shuffledCopy.map { it.bookId }.toSet()
            if (uniqueIds.size == shuffledCopy.size) {
                android.util.Log.d("MainViewModel", "✅ Shuffle verified: No duplicates")
            } else {
                android.util.Log.e("MainViewModel", "❌ Duplicates after shuffle: ${shuffledCopy.size - uniqueIds.size}")
            }
            
            isLoadingMore = false
        }
    }

    /**
     * Get current loaded dramas count
     */
    fun getLoadedDramasCount(): Int = originalDramaList.size

    /**
     * Check if currently loading
     */
    fun isCurrentlyLoading(): Boolean = isLoadingMore

    /**
     * Force reload - clear cache and reload ALL from API
     */
    fun forceReload() {
        android.util.Log.d("MainViewModel", "🔄 Force reload - clearing cache...")
        repository.clearCache()
        originalDramaList = emptyList()
        hasLoadedInitialData = false
        shuffleCount = 0
        loadHomeFeed()
    }

    /**
     * Reset data
     */
    fun resetData() {
        originalDramaList = emptyList()
        hasLoadedInitialData = false
        isLoadingMore = false
        shuffleCount = 0
        android.util.Log.d("MainViewModel", "🔄 Data reset")
    }
}
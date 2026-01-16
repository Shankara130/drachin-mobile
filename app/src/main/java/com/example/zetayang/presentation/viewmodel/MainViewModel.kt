package com.example.zetayang.presentation.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.data.repository.DramaRepositoryImpl
import com.example.zetayang.domain.usecase.GetVideoUrlUseCase
import com.example.zetayang.data.model.DramaBook

class MainViewModel : ViewModel() {

    // Dependency Injection manual
    private val api = RetrofitClient.instance
    private val repository = DramaRepositoryImpl(api)
    val getVideoUrlUseCase = GetVideoUrlUseCase(repository) // Public untuk Adapter

    private val _homeFeed = MutableLiveData<List<DramaBook>>()
    val homeFeed: LiveData<List<DramaBook>> = _homeFeed

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    fun loadHomeFeed() {
        viewModelScope.launch {
            repository.getHomeFeed()
                .onSuccess { list ->
                    _homeFeed.value = list
                }
                .onFailure { t ->
                    _error.value = t.message
                }
        }
    }
}
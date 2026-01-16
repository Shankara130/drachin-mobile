package com.example.zetayang.domain.usecase

import com.example.zetayang.domain.repository.DramaRepository

class GetVideoUrlUseCase(private val repository: DramaRepository) {
    suspend operator fun invoke(bookId: String): String {
        val url = repository.getEpisodeUrl(bookId)
        
        // Fallback ke dummy jika kosong
        return url.ifEmpty { 
            "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8" 
        }
    }
}
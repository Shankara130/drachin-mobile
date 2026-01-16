package com.example.zetayang

data class ApiResponse(
    val result: List<DramaModel>? = null,
    val data: List<DramaModel>? = null
)

data class DramaModel(
    val bookId: String?,
    val id: String?,
    val bookName: String?,
    val title: String?,
    val coverWap: String?,
    val img: String?,
    val desc: String?
) {
    // Helper untuk ambil data yang konsisten (karena API kadang pakai bookId, kadang id)
    fun getRealId() = bookId ?: id ?: ""
    fun getRealTitle() = bookName ?: title ?: "Tanpa Judul"
    fun getRealImage() = coverWap ?: img ?: ""
}
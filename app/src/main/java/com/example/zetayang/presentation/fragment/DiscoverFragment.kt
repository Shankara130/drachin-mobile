package com.example.zetayang.presentation.fragment

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.zetayang.MainActivity
import com.example.zetayang.R
import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.data.model.DramaBook
import com.example.zetayang.presentation.adapter.DiscoverAdapter
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class DiscoverFragment : Fragment() {
    
    private lateinit var etSearch: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var tvEmptyMessage: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: DiscoverAdapter
    
    private var allDramas: List<DramaBook> = emptyList()
    private var filteredDramas: List<DramaBook> = emptyList()
    private var currentSearchQuery: String = ""
    private var currentCategory: String = "Populer"
    private var isLoading: Boolean = false
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("DiscoverFragment", "📱 onCreateView called")
        return inflater.inflate(R.layout.fragment_discover, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        Log.d("DiscoverFragment", "📱 onViewCreated called")
        
        etSearch = view.findViewById(R.id.etSearch)
        chipGroup = view.findViewById(R.id.chipGroup)
        recyclerView = view.findViewById(R.id.recyclerDiscover)
        emptyState = view.findViewById(R.id.emptyState)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        progressBar = view.findViewById(R.id.progressBar)
        
        setupRecyclerView()
        setupSearch()
        setupChipFilters()
        loadDramas()
    }
    
    private fun setupRecyclerView() {
        Log.d("DiscoverFragment", "🔧 Setting up RecyclerView")
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.layoutManager = gridLayoutManager
        adapter = DiscoverAdapter(emptyList())
        recyclerView.adapter = adapter
    }
    
    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                try {
                    currentSearchQuery = s?.toString()?.trim() ?: ""
                    
                    // Only filter if data is loaded
                    if (allDramas.isNotEmpty()) {
                        filterDramas()
                        Log.d("DiscoverFragment", "🔍 Search query: '$currentSearchQuery'")
                    }
                } catch (e: Exception) {
                    Log.e("DiscoverFragment", "❌ Search error: ${e.message}", e)
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun setupChipFilters() {
        chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) {
                // No chip selected, default to "Populer"
                currentCategory = "Populer"
                view?.findViewById<Chip>(R.id.chipPopuler)?.isChecked = true
                return@setOnCheckedStateChangeListener
            }
            
            val selectedChipId = checkedIds[0]
            val selectedChip = view?.findViewById<Chip>(selectedChipId)
            currentCategory = selectedChip?.text.toString()
            
            Log.d("DiscoverFragment", "🏷️ Selected category: $currentCategory")
            filterDramas()
        }
    }
    
    private fun filterDramas() {
        try {
            filteredDramas = allDramas.filter { drama ->
                // IMPORTANT: Skip dramas with null bookName
                val name = drama.bookName
                if (name == null) {
                    Log.w("DiscoverFragment", "⚠️ Skipping drama with null bookName: ${drama.bookId}")
                    return@filter false
                }
                
                // Filter by search query
                val matchesSearch = if (currentSearchQuery.isEmpty()) {
                    true
                } else {
                    val intro = drama.introduction ?: ""
                    name.contains(currentSearchQuery, ignoreCase = true) ||
                    intro.contains(currentSearchQuery, ignoreCase = true)
                }
                
                // Filter by category
                val matchesCategory = when (currentCategory) {
                    "Populer" -> true // Show all for Populer
                    "Sistem" -> {
                        val tags = drama.tags ?: emptyList()
                        tags.any { it.contains("sistem", ignoreCase = true) }
                    }
                    "Harem" -> {
                        val tags = drama.tags ?: emptyList()
                        tags.any { it.contains("harem", ignoreCase = true) }
                    }
                    "Kekuatan super" -> {
                        val tags = drama.tags ?: emptyList()
                        tags.any { 
                            it.contains("kekuatan", ignoreCase = true) || 
                            it.contains("super", ignoreCase = true)
                        }
                    }
                    else -> true
                }
                
                matchesSearch && matchesCategory
            }
            
            // Sort by playCount (popularity) for "Populer" category
            if (currentCategory == "Populer") {
                filteredDramas = filteredDramas.sortedByDescending { drama ->
                    try {
                        drama.playCount?.toLongOrNull() ?: 0L
                    } catch (e: Exception) {
                        0L
                    }
                }
            }
            
            Log.d("DiscoverFragment", "📊 Filtered: ${filteredDramas.size} / ${allDramas.size} dramas")
            updateAdapter()
        } catch (e: Exception) {
            Log.e("DiscoverFragment", "❌ Filter error: ${e.message}", e)
            Toast.makeText(requireContext(), "Error filtering: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateAdapter() {
        try {
            adapter = DiscoverAdapter(filteredDramas)
            recyclerView.adapter = adapter
            
            // Show/hide empty state
            if (filteredDramas.isEmpty() && allDramas.isNotEmpty()) {
                recyclerView.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                
                if (currentSearchQuery.isNotEmpty()) {
                    tvEmptyMessage.text = "Tidak ditemukan \"$currentSearchQuery\""
                } else {
                    tvEmptyMessage.text = "Tidak ada drama di kategori \"$currentCategory\""
                }
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e("DiscoverFragment", "❌ Update adapter error: ${e.message}", e)
            Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadDramas() {
        Log.d("DiscoverFragment", "🔥 Loading dramas from MainActivity's ViewModel...")
        
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyState.visibility = View.GONE
        isLoading = true
        
        lifecycleScope.launch {
            try {
                // SOLUTION: Use MainActivity's ViewModel instead of creating new Repository
                // This shares the same cache and data
                val mainActivity = requireActivity() as? MainActivity
                val viewModel = mainActivity?.let {
                    androidx.lifecycle.ViewModelProvider(it)[com.example.zetayang.presentation.viewmodel.MainViewModel::class.java]
                }
                
                if (viewModel != null) {
                    // Get data from shared ViewModel
                    viewModel.homeFeed.observe(viewLifecycleOwner) { dramas ->
                        if (dramas.isNotEmpty()) {
                            allDramas = dramas
                            
                            Log.d("DiscoverFragment", "✅ Loaded ${allDramas.size} dramas from shared ViewModel")
                            
                            // Hide loading
                            progressBar.visibility = View.GONE
                            isLoading = false
                            
                            // Filter and display
                            filterDramas()
                        }
                    }
                    
                    // Trigger load if not loaded yet
                    if (viewModel.getLoadedDramasCount() == 0) {
                        viewModel.loadHomeFeed()
                    }
                } else {
                    // Fallback: Create own repository instance
                    val repository = com.example.zetayang.data.repository.DramaRepositoryImpl(
                        RetrofitClient.instance
                    )
                    
                    val result = repository.getHomeFeed()
                    result.onSuccess { dramas ->
                        allDramas = dramas
                        Log.d("DiscoverFragment", "✅ Loaded ${allDramas.size} dramas from fallback repository")
                        progressBar.visibility = View.GONE
                        isLoading = false
                        filterDramas()
                    }.onFailure { exception ->
                        handleError(exception)
                    }
                }
            } catch (e: Exception) {
                handleError(e)
            }
        }
    }
    
    private fun handleError(exception: Throwable) {
        Log.e("DiscoverFragment", "❌ Exception: ${exception.message}", exception)
        progressBar.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        tvEmptyMessage.text = "Gagal memuat data: ${exception.message}"
        isLoading = false
        Toast.makeText(requireContext(), "Gagal memuat: ${exception.message}", Toast.LENGTH_SHORT).show()
    }
}
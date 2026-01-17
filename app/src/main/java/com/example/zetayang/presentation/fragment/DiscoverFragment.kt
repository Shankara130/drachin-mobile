package com.example.zetayang.presentation.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.zetayang.R
import com.example.zetayang.data.api.RetrofitClient
import com.example.zetayang.presentation.adapter.DiscoverAdapter
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.launch

class DiscoverFragment : Fragment() {
    
    private lateinit var chipGroup: ChipGroup
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: DiscoverAdapter
    
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
        
        chipGroup = view.findViewById(R.id.chipGroup)
        recyclerView = view.findViewById(R.id.recyclerDiscover)
        
        setupRecyclerView()
        loadDramas()
        
        // Chip filter listener (optional - untuk future implementation)
        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            // TODO: Filter by category
        }
    }
    
    private fun setupRecyclerView() {
        Log.d("DiscoverFragment", "🔧 Setting up RecyclerView")
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        adapter = DiscoverAdapter(emptyList())
        recyclerView.adapter = adapter
    }
    
    private fun loadDramas() {
        Log.d("DiscoverFragment", "📥 Loading dramas...")
        lifecycleScope.launch {
            try {
                val dramas = RetrofitClient.instance.getHomeFeed()
                Log.d("DiscoverFragment", "✅ Loaded ${dramas.size} dramas")
                adapter = DiscoverAdapter(dramas)
                recyclerView.adapter = adapter
            } catch (e: Exception) {
                Log.e("DiscoverFragment", "❌ Error: ${e.message}")
                Toast.makeText(requireContext(), "Gagal memuat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
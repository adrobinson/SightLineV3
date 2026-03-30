package com.example.sightlinev3.graph

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.serialization.json.Json

class GraphViewModel(private val repository: GraphRepository) : ViewModel() {

    private val graph by lazy { repository.loadGraphFromAssets() }
    private val service by lazy { GraphService(graph) }

    fun runPathfinding(start: String, goal: String): String {
        val path = service.findPath(graph, start, goal)
        return path?.joinToString(" - > ") ?: "No path found"
    }




}
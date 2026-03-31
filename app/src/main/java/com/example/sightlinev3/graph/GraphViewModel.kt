package com.example.sightlinev3.graph

import android.content.Context
import androidx.lifecycle.ViewModel
import kotlinx.serialization.json.Json

class GraphViewModel(private val repository: GraphRepository) : ViewModel() {

    private val graph by lazy { repository.loadGraphFromAssets() }
    private val service by lazy { GraphService(graph) }

    fun runPathfinding(start: String, goal: String): List<PathStep>? {
        val path = service.findPath(start, goal)
        if(path != null){
            val pathSteps = service.buildPathDetails(path);
            return pathSteps
        }
        return null
    }




}
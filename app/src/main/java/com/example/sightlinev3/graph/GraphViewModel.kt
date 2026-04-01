package com.example.sightlinev3.graph

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

class GraphViewModel(private val repository: GraphRepository) : ViewModel() {

    private val graph by lazy { repository.loadGraphFromAssets() }
    private val service by lazy { GraphService(graph) }
    private val _currentNode = MutableStateFlow<Node?>(null)
    val currentNode: StateFlow<Node?> = _currentNode

    fun runPathfinding(start: String, goal: String): List<PathStep>? {
        val path = service.findPath(start, goal)
        if(path != null){
            val pathSteps = service.buildPathDetails(path);
            Log.d("VIEW_MODEL",pathSteps.toString())
            return pathSteps
        }
        return null
    }

    fun onQrScanned(nodeId: String) {
        if (_currentNode.value?.id == nodeId) return
        val node = graph.nodes[nodeId];

        if(node != null){
            _currentNode.value = node
            Log.d("GRAPH_VM", "User is now at ${node.name }")
        } else {
            Log.d("GRAPH_VM", "Invalid QE: $nodeId")
        }
    }




}
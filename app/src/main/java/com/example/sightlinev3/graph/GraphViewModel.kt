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

    /**
     * Invokes graphService to run pathfinding, this returns a list of
     * node ids e.g. [01, 02, 06, 07]
     *
     * graphService maps node ids to 'PathSteps', that includes more
     * contextual information that we can pass to LLM API later
     */
    fun runPathfinding(start: String, goal: String): List<PathStep>? {
        val path = service.findPath(start, goal)
        if(path != null){
            val pathSteps = service.buildPathDetails(path);
            Log.d("VIEW_MODEL",pathSteps.toString())
            return pathSteps
        }
        return null
    }

    /**
     * Checks if the value scanned from the QR code is associated
     * with any nodeIds, if it is we set currentNode to that node.
     */
    fun onQrScanned(nodeId: String) {
        if (_currentNode.value?.id == nodeId) return
        val node = graph.nodes[nodeId];

        if(node != null){
            _currentNode.value = node
            Log.d("GRAPH_VM", "User is now at ${node.name }")
        } else {
            Log.d("GRAPH_VM", "Invalid QR: $nodeId")
        }
    }




}
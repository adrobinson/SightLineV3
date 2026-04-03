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
    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex

    private val _routeState = MutableStateFlow<RouteState>(RouteState.Idle)
    val routeState: StateFlow<RouteState> = _routeState

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
            return pathSteps
        }
        return null
    }

    /**
     * Checks if the value scanned from the QR code is associated
     * with any nodeIds, if it is we set currentNode to that node.
     */
    fun onQrScanned(nodeId: String) {
        val currentRoute = _routeState.value

        if(currentRoute is RouteState.Active) {
            // If in route mode - only accept the next expected node
            val nextExpectedNode = currentRoute.steps.getOrNull(_currentStepIndex.value + 1)

            if(nextExpectedNode?.id == nodeId) {
                _currentStepIndex.value++
                _currentNode.value = graph.nodes[nodeId]
                Log.d("GRAPH_VM", "Advanced to step ${_currentStepIndex.value}")

                //Check if destination is reached
                if(currentStepIndex.value == currentRoute.steps.size - 1) {
                    Log.d("GRAPH_VM", "Destination reached")
                    _routeState.value = RouteState.Reached(currentRoute.destination)
                }
            } else {
                Log.d("GRAPH_VM", "Wrong node scanned in route mode, ignoring")
            }
            return
        }

        if (_currentNode.value?.id == nodeId) return
        val node = graph.nodes[nodeId];

        if(node != null){
            _currentNode.value = node
            Log.d("GRAPH_VM", "User is now at ${node.name }")
        } else {
            Log.d("GRAPH_VM", "Invalid QR: $nodeId")
        }
    }

    /**
     * This function is ran when the user asks to go to a new destination, the speech-to-text
     * query is passed into the function, we validate: The user is localized at a Node, we can find
     * the room the user is asking to go to, and there is a route between the two nodes
     *
     * A path is then built and 'RouteState' is made active
     */
    fun startNavigation(query:String){
        val start = _currentNode.value
        if(start == null) {
            _routeState.value = RouteState.Error("You haven't localised yet - scan a valid location first")
            return
        }

        val destination = service.findNodeByName(query)
        if(destination == null){
            _routeState.value = RouteState.Error("Can't find that room")
            return
        }

        if(destination!!.id == start.id){
            _routeState.value = RouteState.Error("You're already there")
            return
        }

        val path = service.findPath(start.id, destination.id)
        if(path == null) {
            _routeState.value = RouteState.Error("No path found to ${destination.name}")
            return
        }

        val steps = service.buildPathDetails(path)
        _currentStepIndex.value = 0
        Log.d("GRAPH_VM", "Route found: ${steps.map {it.description}}")
        _routeState.value = RouteState.Active(
            destination = destination,
            steps = steps
        )
    }

    fun clearRoute() {
        _routeState.value = RouteState.Idle
        _currentStepIndex.value = 0
    }




}
package com.example.sightlinev3.graph

/**
 * When user wants to navigate to a destination, the app goes into route state
 */
sealed class RouteState {
    object Idle : RouteState()
    data class Active(
        val destination: Node,
        val steps: List<PathStep>
    ) : RouteState()
    data class Error(val message: String): RouteState()
}
package com.example.sightlinev3.graph

import android.content.Context
import kotlinx.serialization.json.Json

class GraphRepository(private val context: Context) {

    fun loadGraphFromAssets(): Graph {
        val jsonString = context.assets.open("graph.json")
            .bufferedReader()
            .use { it.readText() }

        val dto = Json.decodeFromString<GraphDto>(jsonString)

        val adjacency = dto.edges.groupBy { it.from }

        return Graph(
            nodes = dto.nodes,
            adjacency = adjacency
        )
    }
}
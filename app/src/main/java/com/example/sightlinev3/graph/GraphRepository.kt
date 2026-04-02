package com.example.sightlinev3.graph

import android.content.Context
import kotlinx.serialization.json.Json

class GraphRepository(private val context: Context) {

    /**
     * Maps Graph from JSON file to GraphDto, then from there maps
     * to Graph object with adjacency map: easier lookup for
     * neighbors of a particular node
     */
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
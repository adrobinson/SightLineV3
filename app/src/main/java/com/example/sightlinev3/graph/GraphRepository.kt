package com.example.sightlinev3.graph

import android.content.Context
import kotlinx.serialization.json.Json

class GraphRepository(private val context: Context) {

    fun loadGraphFromAssets(): Graph {
        val jsonString = context.assets.open("graph.json")
            .bufferedReader()
            .use { it.readText() }

        return Json.decodeFromString(jsonString)
    }
}
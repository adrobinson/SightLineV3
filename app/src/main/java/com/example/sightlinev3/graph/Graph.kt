package com.example.sightlinev3.graph
import kotlinx.serialization.Serializable

@Serializable
data class Node(
    val id: String,
    val name: String,
    val type: String
)

@Serializable
data class Edge(
    val from: String,
    val to: String,
    val visualDescription: String
)

@Serializable
data class Graph(
    val nodes: Map<String, Node>,
    val edges: List<Edge>
)

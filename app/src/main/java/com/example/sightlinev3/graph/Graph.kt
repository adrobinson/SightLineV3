package com.example.sightlinev3.graph
import kotlinx.serialization.Serializable



@Serializable
data class GraphDto(
    val nodes: Map<String, Node>,
    val edges: List<Edge>
)

@Serializable
data class Node(
    val id: String,
    val name: String,
    val type: String,
    val aliases: List<String> = emptyList()
)

@Serializable
data class Edge(
    val from: String,
    val to: String,
    val description: String
)

@Serializable
data class Graph(
    val nodes: Map<String, Node>,
    val adjacency: Map<String, List<Edge>>
)

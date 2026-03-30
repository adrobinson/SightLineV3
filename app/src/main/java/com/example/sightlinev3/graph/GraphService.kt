package com.example.sightlinev3.graph

class GraphService(private val graph: Graph) {

    fun findPath(graph: Graph, start: String, goal: String): List<String>? {
        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf<String>()

        queue.add(listOf(start))

        while(queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val node = path.last()

            if (node == goal) return path

            if (node !in visited) {
                visited.add(node)

                val neighbours = graph.edges
                    .filter { it.from == node }
                    .map { it.to }

                for (neighbour in neighbours) {
                    val newPath = path + neighbour
                    queue.add(newPath)
                }
            }
        }
        return null
    }


}
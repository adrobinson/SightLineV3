package com.example.sightlinev3.graph

class GraphService(private val graph: Graph) {

    /**
     * Standard BFS algorithm
     */
    fun findPath(start: String, goal: String): List<String>? {
        val queue = ArrayDeque<List<String>>()
        val visited = mutableSetOf<String>()

        queue.add(listOf(start))

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val node = path.last()

            if (node == goal) return path

            if (node !in visited) {
                visited.add(node)

                val neighbours = graph.adjacency[node]
                    ?.map { it.to }
                    ?: emptyList()

                for (neighbour in neighbours) {
                    val newPath = path + neighbour
                    queue.add(newPath)
                }
            }
        }
        return null
    }

    /**
     * Convert the 'path' List into a List of 'Path Steps'
     * that include contextual information about the nodes
     */
    fun buildPathDetails(path: List<String>): List<PathStep> {
        val result = mutableListOf<PathStep>();

        for (i in path.indices) {
            val node = graph.nodes[path[i]]!!

            /**
             * We don't need a visual description of the room we are in,
             * we only need visual descriptions of how to get to the next node
             * visual from 01 -> 02 is assigned to 02
             */
            val visual = if (i == 0) {
                null
            } else {
                val prev = path[i - 1]
                graph.adjacency[prev]?.find { it.to == node.id }?.description
            }

            result.add(
                PathStep(
                    id = node.id,
                    name = node.name,
                    type = node.type,
                    description = visual
                )
            )

        }
        return result
    }


}
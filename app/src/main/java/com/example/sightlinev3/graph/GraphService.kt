package com.example.sightlinev3.graph

class GraphService(private val graph: Graph) {

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

    /**
     * Convert the 'path' List into a List of 'Path Steps'
     * that include contextual information about the nodes
     */
    fun buildPathDetails(path: List<String>): List<PathStep> {
        val nodesById = graph.nodes.values.associateBy { it.id }

        val result = mutableListOf<PathStep>();

        for (i in path.indices) {
            val nodeId = path[i]
            val node = nodesById[nodeId]!!

            /**
             * We don't need a visual description of the room we are in,
             * we only need visual descriptions of how to get to the next node
             * visual from 01 -> 02 is assigned to 02
             */
            val visual = if (i == 0) {
                null
            } else {
                val prev = path[i - 1]
                graph.edges.find { it.from == prev && it.to == nodeId }?.visualDescription
            }

            result.add(
                PathStep(
                    id = node.id,
                    name = node.name,
                    type = node.type,
                    visualDescription = visual
                )
            )

        }
        return result
    }


}
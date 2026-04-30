package com.example.sightlinev3.graph

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for GraphService.
 * Place this file in: app/src/test/java/com/example/sightlinev3/graph/
 * Run with: ./gradlew test
 */
class GraphServiceTest {

    // -------------------------------------------------------------------------
    // Helpers — build in-memory Graph objects without loading JSON
    // -------------------------------------------------------------------------

    private fun node(id: String, name: String, vararg aliases: String) =
        Node(id = id, name = name, type = "room", aliases = aliases.toList())

    private fun edge(from: String, to: String, desc: String = "Walk from $from to $to") =
        Edge(from = from, to = to, description = desc)

    private fun graph(nodes: List<Node>, edges: List<Edge>): Graph {
        val nodeMap = nodes.associateBy { it.id }
        val adjacency = edges.groupBy { it.from }
        return Graph(nodes = nodeMap, adjacency = adjacency)
    }

    // -------------------------------------------------------------------------
    // BFS — basic correctness
    // -------------------------------------------------------------------------

    @Test
    fun findPath_directConnection_returnsTwoNodePath() {
        val g = graph(
            nodes = listOf(node("A", "Hall"), node("B", "Kitchen")),
            edges = listOf(edge("A", "B"))
        )
        val result = GraphService(g).findPath("A", "B")
        assertEquals(listOf("A", "B"), result)
    }

    @Test
    fun findPath_twoHops_returnsCorrectPath() {
        val g = graph(
            nodes = listOf(node("A", "Hall"), node("B", "Lounge"), node("C", "Kitchen")),
            edges = listOf(edge("A", "B"), edge("B", "C"))
        )
        val result = GraphService(g).findPath("A", "C")
        assertEquals(listOf("A", "B", "C"), result)
    }

    @Test
    fun findPath_startEqualsGoal_returnsSingleNodePath() {
        val g = graph(
            nodes = listOf(node("A", "Hall"), node("B", "Kitchen")),
            edges = listOf(edge("A", "B"))
        )
        val result = GraphService(g).findPath("A", "A")
        assertEquals(listOf("A"), result)
    }

    @Test
    fun findPath_noPathExists_returnsNull() {
        // B has no outgoing edges to A; graph is directed
        val g = graph(
            nodes = listOf(node("A", "Hall"), node("B", "Kitchen")),
            edges = listOf(edge("A", "B"))
        )
        val result = GraphService(g).findPath("B", "A")
        assertNull(result)
    }

    @Test
    fun findPath_disconnectedGraph_returnsNull() {
        val g = graph(
            nodes = listOf(
                node("A", "Hall"), node("B", "Kitchen"),
                node("C", "Garage"), node("D", "Garden")
            ),
            edges = listOf(edge("A", "B"), edge("C", "D")) // two isolated pairs
        )
        val result = GraphService(g).findPath("A", "D")
        assertNull(result)
    }

    @Test
    fun findPath_cycleInGraph_doesNotLoopInfinitely() {
        // A -> B -> C -> A (cycle) but goal D unreachable from A
        val g = graph(
            nodes = listOf(
                node("A", "Hall"), node("B", "Lounge"),
                node("C", "Kitchen"), node("D", "Garden")
            ),
            edges = listOf(edge("A", "B"), edge("B", "C"), edge("C", "A"))
        )
        val result = GraphService(g).findPath("A", "D")
        assertNull("BFS should terminate and return null, not loop forever", result)
    }

    // -------------------------------------------------------------------------
    // BFS — shortest path (fewest hops)
    // -------------------------------------------------------------------------

    @Test
    fun findPath_twoRoutes_returnsShortestHopCount() {
        // Long:  A -> B -> C -> D  (3 hops)
        // Short: A -> E -> D       (2 hops)
        val g = graph(
            nodes = listOf(
                node("A", "Hall"), node("B", "Lounge"), node("C", "Study"),
                node("D", "Kitchen"), node("E", "Hallway")
            ),
            edges = listOf(
                edge("A", "B"), edge("B", "C"), edge("C", "D"),
                edge("A", "E"), edge("E", "D")
            )
        )
        val result = GraphService(g).findPath("A", "D")
        assertEquals("Should return the 2-hop path via E", listOf("A", "E", "D"), result)
    }

    @Test
    fun findPath_threeEquidistantRoutes_returnsAValidPath() {
        // A can reach D via B, C, or E — all 2 hops
        val g = graph(
            nodes = listOf(
                node("A", "Entrance"), node("B", "Left"),
                node("C", "Middle"), node("D", "Exit")
            ),
            edges = listOf(
                edge("A", "B"), edge("B", "D"),
                edge("A", "C"), edge("C", "D")
            )
        )
        val result = GraphService(g).findPath("A", "D")
        assertNotNull(result)
        assertEquals("Path should be 3 nodes (2 hops)", 3, result!!.size)
        assertEquals("A", result.first())
        assertEquals("D", result.last())
    }

    // -------------------------------------------------------------------------
    // BFS — graph scale
    // -------------------------------------------------------------------------

    @Test
    fun findPath_linearGraph5Nodes_returnsFullSequence() {
        val ids = listOf("01", "02", "03", "04", "05")
        val nodes = ids.map { node(it, "Room $it") }
        val edges = ids.zipWithNext { a, b -> edge(a, b) }
        val g = graph(nodes, edges)

        val result = GraphService(g).findPath("01", "05")
        assertEquals(ids, result)
    }

    @Test
    fun findPath_linearGraph10Nodes_returnsFullSequence() {
        val ids = (1..10).map { it.toString().padStart(2, '0') }
        val nodes = ids.map { node(it, "Room $it") }
        val edges = ids.zipWithNext { a, b -> edge(a, b) }
        val g = graph(nodes, edges)

        val result = GraphService(g).findPath("01", "10")
        assertEquals(ids, result)
    }

    @Test
    fun findPath_bushyGraph10Nodes_returnsShortestPath() {
        // Spine: 01->02->03->04->05 (long route to 05)
        // Shortcut: 01->06->07->05 (3 hops vs 4 hops)
        // Plus extra hanging nodes to increase graph size
        val allNodes = (1..10).map { node(it.toString().padStart(2, '0'), "Room $it") }
        val edges = listOf(
            edge("01", "02"), edge("02", "03"), edge("03", "04"), edge("04", "05"),
            edge("01", "06"), edge("06", "07"), edge("07", "05"),
            edge("02", "08"), edge("08", "09"), edge("09", "10")
        )
        val g = graph(allNodes, edges)
        val result = GraphService(g).findPath("01", "05")
        assertEquals("Shortcut should be chosen (3 hops)", listOf("01", "06", "07", "05"), result)
    }

    // -------------------------------------------------------------------------
    // buildPathDetails
    // -------------------------------------------------------------------------

    @Test
    fun buildPathDetails_firstStepHasNullDescription() {
        val g = graph(
            nodes = listOf(node("A", "Hall"), node("B", "Kitchen")),
            edges = listOf(edge("A", "B", "Go through the door"))
        )
        val service = GraphService(g)
        val steps = service.buildPathDetails(listOf("A", "B"))
        assertNull("Start node should have null description", steps[0].description)
    }

    @Test
    fun buildPathDetails_subsequentStepHasEdgeDescription() {
        val g = graph(
            nodes = listOf(node("A", "Hall"), node("B", "Kitchen")),
            edges = listOf(edge("A", "B", "Go through the door"))
        )
        val service = GraphService(g)
        val steps = service.buildPathDetails(listOf("A", "B"))
        assertEquals("Go through the door", steps[1].description)
    }

    @Test
    fun buildPathDetails_stepNamesMatchNodes() {
        val g = graph(
            nodes = listOf(node("A", "Hall"), node("B", "Kitchen"), node("C", "Lounge")),
            edges = listOf(edge("A", "B", "Desc AB"), edge("B", "C", "Desc BC"))
        )
        val steps = GraphService(g).buildPathDetails(listOf("A", "B", "C"))
        assertEquals("Hall", steps[0].name)
        assertEquals("Kitchen", steps[1].name)
        assertEquals("Lounge", steps[2].name)
    }

    @Test
    fun buildPathDetails_singleNode_returnsOneStep() {
        val g = graph(
            nodes = listOf(node("A", "Hall")),
            edges = emptyList()
        )
        val steps = GraphService(g).buildPathDetails(listOf("A"))
        assertEquals(1, steps.size)
        assertNull(steps[0].description)
    }

    // -------------------------------------------------------------------------
    // findNodeByName
    // -------------------------------------------------------------------------

    @Test
    fun findNodeByName_exactMatch_returnsNode() {
        val g = graph(
            nodes = listOf(node("A", "Kitchen"), node("B", "Bathroom")),
            edges = emptyList()
        )
        val result = GraphService(g).findNodeByName("Kitchen")
        assertEquals("A", result?.id)
    }

    @Test
    fun findNodeByName_caseInsensitive_returnsNode() {
        val g = graph(
            nodes = listOf(node("A", "Kitchen")),
            edges = emptyList()
        )
        val result = GraphService(g).findNodeByName("KITCHEN")
        assertEquals("A", result?.id)
    }

    @Test
    fun findNodeByName_partialQueryMatch_returnsNode() {
        // Simulates STT returning "go to the kitchen please"
        val g = graph(
            nodes = listOf(node("A", "Kitchen")),
            edges = emptyList()
        )
        val result = GraphService(g).findNodeByName("go to the kitchen please")
        assertEquals("A", result?.id)
    }

    @Test
    fun findNodeByName_aliasMatch_returnsNode() {
        val g = graph(
            nodes = listOf(node("A", "Bathroom", "loo", "toilet", "wc")),
            edges = emptyList()
        )
        val result = GraphService(g).findNodeByName("I need the loo")
        assertEquals("A", result?.id)
    }

    @Test
    fun findNodeByName_noMatch_returnsNull() {
        val g = graph(
            nodes = listOf(node("A", "Kitchen"), node("B", "Bathroom")),
            edges = emptyList()
        )
        val result = GraphService(g).findNodeByName("library")
        assertNull(result)
    }

    @Test
    fun findNodeByName_emptyQuery_returnsNull() {
        val g = graph(
            nodes = listOf(node("A", "Kitchen")),
            edges = emptyList()
        )
        val result = GraphService(g).findNodeByName("   ")
        assertNull(result)
    }


    // -------------------------------------------------------------------------
    // Additional edge cases
    // -------------------------------------------------------------------------

    @Test
    fun findPath_startNodeNotInGraph_returnsNull() {
        // In practice this cannot occur (localisation guarantees a valid node),
        // but documented here to verify safe behaviour if called directly
        val g = graph(
            nodes = listOf(node("A", "Hall"), node("B", "Kitchen")),
            edges = listOf(edge("A", "B", "Walk to kitchen"))
        )
        val result = GraphService(g).findPath("INVALID", "B")
        assertNull("Unknown start node should return null, not throw", result)
    }

    @Test
    fun buildPathDetails_emptyPath_returnsEmptyList() {
        val g = graph(nodes = listOf(node("A", "Hall")), edges = emptyList())
        val result = GraphService(g).buildPathDetails(emptyList())
        assertTrue("Empty path should produce empty step list", result.isEmpty())
    }
}
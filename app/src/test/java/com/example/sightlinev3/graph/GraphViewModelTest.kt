package com.example.sightlinev3.graph

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for GraphViewModel.
 * Place in: app/src/test/java/com/example/sightlinev3/graph/GraphViewModelTest.kt
 *
 * Dependencies required in build.gradle (app):
 *   testImplementation("io.mockk:mockk:1.13.8")
 *   testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GraphViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: GraphViewModel
    private lateinit var repository: GraphRepository

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun node(id: String, name: String, vararg aliases: String) =
        Node(id = id, name = name, type = "room", aliases = aliases.toList())

    private fun edge(from: String, to: String, desc: String = "Walk to $to") =
        Edge(from = from, to = to, description = desc)

    private fun makeGraph(nodes: List<Node>, edges: List<Edge>): Graph {
        val nodeMap = nodes.associateBy { it.id }
        val adjacency = edges.groupBy { it.from }
        return Graph(nodes = nodeMap, adjacency = adjacency)
    }

    /** Standard 3-room graph: Hall -> Lounge -> Kitchen */
    private fun threeNodeGraph() = makeGraph(
        nodes = listOf(node("01", "Hall"), node("02", "Lounge"), node("03", "Kitchen", "loo")),
        edges  = listOf(edge("01", "02", "Walk into the lounge"), edge("02", "03", "Enter the kitchen"))
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        every { repository.loadGraphFromAssets() } returns threeNodeGraph()
        viewModel = GraphViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // startNavigation — validation errors
    // -------------------------------------------------------------------------

    @Test
    fun startNavigation_noCurrentNode_emitsErrorState() {
        // ViewModel starts with _currentNode = null
        viewModel.startNavigation("Kitchen")
        val state = viewModel.routeState.value
        assertTrue("Expected Error state", state is RouteState.Error)
        assertEquals("You haven't localised yet - scan a valid location first",
            (state as RouteState.Error).message)
    }

    @Test
    fun startNavigation_unknownDestination_emitsErrorState() {
        // Localise at Hall first
        viewModel.onQrScanned("01")
        viewModel.startNavigation("library")
        val state = viewModel.routeState.value
        assertTrue("Expected Error state", state is RouteState.Error)
        assertEquals("Can't find that room", (state as RouteState.Error).message)
    }

    @Test
    fun startNavigation_alreadyAtDestination_emitsErrorState() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Hall")
        val state = viewModel.routeState.value
        assertTrue("Expected Error state", state is RouteState.Error)
        assertEquals("You're already there", (state as RouteState.Error).message)
    }

    // -------------------------------------------------------------------------
    // startNavigation — success
    // -------------------------------------------------------------------------

    @Test
    fun startNavigation_validDestination_setsActiveState() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        val state = viewModel.routeState.value
        assertTrue("Expected Active state", state is RouteState.Active)
    }

    @Test
    fun startNavigation_validDestination_activeStateHasCorrectDestination() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        val state = viewModel.routeState.value as RouteState.Active
        assertEquals("03", state.destination.id)
    }

    @Test
    fun startNavigation_validDestination_stepsMatchBfsPath() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        val state = viewModel.routeState.value as RouteState.Active
        // BFS Hall->Lounge->Kitchen = 3 steps
        assertEquals(3, state.steps.size)
        assertEquals("01", state.steps[0].id)
        assertEquals("02", state.steps[1].id)
        assertEquals("03", state.steps[2].id)
    }

    @Test
    fun startNavigation_aliasQuery_resolvesDestination() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("loo") // alias for Kitchen
        val state = viewModel.routeState.value
        assertTrue("Alias should resolve to Kitchen node", state is RouteState.Active)
        assertEquals("03", (state as RouteState.Active).destination.id)
    }

    // -------------------------------------------------------------------------
    // onQrScanned — outside route (localisation)
    // -------------------------------------------------------------------------

    @Test
    fun onQrScanned_outsideRoute_validNode_setsCurrentNode() {
        viewModel.onQrScanned("02")
        assertEquals("02", viewModel.currentNode.value?.id)
    }

    @Test
    fun onQrScanned_outsideRoute_invalidNode_doesNotSetCurrentNode() {
        viewModel.onQrScanned("INVALID")
        assertNull(viewModel.currentNode.value)
    }

    @Test
    fun onQrScanned_outsideRoute_sameNodeTwice_noStateChange() {
        viewModel.onQrScanned("01")
        val nodeAfterFirst = viewModel.currentNode.value
        viewModel.onQrScanned("01") // same scan again
        assertEquals(nodeAfterFirst?.id, viewModel.currentNode.value?.id)
    }

    // -------------------------------------------------------------------------
    // onQrScanned — inside active route (checkpoint gating)
    // -------------------------------------------------------------------------

    @Test
    fun onQrScanned_inRoute_wrongNode_doesNotAdvanceStep() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        val initialIndex = viewModel.currentStepIndex.value

        // Scan node 03 when next expected is 02
        viewModel.onQrScanned("03")
        assertEquals("Step should not advance on wrong scan", initialIndex, viewModel.currentStepIndex.value)
    }

    @Test
    fun onQrScanned_inRoute_correctNextNode_emitsCheckpointEvent() = runTest(testDispatcher) {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")

        val checkpoints = mutableListOf<PathStep>()
        val job = launch { viewModel.checkpointReached.collect { checkpoints.add(it) } }

        viewModel.onQrScanned("02") // correct next step
        advanceUntilIdle()

        job.cancel()
        assertEquals("Checkpoint event should have fired once", 1, checkpoints.size)
        assertEquals("02", checkpoints[0].id)
    }

    @Test
    fun onQrScanned_inRoute_finalNode_setsReachedState() {
        // Single-hop route: Hall -> Lounge. RouteState.Reached is set by advanceStep,
        // not onQrScanned, so we use advanceStep to trigger the final state.
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Lounge")
        viewModel.advanceStep("02")
        val state = viewModel.routeState.value
        assertTrue("Expected Reached state after advancing to final step", state is RouteState.Reached)
        assertEquals("02", (state as RouteState.Reached).destination.id)
    }

    // -------------------------------------------------------------------------
    // advanceStep
    // -------------------------------------------------------------------------

    @Test
    fun advanceStep_incrementsStepIndex() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        assertEquals("Step index should start at 0", 0, viewModel.currentStepIndex.value)
        viewModel.advanceStep("02")
        assertEquals("Step index should be 1 after one advance", 1, viewModel.currentStepIndex.value)
    }

    @Test
    fun advanceStep_updatesCurrentNode() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        viewModel.advanceStep("02")
        assertEquals("02", viewModel.currentNode.value?.id)
    }

    @Test
    fun advanceStep_nonFinalStep_remainsActive() {
        // 3-step route [01, 02, 03]; after one advance index=1, steps.size-1=2 → still Active
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        viewModel.advanceStep("02")
        assertTrue(viewModel.routeState.value is RouteState.Active)
    }

    @Test
    fun advanceStep_finalStep_setsReachedState() {
        // Two advances: 0→1 (non-final), 1→2 (final, index == steps.size-1 → Reached)
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        viewModel.advanceStep("02")
        viewModel.advanceStep("03")
        val state = viewModel.routeState.value
        assertTrue(state is RouteState.Reached)
        assertEquals("03", (state as RouteState.Reached).destination.id)
    }

    // -------------------------------------------------------------------------
    // clearRoute
    // -------------------------------------------------------------------------

    @Test
    fun clearRoute_resetsToIdleAndZeroStepIndex() {
        viewModel.onQrScanned("01")
        viewModel.startNavigation("Kitchen")
        assertTrue(viewModel.routeState.value is RouteState.Active)

        viewModel.clearRoute()
        assertTrue(viewModel.routeState.value is RouteState.Idle)
        assertEquals(0, viewModel.currentStepIndex.value)
    }
}

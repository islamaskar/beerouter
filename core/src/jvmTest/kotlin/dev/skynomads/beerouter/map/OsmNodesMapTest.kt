package dev.skynomads.beerouter.map

import kotlin.test.Test
import kotlin.test.assertFails

class OsmNodesMapTest {

    /**
     * minVisitIdInSubtree is a recursive DFS. A deep linear graph causes
     * StackOverflowError because each node in the chain adds a stack frame.
     */
    @Test
    fun cleanupPeninsulasDoesNotStackOverflowOnDeepLinearGraph() {
        // 10 000 nodes in a chain is enough to exhaust the JVM stack
        val nodeCount = 10_000
        val nodes = (0 until nodeCount).map { i -> OsmNode(i, 0) }

        // Chain: nodes[0] — nodes[1] — nodes[2] — … — nodes[N-1]
        for (i in 0 until nodeCount - 1) {
            nodes[i].addLink(OsmLink(), false, nodes[i + 1])
        }

        // visitID == 1 marks a border node; cleanupPeninsulas starts DFS from it
        nodes[0].visitID = 1

        val map = OsmNodesMap()
        map.cleanupMode = OsmNodesMap.CleanupMode.PENINSULAS
        map.cleanupAndCount(nodes)
    }
}

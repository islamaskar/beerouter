/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.expressions.BExpressionContextNode
import dev.skynomads.beerouter.expressions.BExpressionContextWay

internal class StdModel : OsmPathModel() {
    private val recycledPaths: ArrayDeque<StdPath> = ArrayDeque()

    internal var acceptedPathSnapshots: Int = 0
        private set

    internal fun recordAcceptedPathSnapshotForTest() {
        acceptedPathSnapshots++
    }

    internal fun recordAcceptedPathSnapshot() {
        acceptedPathSnapshots++
    }

    override fun createPrePath(): OsmPrePath? = null

    override fun createPath(): OsmPath = recycledPaths.removeLastOrNull() ?: StdPath()

    override fun recyclePath(path: OsmPath) {
        path.resetForReuse()
        recycledPaths.addLast(path as StdPath)
    }

    override fun init(
        expctxWay: BExpressionContextWay?,
        expctxNode: BExpressionContextNode?,
        keyValues: Map<String, String>
    ) = Unit
}

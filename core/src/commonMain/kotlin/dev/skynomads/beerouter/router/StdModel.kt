/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.expressions.BExpressionContextNode
import dev.skynomads.beerouter.expressions.BExpressionContextWay

internal class StdModel : OsmPathModel() {
    override fun createPrePath(): OsmPrePath? = null

    override fun createPath(): OsmPath = StdPath()

    override fun init(
        expctxWay: BExpressionContextWay?,
        expctxNode: BExpressionContextNode?,
        keyValues: Map<String, String>
    ) = Unit
}

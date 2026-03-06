/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.expressions.BExpressionContextNode
import dev.skynomads.beerouter.expressions.BExpressionContextWay

internal class StdModel : OsmPathModel() {
    override fun createPrePath(): OsmPrePath? {
        return null
    }

    override fun createPath(): OsmPath {
        return StdPath()
    }

    protected var ctxWay: BExpressionContextWay? = null
    protected var ctxNode: BExpressionContextNode? = null


    override fun init(
        expctxWay: BExpressionContextWay?,
        expctxNode: BExpressionContextNode?,
        keyValues: MutableMap<String, String>
    ) {
        ctxWay = expctxWay
        ctxNode = expctxNode
    }
}

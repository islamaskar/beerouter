/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.expressions.BExpressionContextNode
import dev.skynomads.beerouter.expressions.BExpressionContextWay

public abstract class OsmPathModel {
    public abstract fun createPrePath(): OsmPrePath?

    public abstract fun createPath(): OsmPath

    public abstract fun init(
        expctxWay: BExpressionContextWay?,
        expctxNode: BExpressionContextNode?,
        keyValues: MutableMap<String, String>
    )
}

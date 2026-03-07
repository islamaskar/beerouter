/**
 * Simple version of OsmPath just to get angle and priority of first segment
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

internal class KinematicPrePath : OsmPrePath() {
    var angle: Double = 0.0
    var priorityclassifier: Int = 0
    var classifiermask: Int = 0

    override fun initPrePath(origin: OsmPath, rc: RoutingContext) {
        val currentLink = requireNotNull(link)
        val currentTargetNode = requireNotNull(targetNode)
        val description: ByteArray = currentLink.descriptionBitmap ?: run {
            //throw new IllegalArgumentException("null description for: " + link);
            currentTargetNode.descriptionBitmap ?: run {
                byteArrayOf(
                    0,
                    1,
                    0
                )
            }
        }

        // extract the 3 positions of the first section
        val previousPosition = requireNotNull(origin.originPosition)

        val sourceNode = requireNotNull(sourceNode)
        val sourcePosition = sourceNode.position
        val isReverse = currentLink.isReverse(sourceNode)

        // evaluate the way tags
        rc.way.evaluate(rc.inverseDirection xor isReverse, description)

        val transferNode = currentLink.geometry?.let {
            rc.geometryDecoder.decodeGeometry(it, sourceNode, currentTargetNode, isReverse)
        }
        val targetPosition = transferNode?.position ?: currentTargetNode.position

        angle = rc.anglemeter.calcAngle(previousPosition, sourcePosition, targetPosition)
        priorityclassifier = rc.way.priorityClassifier.toInt()
        classifiermask = rc.way.classifierMask.toInt()
    }
}

/**
 * Simple version of OsmPath just to get angle and priority of first segment
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.osm.latitudeE6
import dev.skynomads.beerouter.osm.longitudeE6

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
        val lon0 = origin.originLon
        val lat0 = origin.originLat

        val p1 = requireNotNull(sourceNode)
        val lon1 = p1.position.longitudeE6
        val lat1 = p1.position.latitudeE6

        val isReverse = currentLink.isReverse(sourceNode)

        // evaluate the way tags
        rc.way.evaluate(rc.inverseDirection xor isReverse, description)

        val transferNode = currentLink.geometry?.let {
            rc.geometryDecoder.decodeGeometry(it, p1, currentTargetNode, isReverse)
        }

        val lon2: Int
        val lat2: Int

        if (transferNode == null) {
            lon2 = currentTargetNode.position.longitudeE6
            lat2 = currentTargetNode.position.latitudeE6
        } else {
            lon2 = transferNode.position.longitudeE6
            lat2 = transferNode.position.latitudeE6
        }

        angle = rc.anglemeter.calcAngle(lon0, lat0, lon1, lat1, lon2, lat2)
        priorityclassifier = rc.way.priorityClassifier.toInt()
        classifiermask = rc.way.classifierMask.toInt()
    }
}

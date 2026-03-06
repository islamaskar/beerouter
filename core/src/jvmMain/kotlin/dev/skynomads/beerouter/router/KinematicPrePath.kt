/**
 * Simple version of OsmPath just to get angle and priority of first segment
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude

internal class KinematicPrePath : OsmPrePath() {
    var angle: Double = 0.0
    var priorityclassifier: Int = 0
    var classifiermask: Int = 0

    override fun initPrePath(origin: OsmPath, rc: RoutingContext) {
        var description = link!!.descriptionBitmap
        if (description == null) {
            //throw new IllegalArgumentException("null description for: " + link);
            description =
                (if (targetNode!!.descriptionBitmap != null) targetNode!!.descriptionBitmap else byteArrayOf(
                    0,
                    1,
                    0
                ))
        }

        // extract the 3 positions of the first section
        val lon0 = origin.originLon
        val lat0 = origin.originLat

        val p1 = sourceNode!!
        val lon1 = p1.position.longitude.toIntLongitude()
        val lat1 = p1.position.latitude.toIntLatitude()

        val isReverse = link!!.isReverse(sourceNode)

        // evaluate the way tags
        rc.way.evaluate(rc.inverseDirection xor isReverse, description!!)

        val transferNode = if (link!!.geometry == null)
            null
        else
            rc.geometryDecoder.decodeGeometry(link!!.geometry!!, p1, targetNode!!, isReverse)

        val lon2: Int
        val lat2: Int

        if (transferNode == null) {
            lon2 = targetNode!!.position.longitude.toIntLongitude()
            lat2 = targetNode!!.position.latitude.toIntLatitude()
        } else {
            lon2 = transferNode.ilon
            lat2 = transferNode.ilat
        }

        angle = rc.anglemeter.calcAngle(lon0, lat0, lon1, lat1, lon2, lat2)
        priorityclassifier = rc.way.priorityClassifier.toInt()
        classifiermask = rc.way.classifierMask.toInt()
    }
}

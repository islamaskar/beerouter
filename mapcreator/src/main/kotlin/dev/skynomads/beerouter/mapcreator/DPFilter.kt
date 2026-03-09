package dev.skynomads.beerouter.mapcreator

import dev.skynomads.beerouter.geo.coordinateScaleAt

public object DPFilter {
    private const val DP_SQL_THRESHOLD: Double = 0.4 * 0.4

    public fun doDPFilter(nodes: List<OsmNodeP>) {
        var first = 0
        var last = nodes.size - 1
        while (first < last && (nodes[first + 1].bits.toInt() and OsmNodeP.DP_SURVIVOR_BIT) != 0) {
            first++
        }
        while (first < last && (nodes[last - 1].bits.toInt() and OsmNodeP.DP_SURVIVOR_BIT) != 0) {
            last--
        }
        if (last - first > 1) {
            doDPFilter(nodes, first, last)
        }
    }

    public fun doDPFilter(nodes: List<OsmNodeP>, first: Int, last: Int) {
        var maxSqDist = -1.0
        var index = -1
        val p1 = nodes[first]
        val p2 = nodes[last]
        val scale = coordinateScaleAt((p1.ilat + p2.ilat) shr 1)
        val dlon2m = scale.longitudeToMeters
        val dlat2m = scale.latitudeToMeters
        val dx = (p2.ilon - p1.ilon) * dlon2m
        val dy = (p2.ilat - p1.ilat) * dlat2m
        val d2 = dx * dx + dy * dy
        for (i in first + 1 until last) {
            val p = nodes[i]
            var t = 0.0
            if (d2 != 0.0) {
                t = ((p.ilon - p1.ilon) * dlon2m * dx + (p.ilat - p1.ilat) * dlat2m * dy) / d2
                t = t.coerceIn(0.0, 1.0)
            }
            val dx2 = (p.ilon - (p1.ilon + t * (p2.ilon - p1.ilon))) * dlon2m
            val dy2 = (p.ilat - (p1.ilat + t * (p2.ilat - p1.ilat))) * dlat2m
            val sqDist = dx2 * dx2 + dy2 * dy2
            if (sqDist > maxSqDist) {
                index = i
                maxSqDist = sqDist
            }
        }
        if (index >= 0) {
            if (index - first > 1) {
                doDPFilter(nodes, first, index)
            }
            if (maxSqDist >= DP_SQL_THRESHOLD) {
                nodes[index].bits = (nodes[index].bits.toInt() or OsmNodeP.DP_SURVIVOR_BIT).toByte()
            }
            if (last - index > 1) {
                doDPFilter(nodes, index, last)
            }
        }
    }
}

package app.pedalq.routing

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.geo.UNSET_ELEVATION
import dev.skynomads.beerouter.geo.encodedAltitudeToMeters
import dev.skynomads.beerouter.map.DefaultMapSource
import dev.skynomads.beerouter.router.OsmNodeNamed
import dev.skynomads.beerouter.router.OsmTrack
import dev.skynomads.beerouter.router.RoutingContext
import dev.skynomads.beerouter.router.RoutingEngine
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

/**
 * PedalQ's entry point to Beerouter on iOS: a bike route through the points as BRouter's GeoJSON
 * (the shape the app reads from BRouter on Android and from the routing server): the line with
 * elevation, "track-length", "filtered ascend", and "messages" with each stretch's Distance and
 * WayTags. Blocking; call it off the main thread.
 */
public object BikeRouter {
    /** [coords]: lon, lat, lon, lat, … (2 to 6 points); [alternative]: 0–3. Throws when there is no route. */
    @Throws(Exception::class)
    public fun route(segmentDir: String, profilePath: String, lookupsPath: String, coords: DoubleArray, alternative: Int): String {
        require(coords.size >= 4 && coords.size % 2 == 0) { "2 or more points" }
        val ctx = RoutingContext(profileContent = text(Path(profilePath)), lookupContent = text(Path(lookupsPath)), mapSource = DefaultMapSource(Path(segmentDir)))
        ctx.alternativeIdx = alternative
        val n = coords.size / 2
        val waypoints = (0 until n).map { i ->
            OsmNodeNamed().apply {
                name = if (i == 0) "from" else if (i == n - 1) "to" else "via$i"
                position = Position(coords[2 * i], coords[2 * i + 1])
            }
        }
        val track = runBlocking { RoutingEngine(ctx).doRouting(waypoints) } ?: throw IllegalStateException("no route")
        return geojson(track)
    }

    private fun text(p: Path) = SystemFileSystem.source(p).buffered().use { it.readString() }

    private val HEAD = listOf("Longitude", "Latitude", "Elevation", "Distance", "CostPerKm", "ElevCost", "TurnCost", "NodeCost", "InitialCost", "WayTags", "NodeTags", "Time", "Energy")

    internal fun geojson(track: OsmTrack): String {
        val coords = StringBuilder()
        val rows = mutableListOf<List<String>>(HEAD)
        for ((i, node) in track.nodes.withIndex()) {
            val p = node.position
            val lon = (p.longitude - 180_000_000) / 1e6
            val lat = (p.latitude - 90_000_000) / 1e6
            val ele = if (node.altitude == UNSET_ELEVATION) null else encodedAltitudeToMeters(node.altitude)
            if (i > 0) coords.append(',')
            coords.append('[').append(lon).append(',').append(lat)
            if (ele != null) coords.append(',').append(round1(ele))
            coords.append(']')
            val m = node.message ?: continue
            val tags = m.wayTags?.entries?.joinToString(" ") { "${it.key}=${it.value}" } ?: ""
            val nodeTags = m.nodeTags?.entries?.joinToString(" ") { "${it.key}=${it.value}" } ?: ""
            // one row per stretch of the same way, as BRouter aggregates them: the next node on it adds its distance
            val last = rows.last()
            if (rows.size > 1 && last[9] == tags && nodeTags.isEmpty()) {
                rows[rows.size - 1] = last.toMutableList().also { it[3] = (it[3].toInt() + m.linkdist).toString() }
                continue
            }
            rows += listOf(p.longitude.toString(), p.latitude.toString(), (ele?.toInt() ?: 0).toString(), m.linkdist.toString(), (m.costfactor * 1000).toInt().toString(),
                m.linkelevationcost.toString(), m.linkturncost.toString(), m.linknodecost.toString(), m.linkinitcost.toString(), tags, nodeTags, m.time.toInt().toString(), m.energy.toInt().toString())
        }
        val messages = rows.joinToString(",", "[", "]") { r -> r.joinToString(",", "[", "]") { str(it) } }
        return """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"creator":"Beerouter (PedalQ)","track-length":"${track.distance}","filtered ascend":"${track.ascend}","plain-ascend":"${track.plainAscend}","messages":$messages},"geometry":{"type":"LineString","coordinates":[$coords]}}]}"""
    }

    private fun round1(v: Double) = kotlin.math.round(v * 10) / 10

    private fun str(s: String): String {
        val b = StringBuilder("\"")
        for (c in s) when (c) {
            '"' -> b.append("\\\""); '\\' -> b.append("\\\\"); '\n' -> b.append("\\n"); '\r' -> b.append("\\r"); '\t' -> b.append("\\t")
            else -> if (c < ' ') b.append("\\u").append(c.code.toString(16).padStart(4, '0')) else b.append(c)
        }
        return b.append('"').toString()
    }
}

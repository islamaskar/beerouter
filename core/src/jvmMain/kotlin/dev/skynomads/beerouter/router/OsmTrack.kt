/**
 * Container for a track
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import androidx.collection.MutableLongObjectMap
import dev.skynomads.beerouter.mapaccess.MatchedWaypoint
import dev.skynomads.beerouter.mapaccess.MatchedWaypoint.Companion.readFromStream
import dev.skynomads.beerouter.mapaccess.OsmPos
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import org.maplibre.spatialk.geojson.Position
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.io.EOFException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlin.math.abs

public class OsmTrack {
    public var endPoint: MatchedWaypoint? = null
    public lateinit var nogoChecksums: LongArray
    public var profileTimestamp: Long = 0
    public var isDirty: Boolean = false

    public var showspeed: Boolean = false
    public var showSpeedProfile: Boolean = false
    public var showTime: Boolean = false

    public var params: MutableMap<String, String>? = null

    public var pois: MutableList<OsmNodeNamed> = mutableListOf()

    public class OsmPathElementHolder {
        public var node: OsmPathElement? = null
        public var nextHolder: OsmPathElementHolder? = null
    }

    public var nodes: MutableList<OsmPathElement> = mutableListOf()

    private var nodesMap: MutableLongObjectMap<OsmPathElementHolder> = MutableLongObjectMap()

    private var detourMap: MutableLongObjectMap<OsmPathElementHolder> = MutableLongObjectMap()

    public var voiceHints: VoiceHintList = VoiceHintList()

    public var name: String = "unset"

    public var matchedWaypoints: MutableList<MatchedWaypoint> = mutableListOf()
    public var exportWaypoints: Boolean = false
    public var exportCorrectedWaypoints: Boolean = false

    public fun addNode(node: OsmPathElement) {
        nodes.add(0, node)
    }

    public fun registerDetourForId(id: Long, detour: OsmPathElement?) {
        val nh = OsmPathElementHolder()
        nh.node = detour
        var h = detourMap[id]
        if (h != null) {
            while (h!!.nextHolder != null) {
                h = h.nextHolder
            }
            h.nextHolder = nh
        } else {
            detourMap.put(id, nh)
        }
    }

    public fun replaceDetours(source: OsmTrack) {
        val newMap = MutableLongObjectMap<OsmPathElementHolder>()
        source.detourMap.forEach { key, value ->
            newMap[key] = value
        }
        detourMap = newMap
    }

    public fun addDetours(source: OsmTrack) {
        source.detourMap.forEach { id, value ->
            if (!detourMap.contains(id) && source.nodesMap.contains(id)) {
                detourMap.put(id, value)
            }
        }
    }

    public var lastorigin: OsmPathElement? = null

    public fun buildMap() {
        for (node in nodes) {
            val id = node.idFromPos
            val nh = OsmPathElementHolder()
            nh.node = node
            var h = nodesMap[id]
            if (h != null) {
                while (h!!.nextHolder != null) {
                    h = h.nextHolder
                }
                h.nextHolder = nh
            } else {
                nodesMap.put(id, nh)
            }
        }
    }

    /**
     * writes the track in binary-format to a file
     *
     * @param filename the filename to write to
     */
    public fun writeBinary(filename: String) {
        File(filename).outputStream().asSink().buffered().use { sink ->
            writeBinary(sink)
        }
    }

    public fun writeBinary(stream: OutputStream) {
        stream.asSink().buffered().use { sink ->
            writeBinary(sink)
        }
    }

    public fun writeBinary(sink: Sink) {
        endPoint!!.writeToStream(sink)
        sink.writeInt(nodes.size)
        for (node in nodes) {
            node.writeToStream(sink)
        }
        sink.writeLong(nogoChecksums[0])
        sink.writeLong(nogoChecksums[1])
        sink.writeLong(nogoChecksums[2])
        sink.writeByte(if (isDirty) 1 else 0)
        sink.writeLong(profileTimestamp)
    }

    public fun addNodes(t: OsmTrack) {
        for (n in t.nodes) addNode(n)
        buildMap()
    }

    public fun containsNode(node: OsmPos): Boolean {
        return nodesMap.contains(node.idFromPos)
    }

    public fun getLink(n1: Long, n2: Long): OsmPathElement? {
        var h = nodesMap[n2]
        while (h != null) {
            val e1 = h.node!!.origin
            if (e1 != null && e1.idFromPos == n1) {
                return h.node
            }
            h = h.nextHolder
        }
        return null
    }

    public fun appendTrack(t: OsmTrack) {
        var i: Int

        val ourSize = nodes.size
        if (ourSize > 0 && t.nodes.size > 1) {
            val olde = nodes[ourSize - 1]
            t.nodes[1].origin = olde
        }
        val t0 = if (ourSize > 0) nodes[ourSize - 1].time else 0f
        val e0 = if (ourSize > 0) nodes[ourSize - 1].energy else 0f
        val c0 = if (ourSize > 0) nodes[ourSize - 1].cost else 0
        i = 0
        while (i < t.nodes.size) {
            val e = t.nodes[i]
            if (i == 0 && ourSize > 0 && toSElev(nodes[ourSize - 1].position.altitude) == Short.MIN_VALUE) {
                nodes[ourSize - 1].position = Position(
                    nodes[ourSize - 1].position.longitude,
                    nodes[ourSize - 1].position.latitude,
                    toSElev(e.position.altitude).toDouble() / 4.0
                )
            }
            if (i > 0 || ourSize == 0) {
                e.time += t0
                e.energy += e0
                e.cost += c0
                if (e.message != null) {
                    val lon = e.position.longitude.toIntLongitude()
                    val lat = e.position.latitude.toIntLatitude()
                    if (!(e.message!!.lon == lon && e.message!!.lat == lat)) {
                        e.message!!.lon = lon
                        e.message!!.lat = lat
                    }
                }
                nodes.add(e)
            }
            i++
        }

        if (t.voiceHints.isNotEmpty()) {
            if (ourSize > 0) {
                for (hint in t.voiceHints) {
                    hint.indexInTrack = hint.indexInTrack + ourSize - 1
                }
            }
            voiceHints.addAll(t.voiceHints)
        } else {
            addDetours(t)
        }

        distance += t.distance
        ascend += t.ascend
        plainAscend += t.plainAscend
        cost += t.cost
        energy = nodes[nodes.size - 1].energy.toInt()

        showspeed = showspeed or t.showspeed
        showSpeedProfile = showSpeedProfile or t.showSpeedProfile
    }

    public var distance: Int = 0
    public var ascend: Int = 0
    public var plainAscend: Int = 0
    public var cost: Int = 0
    public var energy: Int = 0
    public var iternity: MutableList<String?>? = null

    public fun getVoiceHint(i: Int): VoiceHint? {
        for (hint in voiceHints) {
            if (hint.indexInTrack == i) {
                return hint
            }
        }
        return null
    }

    public fun getMatchedWaypoint(idx: Int): MatchedWaypoint? {
        for (wp in matchedWaypoints) {
            if (idx == wp.indexInTrack) {
                return wp
            }
        }
        return null
    }

    private fun getVNode(i: Int): Int {
        val m1 = if (i + 1 < nodes.size) nodes[i + 1].message else null
        val m0 = if (i < nodes.size) nodes[i].message else null
        val vnode0 = m1?.vnode0 ?: 999
        val vnode1 = m0?.vnode1 ?: 999
        return if (vnode0 < vnode1) vnode0 else vnode1
    }

    public val totalSeconds: Int
        get() {
            val s =
                if (nodes.size < 2) 0f else nodes[nodes.size - 1].time - nodes[0].time
            return (s + 0.5).toInt()
        }

    public fun equalsTrack(t: OsmTrack): Boolean {
        if (nodes.size != t.nodes.size) return false
        for (i in nodes.indices) {
            val e1 = nodes[i]
            val e2 = t.nodes[i]
            if (e1.position.longitude.toIntLongitude() != e2.position.longitude.toIntLongitude() ||
                e1.position.latitude.toIntLatitude() != e2.position.latitude.toIntLatitude()) return false
        }
        return true
    }

    public fun getFromDetourMap(id: Long): OsmPathElementHolder? {
        return detourMap[id]
    }

    public fun prepareSpeedProfile(rc: RoutingContext?) {
        // sendSpeedProfile = rc.keyValues != null && rc.keyValues.containsKey( "vmax" );
    }

    public fun processVoiceHints(rc: RoutingContext) {
        voiceHints = VoiceHintList()
        voiceHints.setTransportMode(rc.global.carMode, rc.global.bikeMode)
        voiceHints.turnInstructionMode = rc.global.turnInstructionMode

        if (detourMap.isEmpty() && !rc.global.hasDirectRouting) {
            // only when no direct way points
            return
        }
        var nodeNr = nodes.size - 1
        var node = nodes.getOrNull(nodeNr)
        while (node != null) {
            node = node.origin
        }

        node = nodes[nodeNr]
        val inputs = mutableListOf<VoiceHint>()
        while (node != null) {
            val origin = node.origin
            if (origin != null) {
                if (nodeNr == nodes.size - 1) {
                    val input = VoiceHint()
                    inputs.add(0, input)
                    input.ilat = node.position.latitude.toIntLatitude()
                    input.ilon = node.position.longitude.toIntLongitude()
                    input.selev = toSElev(node.position.altitude)
                    input.goodWay = node.message
                    input.oldWay = node.message
                    input.indexInTrack = nodes.size - 1
                    input.command = VoiceHint.Command.END
                }
                val input = VoiceHint()
                inputs.add(input)
                input.ilat = origin.position.latitude.toIntLatitude()
                input.ilon = origin.position.longitude.toIntLongitude()
                input.selev = toSElev(origin.position.altitude)
                input.indexInTrack = --nodeNr
                input.goodWay = node.message
                input.oldWay = if (origin.message == null) node.message else origin.message

                if (rc.global.turnInstructionMode == 8 || rc.global.turnInstructionMode == 4 || rc.global.turnInstructionMode == 2 || rc.global.turnInstructionMode == 9) {
                    val mwpt = getMatchedWaypoint(nodeNr)
                    if (mwpt != null && mwpt.type == MatchedWaypoint.Type.DIRECT) {
                        input.command = VoiceHint.Command.BL
                        input.angle =
                            (if (nodeNr == 0) origin.message!!.turnangle else node.message!!.turnangle)
                        input.distanceToNext = node.calcDistance(origin).toDouble()
                    }
                }
                run {
                    val detours = detourMap[origin.idFromPos]
                    if (nodeNr >= 0 && detours != null) {
                        var h: OsmPathElementHolder? = detours
                        while (h != null) {
                            val e = h.node
                            input.addBadWay(startSection(e, origin))
                            h = h.nextHolder
                        }
                    }
                }
                /* else if (nodeNr == 0 && detours != null) {
          OsmPathElementHolder h = detours;
          OsmPathElement e = h.node;
          input.addBadWay(startSection(e, e));
        } */
            }
            node = node.origin
        }

        val transportMode = voiceHints.transportMode
        val vproc = VoiceHintProcessor(
            rc.global.turnInstructionCatchingRange,
            rc.global.turnInstructionRoundabouts,
            transportMode
        )
        val results = vproc.process(inputs)

        val minDistance = this.minDistance.toDouble()
        val resultsLast =
            vproc.postProcess(results, rc.global.turnInstructionCatchingRange, minDistance)
        for (hint in resultsLast) {
            voiceHints.list.add(hint)
        }
    }

    public val minDistance: Int
        get() {
            return when (voiceHints.transportMode) {
                VoiceHintList.TransportMode.CAR -> 20
                VoiceHintList.TransportMode.FOOT -> 3
                VoiceHintList.TransportMode.BIKE -> 5
            }
        }

    public fun getVoiceHintTime(i: Int): Float {
        if (voiceHints.list.isNotEmpty() && i < voiceHints.list.size) {
            return voiceHints.list[i].time
        }
        if (nodes.isEmpty()) {
            return 0f
        }
        return nodes[nodes.size - 1].time
    }

    public fun removeVoiceHint(i: Int): Boolean {
        for (vh in voiceHints) {
            if (vh.indexInTrack == i) {
                return voiceHints.remove(vh)
            }
        }
        return false
    }

    private fun startSection(element: OsmPathElement?, root: OsmPathElement): MessageData? {
        var e = element
        var cnt = 0
        val rootId = root.idFromPos
        while (e != null && e.origin != null) {
            if (e.origin!!.idFromPos == rootId) {
                return e.message
            }
            e = e.origin
            require(cnt++ != 1000000) { "ups: $root->$element" }
        }
        return null
    }

    public companion object {
        public fun readBinary(
            filename: String?,
            newEp: OsmNodeNamed,
            nogoChecksums: LongArray,
            profileChecksum: Long,
            debugInfo: StringBuilder?
        ): OsmTrack? {
            var t: OsmTrack? = null
            if (filename != null) {
                val f = File(filename)
                if (f.exists()) {
                    try {
                        f.inputStream().use { stream ->
                            t = readBinary(stream, newEp, nogoChecksums, profileChecksum, debugInfo)
                        }
                    } catch (e: Exception) {
                        debugInfo?.append("Error reading rawTrack: $e")
                    }
                }
            }
            return t
        }

        public fun readBinary(
            stream: InputStream,
            newEp: OsmNodeNamed,
            nogoChecksums: LongArray,
            profileChecksum: Long,
            debugInfo: StringBuilder?
        ): OsmTrack? {
            stream.asSource().buffered().use { source ->
                return readBinary(source, newEp, nogoChecksums, profileChecksum, debugInfo)
            }
        }

        public fun readBinary(
            source: Source,
            newEp: OsmNodeNamed,
            nogoChecksums: LongArray,
            profileChecksum: Long,
            debugInfo: StringBuilder?
        ): OsmTrack? {
            val ep = readFromStream(source)
            val dlon: Int = ep.waypoint!!.position.longitude.toIntLongitude() - newEp.position.longitude.toIntLongitude()
            val dlat = ep.waypoint!!.position.latitude.toIntLatitude() - newEp.position.latitude.toIntLatitude()
            val targetMatch = dlon < 20 && dlon > -20 && dlat < 20 && dlat > -20
            debugInfo?.append("target-delta = $dlon/$dlat targetMatch=$targetMatch")
            if (!targetMatch) return null

            val track = OsmTrack()
            track.endPoint = ep
            val n = source.readInt()
            var lastPe: OsmPathElement? = null
            for (i in 0..<n) {
                val pe: OsmPathElement = OsmPathElement.readFromStream(source)
                pe.origin = lastPe
                lastPe = pe
                track.nodes.add(pe)
            }
            track.cost = lastPe!!.cost
            track.buildMap()

            val al = LongArray(3)
            var pchecksum: Long = 0
            try {
                al[0] = source.readLong()
                al[1] = source.readLong()
                al[2] = source.readLong()
            } catch (_: EOFException) { /* kind of expected */
            }
            try {
                track.isDirty = source.readByte().toInt() != 0
            } catch (_: EOFException) { /* kind of expected */
            }
            try {
                pchecksum = source.readLong()
            } catch (_: EOFException) { /* kind of expected */
            }
            val nogoCheckOk =
                abs(al[0] - nogoChecksums[0]) <= 20 &&
                        abs(al[1] - nogoChecksums[1]) <= 20 &&
                        abs(al[2] - nogoChecksums[2]) <= 20
            val profileCheckOk = pchecksum == profileChecksum

            if (debugInfo != null) {
                debugInfo.append(" nogoCheckOk=$nogoCheckOk profileCheckOk=$profileCheckOk")
                debugInfo.append(" al=${formatLongs(al)} nogoChecksums=${formatLongs(nogoChecksums)}")
            }
            return if (nogoCheckOk && profileCheckOk) track else null
        }

        private fun formatLongs(al: LongArray): String {
            val sb = StringBuilder()
            sb.append('{')
            for (l in al) {
                sb.append(l)
                sb.append(' ')
            }
            sb.append('}')
            return sb.toString()
        }

        private fun toSElev(altitude: Double?): Short =
            altitude?.times(4.0)?.toInt()?.toShort() ?: Short.MIN_VALUE
    }
}

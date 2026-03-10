package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.codec.NoisyDiffCoder
import dev.skynomads.beerouter.codec.StatCoderContext
import dev.skynomads.beerouter.codec.TagValueCoder
import dev.skynomads.beerouter.codec.TagValueValidator
import dev.skynomads.beerouter.codec.WaypointMatcher
import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.util.ByteDataWriter

/**
 * DirectWeaver does the same decoding as MicroCache2, but decodes directly
 * into the instance-graph, not into the intermediate nodes-cache
 */
public class DirectWeaver(
    bc: StatCoderContext,
    dataBuffers: DataBuffers,
    lonIdx: Int,
    latIdx: Int,
    divisor: Int,
    wayValidator: TagValueValidator,
    waypointMatcher: WaypointMatcher?,
    hollowNodes: OsmNodesMap
) : ByteDataWriter() {
    private val id64Base: Long

    init {
        val cellsize = 1000000 / divisor
        id64Base = Position(lonIdx * cellsize, latIdx * cellsize).id

        val wayTagCoder = TagValueCoder(bc, dataBuffers, wayValidator)
        val nodeTagCoder = TagValueCoder(bc, dataBuffers, null)
        val nodeIdxDiff = NoisyDiffCoder(bc)
        val nodeEleDiff = NoisyDiffCoder(bc)
        val extLonDiff = NoisyDiffCoder(bc)
        val extLatDiff = NoisyDiffCoder(bc)
        val transEleDiff = NoisyDiffCoder(bc)

        val size = bc.decodeNoisyNumber(5)

        val faid = if (size > dataBuffers.ibuf2.size) IntArray(size) else dataBuffers.ibuf2

        bc.decodeSortedArray(faid, 0, size, 29, 0)

        val nodes = (0 until size).map { n ->
            val id = expandId(faid[n])
            var node = hollowNodes.get(id)
            if (node == null) {
                node = OsmNode(id)
            } else {
                node.visitID = 1
                hollowNodes.remove(node)
            }
            node
        }

        val netdatasize = bc.decodeNoisyNumber(10) // (not needed for direct weaving)
        ab = dataBuffers.bbuf1
        aboffset = 0

        var selev = 0
        for (n in 0..<size) { // loop over nodes
            val node = nodes[n]
            val nodePosition = node.position
            
            

            // future escapes (turn restrictions?)
            var trExceptions: Short = 0
            while (true) {
                val featureId = bc.decodeVarBits()
                if (featureId == 0) break
                val bitsize = bc.decodeNoisyNumber(5)

                when (featureId) {
                    2 -> { // exceptions to turn-restriction
                        trExceptions = bc.decodeBounded(1023).toShort()
                    }

                    1 -> { // turn-restriction
                        val exceptions = trExceptions
                        trExceptions = 0
                        val isPositive = bc.decodeBit()
                        val fromId = Position(nodePosition.longitude + bc.decodeNoisyDiff(10), nodePosition.latitude + bc.decodeNoisyDiff(10)).id
                        val toId = Position(nodePosition.longitude + bc.decodeNoisyDiff(10), nodePosition.latitude + bc.decodeNoisyDiff(10)).id
                        node.addTurnRestriction(
                            TurnRestriction(
                                isPositive = isPositive,
                                exceptions = exceptions,
                                fromId = fromId,
                                toId = toId
                            )
                        )
                    }

                    else -> {
                        for (i in 0..<bitsize) bc.decodeBit() // unknown feature, just skip
                    }
                }
            }

            selev += nodeEleDiff.decodeSignedValue()
            node.clearHollow()
            node.position = node.position.copy(altitude = selev.toShort().takeUnless { it == Short.MIN_VALUE })
            val nodeTags = nodeTagCoder.decodeTagValueSet()
            node.nodeDescription = nodeTags?.data // TODO: unified?

            val links = bc.decodeNoisyNumber(1)
            for (li in 0..<links) {
                val nodeIdx = n + nodeIdxDiff.decodeSignedValue()

                var dlonRemaining: Int
                var dlatRemaining: Int

                var isReverse = false
                if (nodeIdx != n) { // internal (forward-) link
                    val targetPosition = nodes[nodeIdx].position
                    dlonRemaining = targetPosition.longitude - nodePosition.longitude
                    dlatRemaining = targetPosition.latitude - nodePosition.latitude
                } else {
                    isReverse = bc.decodeBit()
                    dlonRemaining = extLonDiff.decodeSignedValue()
                    dlatRemaining = extLatDiff.decodeSignedValue()
                }

                val wayTags = wayTagCoder.decodeTagValueSet()

                val linklon = nodePosition.longitude + dlonRemaining
                val linklat = nodePosition.latitude + dlatRemaining
                aboffset = 0
                if (!isReverse) { // write geometry for forward links only
                    var matcher =
                        if (wayTags == null || wayTags.accessType < 2) null else waypointMatcher
                    val ilontarget = nodePosition.longitude + dlonRemaining
                    val ilattarget = nodePosition.latitude + dlatRemaining
                    if (matcher != null) {
                        val useAsStartWay =
                            wayTags == null || wayValidator.checkStartWay(wayTags.data)
                        if (!matcher.start(Position(nodePosition.longitude, nodePosition.latitude), Position(ilontarget, ilattarget), useAsStartWay)) {
                            matcher = null
                        }
                    }

                    val transcount = bc.decodeVarBits()
                    var count = transcount + 1
                    for (i in 0..<transcount) {
                        val dlon = bc.decodePredictedValue(dlonRemaining / count)
                        val dlat = bc.decodePredictedValue(dlatRemaining / count)
                        dlonRemaining -= dlon
                        dlatRemaining -= dlat
                        count--
                        val elediff = transEleDiff.decodeSignedValue()
                        if (wayTags != null) {
                            writeVarLengthSigned(dlon)
                            writeVarLengthSigned(dlat)
                            writeVarLengthSigned(elediff)
                        }

                        matcher?.transferNode(Position(ilontarget - dlonRemaining, ilattarget - dlatRemaining))
                    }
                    matcher?.end()
                }

                if (wayTags != null) {
                    var geometry: ByteArray? = null
                    if (aboffset > 0) {
                        geometry = ByteArray(aboffset)
                        ab.copyInto(geometry, 0, 0, aboffset)
                    }

                    if (nodeIdx != n) { // valid internal (forward-) link
                        val node2 = nodes[nodeIdx]
                        var link: OsmLink? =
                            if (node.isLinkUnused) node else (if (node2.isLinkUnused) node2 else null)
                        if (link == null) {
                            link = OsmLink()
                        }
                        link.descriptionBitmap = wayTags.data
                        link.geometry = geometry
                        node.addLink(link, isReverse, node2)
                    } else { // weave external link
                        node.addLink(
                            Position(linklon, linklat),
                            wayTags.data,
                            geometry,
                            hollowNodes,
                            isReverse
                        )
                        node.visitID = 1
                    }
                }
            } // ... loop over links
        } // ... loop over nodes


        hollowNodes.cleanupAndCount(nodes)
    }

    public fun expandId(id32: Int): Long {
        return id64Base + id32_00[id32 and 1023] + id32_10[(id32 shr 10) and 1023] + id32_20[(id32 shr 20) and 1023]
    }

    public companion object {
        private val id32_00 = LongArray(1024)
        private val id32_10 = LongArray(1024)
        private val id32_20 = LongArray(1024)

        init {
            for (i in 0..1023) {
                id32_00[i] = _expandId(i)
                id32_10[i] = _expandId(i shl 10)
                id32_20[i] = _expandId(i shl 20)
            }
        }

        private fun _expandId(id32: Int): Long {
            var id32 = id32
            var dlon = 0
            var dlat = 0

            var bm = 1
            while (bm < 0x8000) {
                if ((id32 and 1) != 0) dlon = dlon or bm
                if ((id32 and 2) != 0) dlat = dlat or bm
                id32 = id32 shr 2
                bm = bm shl 1
            }
            return Position.computeId(dlon, dlat)
        }
    }
}

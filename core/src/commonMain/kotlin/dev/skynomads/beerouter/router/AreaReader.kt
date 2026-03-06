package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.expressions.BExpressionContextWay
import dev.skynomads.beerouter.mapaccess.MatchedWaypoint
import dev.skynomads.beerouter.mapaccess.MatchedWaypoint.Companion.readFromStream
import dev.skynomads.beerouter.mapaccess.NodesCache
import dev.skynomads.beerouter.mapaccess.OsmFile
import dev.skynomads.beerouter.mapaccess.OsmLink
import dev.skynomads.beerouter.mapaccess.OsmNode
import dev.skynomads.beerouter.mapaccess.OsmNodesMap
import dev.skynomads.beerouter.mapaccess.PhysicalFile
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.math.abs
import kotlin.math.roundToInt

public class AreaReader {
    public fun getDirectAllData(
        rc: RoutingContext,
        wp: OsmNodeNamed,
        maxscale: Int,
        expctxWay: BExpressionContextWay,
        searchRect: OsmNogoPolygon,
        ais: MutableList<AreaInfo>
    ) {
        val div = 32
        val cellsize = 1000000 / div
        val scale = maxscale
        var count = 0
        var used = 0
        val checkBorder = maxscale > 7

        val tiles = mutableMapOf<Long, String>()
        for (idxLat in -scale..scale) {
            for (idxLon in -scale..scale) {
                if (ignoreCenter(maxscale, idxLon, idxLat)) continue
                val tmplon: Int = wp.position.longitude.toIntLongitude() + cellsize * idxLon
                val tmplat = wp.position.latitude.toIntLatitude() + cellsize * idxLat
                val lonDegree = tmplon / 1000000
                val latDegree = tmplat / 1000000
                val lonMod5 = lonDegree % 5
                val latMod5 = latDegree % 5

                var lon = lonDegree - 180 - lonMod5
                val slon = if (lon < 0) "W" + (-lon) else "E$lon"
                var lat = latDegree - 90 - latMod5
                val slat = if (lat < 0) "S" + (-lat) else "N$lat"
                val filenameBase = slon + "_" + slat

                val lonIdx = tmplon / cellsize
                val latIdx = tmplat / cellsize
                val subIdx = (latIdx - div * latDegree) * div + (lonIdx - div * lonDegree)

                val subLonIdx = (lonIdx - div * lonDegree)
                val subLatIdx = (latIdx - div * latDegree)

                val dataRect = OsmNogoPolygon(true)
                lon = lonDegree * 1000000
                lat = latDegree * 1000000
                var tmplon2 = lon + cellsize * (subLonIdx)
                var tmplat2 = lat + cellsize * (subLatIdx)
                dataRect.addVertex(tmplon2, tmplat2)

                tmplon2 = lon + cellsize * (subLonIdx + 1)
                tmplat2 = lat + cellsize * (subLatIdx)
                dataRect.addVertex(tmplon2, tmplat2)

                tmplon2 = lon + cellsize * (subLonIdx + 1)
                tmplat2 = lat + cellsize * (subLatIdx + 1)
                dataRect.addVertex(tmplon2, tmplat2)

                tmplon2 = lon + cellsize * (subLonIdx)
                tmplat2 = lat + cellsize * (subLatIdx + 1)
                dataRect.addVertex(tmplon2, tmplat2)

                var intersects = checkBorder && dataRect.intersects(
                    searchRect.points[0].x,
                    searchRect.points[0].y,
                    searchRect.points[2].x,
                    searchRect.points[2].y
                )
                if (!intersects && checkBorder) intersects = dataRect.intersects(
                    searchRect.points[1].x,
                    searchRect.points[1].y,
                    searchRect.points[2].x,
                    searchRect.points[3].y
                )
                if (intersects) {
                    continue
                }

                intersects = searchRect.intersects(
                    dataRect.points[0].x,
                    dataRect.points[0].y,
                    dataRect.points[2].x,
                    dataRect.points[2].y
                )
                if (!intersects) intersects = searchRect.intersects(
                    dataRect.points[1].x,
                    dataRect.points[1].y,
                    dataRect.points[3].x,
                    dataRect.points[3].y
                )
                if (!intersects) intersects = containsRect(
                    searchRect,
                    dataRect.points[0].x,
                    dataRect.points[0].y,
                    dataRect.points[2].x,
                    dataRect.points[2].y
                )

                if (!intersects) {
                    continue
                }

                tiles[(tmplon.toLong()) shl 32 or tmplat.toLong()] = filenameBase
                count++
            }
        }

        val list = tiles.entries.sortedBy { it.value }

        val maxmem = rc.memoryclass * 1024L * 1024L // in MB
        val nodesCache =
            NodesCache(
                rc.mapAccessFileSystem,
                expctxWay,
                rc.global.forceSecondaryData,
                maxmem,
                null,
                false
            )
        var currentPhysicalFile: PhysicalFile? = null
        var currentDataBuffers: DataBuffers? = null
        var lastFilenameBase = ""
        try {
            for (entry in list) {
                val n = OsmNode(entry.key)
                val filenameBase: String = entry.value
                if (filenameBase != lastFilenameBase) {
                    currentPhysicalFile?.close()
                    lastFilenameBase = filenameBase
                    val fileName = "$filenameBase.rd5"
                    currentDataBuffers = DataBuffers()
                    currentPhysicalFile = PhysicalFile(
                        fileName,
                        rc.mapAccessFileSystem,
                        currentDataBuffers,
                        -1,
                        -1
                    )
                }
                val physicalFile = requireNotNull(currentPhysicalFile)
                val dataBuffers = requireNotNull(currentDataBuffers)
                if (getDirectData(
                        physicalFile,
                        dataBuffers,
                        n.position.longitude.toIntLongitude(),
                        n.position.latitude.toIntLatitude(),
                        rc,
                        expctxWay,
                        ais
                    )
                ) {
                    used++
                }
            }
        } catch (e: Exception) {
            println("AreaReader error after used=$used / count=$count: ${e.message}")
            ais.clear()
        } finally {
            runCatching { currentPhysicalFile?.close() }
            nodesCache.close()
        }
    }

    public fun getDirectData(
        pf: PhysicalFile,
        dataBuffers: DataBuffers,
        inlon: Int,
        inlat: Int,
        rc: RoutingContext?,
        expctxWay: BExpressionContextWay,
        ais: MutableList<AreaInfo>
    ): Boolean {
        val lonDegree = inlon / 1000000
        val latDegree = inlat / 1000000

        val nodesMap = OsmNodesMap()

        try {
            val div = pf.divisor

            val osmf = OsmFile(pf, lonDegree, latDegree, dataBuffers)
            if (osmf.hasData()) {
                val cellsize = 1000000 / div
                val tmplon = inlon
                val tmplat = inlat
                val lonIdx = tmplon / cellsize
                val latIdx = tmplat / cellsize

                val segment =
                    osmf.createMicroCacheForCell(lonIdx, latIdx, dataBuffers, expctxWay, null, null)

                val size = segment.size
                for (i in 0..<size) {
                    val id = segment.getIdForIndex(i)
                    val node = OsmNode(id)
                    if (segment.getAndClear(id)) {
                        node.parseNodeBody(segment, nodesMap, expctxWay)
                        if (node.firstlink is OsmLink) {
                            var link = node.firstlink
                            while (link != null) {
                                val nextNode = link.getTarget(node)
                                if (nextNode!!.firstlink == null) {
                                    link = link.getNext(node)
                                    continue  // don't care about dead ends
                                }
                                if (nextNode.firstlink!!.descriptionBitmap == null) {
                                    link = link.getNext(node)
                                    continue
                                }

                                for (ai in ais) {
                                    if (ai.polygon!!.isWithin(
                                            node.position.longitude.toIntLongitude().toLong(),
                                            node.position.latitude.toIntLatitude().toLong()
                                        )
                                    ) {
                                        ai.checkAreaInfo(
                                            expctxWay,
                                            node.position.altitude ?: 0.0,
                                            nextNode.firstlink!!.descriptionBitmap!!
                                        )
                                        break
                                    }
                                }
                                break
                                link = link.getNext(node)
                            }
                        }
                    }
                }
                return true
            }
        } catch (e: Exception) {
            println("AreaReader: ${e.message}")
        }
        return false
    }

    public fun ignoreCenter(maxscale: Int, idxLon: Int, idxLat: Int): Boolean {
        val centerScale = (maxscale * .2).roundToInt() - 1
        if (centerScale < 0) return false
        return idxLon >= -centerScale && idxLon <= centerScale && idxLat >= -centerScale && idxLat <= centerScale
    }

    /*
    in this case the polygon is 'only' a rectangle
  */
    public fun containsRect(searchRect: OsmNogoPolygon, p1x: Int, p1y: Int, p2x: Int, p2y: Int): Boolean {
        return searchRect.isWithin(p1x.toLong(), p1y.toLong()) &&
                searchRect.isWithin(p2x.toLong(), p2y.toLong())
    }

    public fun writeAreaInfo(filename: String, wp: MatchedWaypoint, ais: MutableList<AreaInfo>) {
        writeAreaInfo(Path(filename), wp, ais)
    }

    public fun writeAreaInfo(path: Path, wp: MatchedWaypoint, ais: MutableList<AreaInfo>) {
        SystemFileSystem.sink(path).buffered().use { sink -> writeAreaInfo(sink, wp, ais) }
    }

    public fun writeAreaInfo(sink: Sink, wp: MatchedWaypoint, ais: MutableList<AreaInfo>) {
        wp.writeToStream(sink)
        for (ai in ais) {
            sink.writeInt(ai.direction)
            sink.writeLong(ai.elevStart.toBits())
            sink.writeInt(ai.ways)
            sink.writeInt(ai.greenWays)
            sink.writeInt(ai.riverWays)
            sink.writeInt(ai.elev50)
        }
    }

    public fun readAreaInfo(path: Path, wp: MatchedWaypoint, ais: MutableList<AreaInfo>) {
        try {
            SystemFileSystem.source(path).buffered().use { source -> readAreaInfo(source, wp, ais) }
        } catch (e: Exception) {
            ais.clear()
        }
    }

    public fun readAreaInfo(source: Source, wp: MatchedWaypoint, ais: MutableList<AreaInfo>) {
        try {
            val ep = readFromStream(source)
            if (abs(ep.waypoint!!.position.longitude.toIntLongitude() - wp.waypoint!!.position.longitude.toIntLongitude()) > 500 &&
                abs(ep.waypoint!!.position.latitude.toIntLatitude() - wp.waypoint!!.position.latitude.toIntLatitude()) > 500
            ) {
                return
            }
            if (abs(ep.radius - wp.radius) > 500) {
                return
            }
            for (i in 0..3) {
                val direction = source.readInt()
                val ai = AreaInfo(direction)
                ai.elevStart = Double.fromBits(source.readLong())
                ai.ways = source.readInt()
                ai.greenWays = source.readInt()
                ai.riverWays = source.readInt()
                ai.elev50 = source.readInt()
                ais.add(ai)
            }
        } catch (_: Exception) {
            ais.clear()
        }
    }
}

/**
 * Efficient cache or osmnodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.codec.MicroCache
import dev.skynomads.beerouter.codec.WaypointMatcher
import dev.skynomads.beerouter.expressions.BExpressionContextWay
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import java.io.File

public class NodesCache(
    private var segmentDir: File,
    private val ctxWay: BExpressionContextWay,
    forceSecondaryData: Boolean,
    maxmem: Long,
    oldCache: NodesCache?,
    detailed: Boolean
) {
    private val MAX_DYNAMIC_CATCHES = 20 // used with RoutingEngine MAX_DYNAMIC_RANGE = 60000m

    public var nodesMap: OsmNodesMap = OsmNodesMap()
    private val lookupVersion: Int
    private val lookupMinorVersion: Int
    private val forceSecondaryData: Boolean
    private var currentFileName: String? = null

    private var fileCache: MutableMap<String, PhysicalFile?> = HashMap(4)
    private var dataBuffers: DataBuffers = DataBuffers()

    private var fileRows: Array<Array<OsmFile?>?>

    public var waypointMatcher: WaypointMatcher? = null

    public var firstFileAccessFailed: Boolean = false
    public var firstFileAccessName: String?

    private var cacheSum: Long = 0
    private var maxmemtiles: Long = maxmem / 8
    private var detailed: Boolean // NOPMD used in constructor

    private var garbageCollectionEnabled = false
    private var ghostCleaningDone = false


    private var cacheSumClean: Long = 0
    private var ghostSum: Long = 0
    private var ghostWakeup: Long = 0

    private val directWeaving = true //!Boolean.getBoolean("disableDirectWeaving")

    public fun formatStatus(): String {
        return "collecting=$garbageCollectionEnabled noGhosts=$ghostCleaningDone cacheSum=$cacheSum cacheSumClean=$cacheSumClean ghostSum=$ghostSum ghostWakeup=$ghostWakeup"
    }

    init {
        this.nodesMap.maxmem = (2L * maxmem) / 3L
        this.lookupVersion = ctxWay.meta.lookupVersion.toInt()
        this.lookupMinorVersion = ctxWay.meta.lookupMinorVersion.toInt()
        this.forceSecondaryData = forceSecondaryData
        this.detailed = detailed

        ctxWay.setDecodeForbidden(detailed)

        firstFileAccessFailed = false
        firstFileAccessName = null

        if (!this.segmentDir.isDirectory) throw RuntimeException("segment directory ${segmentDir.absolutePath} does not exist")

        if (oldCache != null) {
            fileCache = oldCache.fileCache
            dataBuffers = oldCache.dataBuffers

            // re-use old, virgin caches (if same detail-mode)
            if (oldCache.detailed == detailed) {
                fileRows = oldCache.fileRows
                for (fileRow in fileRows) {
                    if (fileRow == null) continue
                    for (osmf in fileRow) {
                        cacheSum += osmf!!.setGhostState()
                    }
                }
            } else {
                fileRows = arrayOfNulls(180)
            }
        } else {
            fileRows = arrayOfNulls(180)
            dataBuffers = DataBuffers()
        }
        ghostSum = cacheSum
    }

    public fun clean(all: Boolean) {
        for (fileRow in fileRows) {
            if (fileRow == null) continue
            for (osmf in fileRow) {
                osmf!!.clean(all)
            }
        }
    }

    // if the cache sum exceeded a threshold,
    // clean all ghosts and enable garbage collection
    private fun checkEnableCacheCleaning() {
        if (cacheSum < maxmemtiles) {
            return
        }

        for (fileRow in fileRows) {
            if (fileRow == null) continue
            for (osmf in fileRow) {
                cacheSum -= if (garbageCollectionEnabled && !ghostCleaningDone) {
                    osmf!!.cleanGhosts()
                } else {
                    osmf!!.collectAll()
                }
            }
        }

        if (garbageCollectionEnabled) {
            ghostCleaningDone = true
            maxmemtiles *= 2
        } else {
            cacheSumClean = cacheSum
            garbageCollectionEnabled = true
        }
    }

    public fun loadSegmentFor(ilon: Int, ilat: Int): Int {
        val mc = getSegmentFor(ilon, ilat)
        return mc?.size ?: 0
    }

    public fun getSegmentFor(ilon: Int, ilat: Int): MicroCache? {
        try {
            val lonDegree = ilon / 1000000
            val latDegree = ilat / 1000000
            var osmf: OsmFile? = null
            val fileRow = fileRows[latDegree]
            val ndegrees = fileRow?.size ?: 0
            for (i in 0..<ndegrees) {
                if (fileRow!![i]!!.lonDegree == lonDegree) {
                    osmf = fileRow[i]
                    break
                }
            }
            if (osmf == null) {
                osmf = fileForSegment(lonDegree, latDegree)
                val newFileRow = arrayOfNulls<OsmFile>(ndegrees + 1)
                for (i in 0..<ndegrees) {
                    newFileRow[i] = fileRow!![i]
                }
                newFileRow[ndegrees] = osmf
                fileRows[latDegree] = newFileRow
            }
            currentFileName = osmf.filename

            if (!osmf.hasData()) {
                return null
            }

            var segment = osmf.getMicroCache(ilon, ilat)
            // needed for a second chance
            if (segment == null || (waypointMatcher != null && (waypointMatcher as WaypointMatcherImpl).useDynamicRange)) {
                checkEnableCacheCleaning()
                segment = osmf.createMicroCache(
                    ilon,
                    ilat,
                    dataBuffers,
                    ctxWay,
                    waypointMatcher,
                    if (directWeaving) nodesMap else null
                )

                cacheSum += segment.dataSize.toLong()
            } else if (segment.ghost) {
                segment.unGhost()
                ghostWakeup += segment.dataSize.toLong()
            }
            return segment
        } catch (re: RuntimeException) {
            throw re
        } catch (e: Exception) {
            throw RuntimeException("error reading datafile $currentFileName: $e", e)
        }
    }

    /**
     * make sure the given node is non-hollow,
     * which means it contains not just the id,
     * but also the actual data
     *
     * @return true if successfull, false if node is still hollow
     */
    public fun obtainNonHollowNode(node: OsmNode): Boolean {
        if (!node.isHollow) return true

        val segment = getSegmentFor(node.position.longitude.toIntLongitude(), node.position.latitude.toIntLatitude())
        if (segment == null) {
            return false
        }
        if (!node.isHollow) {
            return true // direct weaving...
        }

        val id = node.idFromPos
        if (segment.getAndClear(id)) {
            node.parseNodeBody(segment, nodesMap, ctxWay)
        }

        if (garbageCollectionEnabled) { // garbage collection
            cacheSum -= segment.collect(segment.size shr 1)
                .toLong() // threshold = 1/2 of size is deleted
        }

        return !node.isHollow
    }


    /**
     * make sure all link targets of the given node are non-hollow
     */
    public fun expandHollowLinkTargets(n: OsmNode) {
        var link = n.firstlink
        while (link != null) {
            obtainNonHollowNode(link.getTarget(n)!!)
            link = link.getNext(n)
        }
    }

    /**
     * make sure all link targets of the given node are non-hollow
     */
    public fun hasHollowLinkTargets(n: OsmNode): Boolean {
        var link = n.firstlink
        while (link != null) {
            if (link.getTarget(n)!!.isHollow) {
                return true
            }
            link = link.getNext(n)
        }
        return false
    }

    /**
     * get a node for the given id with all link-targets also non-hollow
     *
     *
     * It is required that an instance of the start-node does not yet
     * exist, not even a hollow instance, so getStartNode should only
     * be called once right after resetting the cache
     *
     * @param id the id of the node to load
     * @return the fully expanded node for id, or null if it was not found
     */
    public fun getStartNode(id: Long): OsmNode? {
        // initialize the start-node
        val n = OsmNode(id)
        n.setHollow()
        nodesMap.put(n)
        if (!obtainNonHollowNode(n)) {
            return null
        }
        expandHollowLinkTargets(n)
        return n
    }

    public fun getGraphNode(template: OsmNode): OsmNode {
        val graphNode = OsmNode(template.position)
        graphNode.setHollow()
        val existing = nodesMap.put(graphNode)
        if (existing == null) {
            return graphNode
        }
        nodesMap.put(existing)
        return existing
    }

    public fun matchWaypointsToNodes(
        unmatchedWaypoints: MutableList<MatchedWaypoint>,
        maxDistance: Double,
        islandNodePairs: OsmNodePairSet?
    ): Boolean {
        waypointMatcher = WaypointMatcherImpl(unmatchedWaypoints, maxDistance, islandNodePairs!!)
        for (mwp in unmatchedWaypoints) {
            var cellsize = 12500
            preloadPosition(mwp.waypoint!!, cellsize, 1, false)
            // get a second chance
            if (mwp.crosspoint == null || mwp.radius > RETRY_RANGE) {
                cellsize = 1000000 / 32
                preloadPosition(
                    mwp.waypoint!!,
                    cellsize,
                    if (maxDistance < 0) MAX_DYNAMIC_CATCHES else 2,
                    maxDistance < 0
                )
            }
        }

        require(!firstFileAccessFailed) { "datafile $firstFileAccessName not found" }
        for ((i, mwp) in unmatchedWaypoints.withIndex()) {
            if (mwp.crosspoint == null) {
                if (unmatchedWaypoints.size > 1 && i == unmatchedWaypoints.size - 1 && unmatchedWaypoints[i - 1].type == MatchedWaypoint.Type.DIRECT
                ) {
                    mwp.crosspoint = OsmNode(mwp.waypoint!!.position)
                    mwp.type = MatchedWaypoint.Type.DIRECT
                } else {
                    // do not break here throw new IllegalArgumentException(mwp.name + "-position not mapped in existing datafile");
                    return false
                }
            }
            if (unmatchedWaypoints.size > 1 && i == unmatchedWaypoints.size - 1 && unmatchedWaypoints[i - 1].type == MatchedWaypoint.Type.DIRECT
            ) {
                mwp.crosspoint = OsmNode(mwp.waypoint!!.position)
                mwp.type = MatchedWaypoint.Type.DIRECT
            }
        }
        return true
    }

    private fun preloadPosition(
        n: OsmNode,
        d: Int,
        maxscale: Int,
        bUseDynamicRange: Boolean
    ) {
        firstFileAccessFailed = false
        firstFileAccessName = null
        loadSegmentFor(n.position.longitude.toIntLongitude(), n.position.latitude.toIntLatitude())
        require(!firstFileAccessFailed) { "datafile $firstFileAccessName not found" }
        var scale = 1
        while (scale < maxscale) {
            for (idxLat in -scale..scale) for (idxLon in -scale..scale) {
                if (idxLon != 0 || idxLat != 0) {
                    loadSegmentFor(n.position.longitude.toIntLongitude() + d * idxLon, n.position.latitude.toIntLatitude() + d * idxLat)
                }
            }
            if (bUseDynamicRange && waypointMatcher!!.hasMatch(n.position.longitude.toIntLongitude(), n.position.latitude.toIntLatitude())) break
            scale++
        }
    }

    private fun fileForSegment(lonDegree: Int, latDegree: Int): OsmFile {
        val lonMod5 = lonDegree % 5
        val latMod5 = latDegree % 5

        val lon = lonDegree - 180 - lonMod5
        val slon = if (lon < 0) "W" + (-lon) else "E$lon"
        val lat = latDegree - 90 - latMod5

        val slat = if (lat < 0) "S" + (-lat) else "N$lat"
        val filenameBase = slon + "_" + slat

        currentFileName = "$filenameBase.rd5"

        val ra: PhysicalFile? = fileCache.getOrPut(filenameBase) {
            if (!forceSecondaryData) {
                val primary = File(segmentDir, "$filenameBase.rd5")
                if (primary.exists()) {
                    currentFileName = primary.name
                    return@getOrPut PhysicalFile(primary, dataBuffers, lookupVersion, lookupMinorVersion)
                }
            }
            null
        }
        val osmf = OsmFile(ra, lonDegree, latDegree, dataBuffers)

        if (firstFileAccessName == null) {
            firstFileAccessName = currentFileName
            firstFileAccessFailed = osmf.filename == null
        }

        return osmf
    }

    public fun close() {
        for (f in fileCache.values) {
            runCatching { f?.close() }
        }
    }

    public fun getElevationType(ilon: Int, ilat: Int): Int {
        val lonDegree = ilon / 1000000
        val latDegree = ilat / 1000000
        val fileRow = fileRows[latDegree]
        val ndegrees = fileRow?.size ?: 0
        for (i in 0..<ndegrees) {
            if (fileRow!![i]!!.lonDegree == lonDegree) {
                val osmf = fileRow[i]
                if (osmf != null) return osmf.elevationType.toInt()
                break
            }
        }
        return 3
    }

    public companion object {
        public const val RETRY_RANGE: Int = 250
    }
}

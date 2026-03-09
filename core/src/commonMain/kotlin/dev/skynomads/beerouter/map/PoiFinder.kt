package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.expressions.BExpressionContextNode
import dev.skynomads.beerouter.expressions.BExpressionMetaData
import kotlin.math.max
import kotlin.math.min

public class PoiFinder(
    private val mapSource: MapSource,
    lookupContent: String,
) {
    private val dataBuffers: DataBuffers = DataBuffers()
    private val nodeContext: BExpressionContextNode
    private val lookupVersion: Int
    private val lookupMinorVersion: Int
    private val fileCache: MutableMap<String, PhysicalFile?> = linkedMapOf()

    init {
        val meta = BExpressionMetaData()
        nodeContext = BExpressionContextNode(meta)
        meta.readMetaData(lookupContent)
        lookupVersion = meta.lookupVersion.toInt()
        lookupMinorVersion = meta.lookupMinorVersion.toInt()
    }

    public fun find(query: PoiQuery): List<PoiResult> {
        val results = mutableListOf<PoiResult>()
        val seenNodeIds = linkedSetOf<Long>()
        val minLonDegree = floorDiv(query.minLon, ONE_DEGREE)
        val maxLonDegree = floorDiv(query.maxLon, ONE_DEGREE)
        val minLatDegree = floorDiv(query.minLat, ONE_DEGREE)
        val maxLatDegree = floorDiv(query.maxLat, ONE_DEGREE)

        for (latDegree in minLatDegree..maxLatDegree) {
            for (lonDegree in minLonDegree..maxLonDegree) {
                val physicalFile = physicalFileForDegrees(lonDegree, latDegree) ?: continue
                val osmFile = OsmFile(physicalFile, lonDegree, latDegree, dataBuffers)
                if (!osmFile.hasData()) {
                    continue
                }

                val divisor = physicalFile.divisor
                val cellSize = ONE_DEGREE / divisor
                val degreeLonMin = lonDegree * ONE_DEGREE
                val degreeLatMin = latDegree * ONE_DEGREE
                val degreeLonMax = degreeLonMin + ONE_DEGREE - 1
                val degreeLatMax = degreeLatMin + ONE_DEGREE - 1

                val scanLonMin = max(query.minLon, degreeLonMin)
                val scanLonMax = min(query.maxLon, degreeLonMax)
                val scanLatMin = max(query.minLat, degreeLatMin)
                val scanLatMax = min(query.maxLat, degreeLatMax)
                if (scanLonMin > scanLonMax || scanLatMin > scanLatMax) {
                    continue
                }

                val minLonIdx = floorDiv(scanLonMin, cellSize)
                val maxLonIdx = floorDiv(scanLonMax, cellSize)
                val minLatIdx = floorDiv(scanLatMin, cellSize)
                val maxLatIdx = floorDiv(scanLatMax, cellSize)

                for (latIdx in minLatIdx..maxLatIdx) {
                    for (lonIdx in minLonIdx..maxLonIdx) {
                        val segment = osmFile.createMicroCacheForCell(
                            lonIdx,
                            latIdx,
                            dataBuffers,
                            null,
                            null,
                            null
                        )
                        val nodesMap = OsmNodesMap()
                        val size = segment.size
                        for (index in 0 until size) {
                            val nodeId = segment.getIdForIndex(index)
                            if (!segment.getAndClear(nodeId)) {
                                continue
                            }
                            val node = OsmNode(nodeId)
                            node.parseNodeBody(segment, nodesMap, nodeContext)
                            if (node.position.longitude !in query.minLon..query.maxLon ||
                                node.position.latitude !in query.minLat..query.maxLat
                            ) {
                                continue
                            }
                            val description = node.nodeDescription ?: continue
                            val tags = nodeContext.getMap(false, description)
                            if (!matchesRequiredTags(tags, query.requiredTags)) {
                                continue
                            }
                            if (!seenNodeIds.add(node.idFromPos)) {
                                continue
                            }
                            results += PoiResult(
                                nodeId = node.idFromPos,
                                position = node.position,
                                tags = tags,
                                sourceFile = physicalFile.fileName,
                            )
                            if (query.limit != null && results.size >= query.limit) {
                                return results
                            }
                        }
                    }
                }
            }
        }
        return results
    }

    public fun close() {
        for (physicalFile in fileCache.values) {
            runCatching { physicalFile?.close() }
        }
        fileCache.clear()
    }

    private fun physicalFileForDegrees(lonDegree: Int, latDegree: Int): PhysicalFile? {
        val fileName = rd5FileName(lonDegree, latDegree)
        return fileCache.getOrPut(fileName.removeSuffix(".rd5")) {
            if (!mapSource.exists(fileName)) {
                return@getOrPut null
            }
            PhysicalFile(
                fileName = fileName,
                mapSource = mapSource,
                dataBuffers = dataBuffers,
                lookupVersion = lookupVersion,
                lookupMinorVersion = lookupMinorVersion,
            )
        }
    }

    private fun rd5FileName(lonDegree: Int, latDegree: Int): String {
        val lonMod5 = lonDegree % 5
        val latMod5 = latDegree % 5
        val lon = lonDegree - 180 - lonMod5
        val lat = latDegree - 90 - latMod5
        val slon = if (lon < 0) "W${-lon}" else "E$lon"
        val slat = if (lat < 0) "S${-lat}" else "N$lat"
        return "${slon}_${slat}.rd5"
    }

    private fun matchesRequiredTags(
        tags: Map<String, String>,
        requiredTags: Map<String, Set<String>>
    ): Boolean {
        for ((key, expectedValues) in requiredTags) {
            val actualValue = tags[key] ?: return false
            if (expectedValues.isNotEmpty() && actualValue !in expectedValues) {
                return false
            }
        }
        return true
    }

    private companion object {
        private const val ONE_DEGREE: Int = 1_000_000

        private fun floorDiv(dividend: Int, divisor: Int): Int {
            val quotient = dividend / divisor
            val remainder = dividend % divisor
            return if (remainder != 0 && (dividend xor divisor) < 0) quotient - 1 else quotient
        }
    }
}

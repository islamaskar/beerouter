package dev.skynomads.beerouter.map.generator

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.geo.UNSET_ELEVATION
import dev.skynomads.beerouter.map.MapSource
import dev.skynomads.beerouter.map.PoiFinder
import dev.skynomads.beerouter.map.PoiQuery
import dev.skynomads.beerouter.map.RandomAccessReader
import java.io.File
import java.io.RandomAccessFile
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.GZIPInputStream
import javax.xml.stream.XMLInputFactory
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

public class GeneratorTest {
    @Test
    public fun elevationRasterClampsEncodedElevation() {
        fun rasterWithElevation(elevationMeters: Short): ElevationRaster = ElevationRaster().apply {
            ncols = 2
            nrows = 2
            xllcorner = 0.0
            yllcorner = 0.0
            cellsize = 1.0
            eval_array = ShortArray(4) { elevationMeters }
        }

        val position = Position(0.5, 0.5)

        assertEquals(Short.MAX_VALUE, rasterWithElevation(8849).getElevation(position))
        assertEquals((Short.MIN_VALUE + 1).toShort(), rasterWithElevation((-9000).toShort()).getElevation(position))
        assertEquals(UNSET_ELEVATION, rasterWithElevation(UNSET_ELEVATION).getElevation(position))
    }

    @Test
    public fun generatorTest() {
        val mapFile = testResourceFile("/dreieich.pbf")
        val workingDir = requireNotNull(mapFile.parentFile)
        val profileDir = findProfileDir()
        val tmpdir = createTempDirectory("generator-test-").toFile()

        val generation = generateBeerouterSegments(tmpdir, mapFile, workingDir, profileDir)
        val segments = generation.segments

        assertTrue(
            requireNotNull(segments.listFiles()).isNotEmpty(),
            "segments directory should not be empty"
        )
    }

    @Test
    public fun rd5MatchesUpstreamBrouter() {
        val mapFile = testResourceFile("/dreieich.pbf")
        val workingDir = requireNotNull(mapFile.parentFile)
        val profileDir = findProfileDir()
        val tmpdir = createTempDirectory("generator-parity-").toFile()

        val beerouterSegments = generateBeerouterSegments(
            File(tmpdir, "beerouter"),
            mapFile,
            workingDir,
            profileDir
        ).segments
        val brouterSegments = UpstreamBrouterReference.generateSegments(
            baseDir = File(tmpdir, "brouter"),
            mapFile = mapFile,
            srtmDir = workingDir,
            profileDir = profileDir,
        )

        assertSegmentDirectoriesEquivalent(beerouterSegments, brouterSegments)
    }

    @Test
    public fun poiFinderReturnsConfiguredToilets() {
        val mapFile = testResourceFile("/dreieich.pbf")
        val osmFile = testResourceFile("/dreieich.osm.gz")
        val workingDir = requireNotNull(mapFile.parentFile)
        val profileDir = findProfileDir()
        val tmpdir = createTempDirectory("generator-poi-").toFile()

        val generation = generateBeerouterSegments(
            tmpdir = tmpdir,
            mapFile = mapFile,
            workingDir = workingDir,
            profileDir = profileDir,
            poiConfigContent = "amenity=toilets\n",
        )
        val expectedPois = parseExpectedPois(osmFile, "amenity", "toilets")
        assertTrue(expectedPois.isNotEmpty(), "expected toilets in dreieich fixture")

        val bbox = expectedPois.boundingBox()
        val finder = PoiFinder(
            mapSource = TestMapSource(generation.segments),
            lookupContent = generation.lookupFile.readText(),
        )

        val results = finder.find(
            PoiQuery(
                minLon = bbox.minLon,
                minLat = bbox.minLat,
                maxLon = bbox.maxLon,
                maxLat = bbox.maxLat,
                requiredTags = mapOf("amenity" to setOf("toilets")),
            ),
        )
        finder.close()

        assertEquals(
            expectedPois.map(ExpectedPoi::positionId).sorted(),
            results.map { it.nodeId }.sorted()
        )
        assertTrue(results.all { it.tags["amenity"] == "toilets" })
    }

    @Test
    public fun poiFinderDeduplicatesAcrossRd5Boundaries() {
        val mapFile = testResourceFile("/dreieich.pbf")
        val osmFile = testResourceFile("/dreieich.osm.gz")
        val workingDir = requireNotNull(mapFile.parentFile)
        val profileDir = findProfileDir()
        val tmpdir = createTempDirectory("generator-poi-dedupe-").toFile()

        val generation = generateBeerouterSegments(
            tmpdir = tmpdir,
            mapFile = mapFile,
            workingDir = workingDir,
            profileDir = profileDir,
            poiConfigContent = "amenity=toilets\n",
        )
        val expectedPois = parseExpectedPois(osmFile, "amenity", "toilets")
        val bbox = expectedPois.boundingBox()
        val sourceFile = rd5FileNameForDegrees(
            lonDegree = expectedPois.first().longitude / 1_000_000,
            latDegree = expectedPois.first().latitude / 1_000_000,
        )
        val duplicateFile = File(generation.segments, "E10_N40.rd5")
        val duplicatedFileName = "E10_N45.rd5"
        Files.copy(
            File(generation.segments, sourceFile).toPath(),
            File(generation.segments, duplicatedFileName).toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )

        val finder = PoiFinder(
            mapSource = TestMapSource(generation.segments),
            lookupContent = generation.lookupFile.readText(),
        )
        val results = finder.find(
            PoiQuery(
                minLon = bbox.minLon,
                minLat = bbox.minLat,
                maxLon = 190_000_000,
                maxLat = bbox.maxLat,
                requiredTags = mapOf("amenity" to setOf("toilets")),
            ),
        )
        finder.close()

        assertEquals(expectedPois.size, results.size)
        assertEquals(
            expectedPois.map(ExpectedPoi::positionId).sorted(),
            results.map { it.nodeId }.sorted()
        )
    }

    @Test
    public fun poiFinderRejectsLookupMismatch() {
        val mapFile = testResourceFile("/dreieich.pbf")
        val workingDir = requireNotNull(mapFile.parentFile)
        val profileDir = findProfileDir()
        val tmpdir = createTempDirectory("generator-poi-lookup-mismatch-").toFile()

        val generation = generateBeerouterSegments(
            tmpdir = tmpdir,
            mapFile = mapFile,
            workingDir = workingDir,
            profileDir = profileDir,
            poiConfigContent = "amenity=toilets\n",
        )
        val finder = PoiFinder(
            mapSource = TestMapSource(generation.segments),
            lookupContent = File(profileDir, "lookups.dat").readText(),
        )
        val bbox = parseExpectedPois(
            testResourceFile("/dreieich.osm.gz"),
            "amenity",
            "toilets"
        ).boundingBox()

        val error = assertFailsWith<Exception> {
            finder.find(
                PoiQuery(
                    minLon = bbox.minLon,
                    minLat = bbox.minLat,
                    maxLon = bbox.maxLon,
                    maxLat = bbox.maxLat,
                    requiredTags = mapOf("amenity" to setOf("toilets")),
                    limit = 1,
                ),
            )
        }
        assertTrue(error.message?.contains("lookup minor version mismatch") == true)
        finder.close()
    }

    private fun generateBeerouterSegments(
        tmpdir: File,
        mapFile: File,
        workingDir: File,
        profileDir: File,
        poiConfigContent: String? = null,
    ): GeneratedSegments {
        tmpdir.mkdirs()

        val nodes = File(tmpdir, "nodetiles").apply { mkdir() }
        val ways = File(tmpdir, "waytiles").apply { mkdir() }
        val nodes55 = File(tmpdir, "nodes55").apply { mkdir() }
        val ways55 = File(tmpdir, "waytiles55").apply { mkdir() }
        val baseLookupFile = File(profileDir, "lookups.dat")
        val poiGenerationConfig =
            poiConfigContent?.let(PoiConfigParser::parse) ?: PoiGenerationConfig.EMPTY
        val generatedLookup =
            GeneratedLookups.generate(baseLookupFile.readText(), poiGenerationConfig)
        val lookupFile =
            File(tmpdir, "generated-lookups.dat").apply { writeText(generatedLookup.lookupContent) }
        val relFile = File(tmpdir, "cycleways.dat")
        val resFile = File(tmpdir, "restrictions.dat")
        val profileAll = File(profileDir, "all.brf")
        val profileReport = File(profileDir, "trekking.brf")
        val profileCheck = File(profileDir, "softaccess.brf")
        val borderFile = File(tmpdir, "bordernids.dat")

        OsmFastCutter.doCut(
            lookupFile,
            nodes,
            ways,
            nodes55,
            ways55,
            borderFile,
            relFile,
            resFile,
            profileAll,
            profileReport,
            profileCheck,
            mapFile,
            null,
            poiGenerationConfig,
        )

        val unodes55 = File(tmpdir, "unodes55").apply { mkdir() }
        val bordernodes = File(tmpdir, "bordernodes.dat")
        PosUnifier().process(
            nodes55,
            unodes55,
            borderFile,
            bordernodes,
            workingDir.absolutePath,
            null
        )

        val segments = File(tmpdir, "segments").apply { mkdir() }
        WayLinker().process(
            unodes55,
            ways55,
            bordernodes,
            resFile,
            lookupFile,
            profileAll,
            segments,
            "rd5"
        )
        return GeneratedSegments(segments, lookupFile)
    }

    private fun parseExpectedPois(osmFile: File, key: String, value: String): List<ExpectedPoi> {
        val xmlFactory = XMLInputFactory.newFactory()
        GZIPInputStream(osmFile.inputStream()).use { gzip ->
            val reader = xmlFactory.createXMLStreamReader(gzip)
            val pois = mutableListOf<ExpectedPoi>()
            var currentNodeId: Long? = null
            var currentLon: Int? = null
            var currentLat: Int? = null
            var matches = false
            while (reader.hasNext()) {
                when (reader.next()) {
                    javax.xml.stream.XMLStreamConstants.START_ELEMENT -> {
                        when (reader.localName) {
                            "node" -> {
                                currentNodeId = reader.getAttributeValue(null, "id").toLong()
                                val position = Position(
                                    reader.getAttributeValue(null, "lon").toDouble(),
                                    reader.getAttributeValue(null, "lat").toDouble(),
                                )
                                currentLon = position.longitude
                                currentLat = position.latitude
                                matches = false
                            }

                            "tag" -> {
                                if (currentNodeId != null &&
                                    reader.getAttributeValue(null, "k") == key &&
                                    reader.getAttributeValue(null, "v") == value
                                ) {
                                    matches = true
                                }
                            }
                        }
                    }

                    javax.xml.stream.XMLStreamConstants.END_ELEMENT -> {
                        if (reader.localName == "node") {
                            if (matches) {
                                pois += ExpectedPoi(
                                    osmNodeId = requireNotNull(currentNodeId),
                                    positionId = Position.computeId(
                                        requireNotNull(currentLon),
                                        requireNotNull(currentLat)
                                    ),
                                    longitude = requireNotNull(currentLon),
                                    latitude = requireNotNull(currentLat),
                                )
                            }
                            currentNodeId = null
                            currentLon = null
                            currentLat = null
                            matches = false
                        }
                    }
                }
            }
            reader.close()
            return pois
        }
    }

    private fun assertSegmentDirectoriesEquivalent(
        beerouterSegments: File,
        brouterSegments: File,
    ) {
        val beerouterFiles = beerouterSegments
            .walkTopDown()
            .filter(File::isFile)
            .associateBy { it.relativeTo(beerouterSegments).invariantSeparatorsPath }
        val brouterFiles = brouterSegments
            .walkTopDown()
            .filter(File::isFile)
            .associateBy { it.relativeTo(brouterSegments).invariantSeparatorsPath }

        assertEquals(brouterFiles.keys, beerouterFiles.keys, "segment file set differs")

        for ((relativePath, beerouterFile) in beerouterFiles) {
            val brouterFile =
                requireNotNull(brouterFiles[relativePath]) { "missing $relativePath in brouter output" }
            assertRd5Equivalent(beerouterFile, brouterFile)
        }
    }

    private fun assertRd5Equivalent(
        beerouterFile: File,
        brouterFile: File,
    ) {
        val beerouterBytes = beerouterFile.readBytes()
        val brouterBytes = brouterFile.readBytes()

        assertEquals(
            brouterBytes.size,
            beerouterBytes.size,
            "${beerouterFile.name}: file size differs",
        )

        if (beerouterBytes.contentEquals(brouterBytes)) {
            return
        }

        val beerouterTimestampOffset = rd5TimestampOffset(beerouterBytes)
        val brouterTimestampOffset = rd5TimestampOffset(brouterBytes)
        assertEquals(
            brouterTimestampOffset,
            beerouterTimestampOffset,
            "${beerouterFile.name}: timestamp offset differs",
        )

        val diffPositions = buildList {
            for (index in beerouterBytes.indices) {
                if (beerouterBytes[index] != brouterBytes[index]) add(index)
            }
        }
        val timestampRange =
            beerouterTimestampOffset until (beerouterTimestampOffset + Long.SIZE_BYTES)
        assertTrue(
            diffPositions.all(timestampRange::contains),
            "${beerouterFile.name}: unexpected byte differences at ${diffPositions.take(16)}",
        )

        val normalizedBeerouter = beerouterBytes.copyOf()
        val normalizedBrouter = brouterBytes.copyOf()
        repeat(Long.SIZE_BYTES) { index ->
            normalizedBeerouter[beerouterTimestampOffset + index] = 0
            normalizedBrouter[beerouterTimestampOffset + index] = 0
        }
        assertContentEquals(
            normalizedBrouter,
            normalizedBeerouter,
            "${beerouterFile.name}: normalized rd5 content differs",
        )
    }

    private fun rd5TimestampOffset(bytes: ByteArray): Int {
        val fileIndexEntry = readLongBigEndian(bytes, 24 * Long.SIZE_BYTES)
        val offset = (fileIndexEntry and 0x0000FFFFFFFFFFFFL).toInt()
        assertTrue(offset >= 200, "rd5 timestamp offset must be after the 200-byte file index")
        assertTrue(offset + Long.SIZE_BYTES <= bytes.size, "rd5 timestamp offset out of bounds")
        return offset
    }

    private fun readLongBigEndian(bytes: ByteArray, offset: Int): Long {
        require(offset + Long.SIZE_BYTES <= bytes.size) {
            "cannot read long at offset $offset from buffer of size ${bytes.size}"
        }
        var value = 0L
        repeat(Long.SIZE_BYTES) { index ->
            value = (value shl 8) or (bytes[offset + index].toLong() and 0xff)
        }
        return value
    }

    private fun testResourceFile(path: String): File {
        val resource = javaClass.getResource(path)
        assertNotNull(resource, "missing test resource $path")
        return File(URI(resource.toString()))
    }

    private fun findProfileDir(): File {
        val candidates = listOf(
            File(System.getProperty("user.dir"), "misc/profiles2"),
            File(System.getProperty("user.dir"), "../misc/profiles2"),
        )
        return candidates.firstOrNull(File::isDirectory)
            ?: error("could not find misc/profiles2 from ${System.getProperty("user.dir")}")
    }

    private fun rd5FileNameForDegrees(lonDegree: Int, latDegree: Int): String {
        val lonMod5 = lonDegree % 5
        val latMod5 = latDegree % 5
        val lon = lonDegree - 180 - lonMod5
        val lat = latDegree - 90 - latMod5
        val slon = if (lon < 0) "W${-lon}" else "E$lon"
        val slat = if (lat < 0) "S${-lat}" else "N$lat"
        return "${slon}_${slat}.rd5"
    }

    private data class GeneratedSegments(
        val segments: File,
        val lookupFile: File,
    )

    private data class ExpectedPoi(
        val osmNodeId: Long,
        val positionId: Long,
        val longitude: Int,
        val latitude: Int,
    )

    private data class PoiBoundingBox(
        val minLon: Int,
        val minLat: Int,
        val maxLon: Int,
        val maxLat: Int,
    )

    private fun List<ExpectedPoi>.boundingBox(): PoiBoundingBox {
        return PoiBoundingBox(
            minLon = minOf(ExpectedPoi::longitude) - 1_000,
            minLat = minOf(ExpectedPoi::latitude) - 1_000,
            maxLon = maxOf(ExpectedPoi::longitude) + 1_000,
            maxLat = maxOf(ExpectedPoi::latitude) + 1_000,
        )
    }

    private class TestMapSource(private val segmentDir: File) : MapSource {
        override fun exists(fileName: String): Boolean = File(segmentDir, fileName).isFile

        override fun open(fileName: String): RandomAccessReader =
            TestRandomAccessReader(File(segmentDir, fileName))
    }

    private class TestRandomAccessReader(file: File) : RandomAccessReader {
        private val delegate = RandomAccessFile(file, "r")

        override fun seek(position: Long) {
            delegate.seek(position)
        }

        override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
            delegate.readFully(buffer, offset, length)
        }

        override fun length(): Long = delegate.length()

        override fun close() {
            delegate.close()
        }
    }
}

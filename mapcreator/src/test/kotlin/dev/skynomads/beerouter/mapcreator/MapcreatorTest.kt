package dev.skynomads.beerouter.mapcreator

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import java.net.URI
import kotlin.io.path.createTempDirectory

public class MapcreatorTest {
    @Test
    public fun mapcreatorTest() {
        System.setProperty("avoidMapPolling", "true")

        val mapFile = testResourceFile("/dreieich.pbf")
        val workingDir = requireNotNull(mapFile.parentFile)
        val profileDir = findProfileDir()
        val tmpdir = createTempDirectory(workingDir.toPath(), "mapcreator-test-").toFile()

        val segments = generateBeerouterSegments(tmpdir, mapFile, workingDir, profileDir)

        assertTrue(requireNotNull(segments.listFiles()).isNotEmpty(), "segments directory should not be empty")
    }

    @Test
    public fun rd5MatchesUpstreamBrouter() {
        System.setProperty("avoidMapPolling", "true")

        val mapFile = testResourceFile("/dreieich.pbf")
        val workingDir = requireNotNull(mapFile.parentFile)
        val profileDir = findProfileDir()
        val tmpdir = createTempDirectory(workingDir.toPath(), "mapcreator-parity-").toFile()

        val beerouterSegments = generateBeerouterSegments(File(tmpdir, "beerouter"), mapFile, workingDir, profileDir)
        val brouterSegments = UpstreamBrouterReference.generateSegments(
            baseDir = File(tmpdir, "brouter"),
            mapFile = mapFile,
            srtmDir = workingDir,
            profileDir = profileDir,
        )

        assertSegmentDirectoriesEquivalent(beerouterSegments, brouterSegments)
    }

    private fun generateBeerouterSegments(
        tmpdir: File,
        mapFile: File,
        workingDir: File,
        profileDir: File,
    ): File {
        tmpdir.mkdirs()

        val nodes = File(tmpdir, "nodetiles").apply { mkdir() }
        val ways = File(tmpdir, "waytiles").apply { mkdir() }
        val nodes55 = File(tmpdir, "nodes55").apply { mkdir() }
        val ways55 = File(tmpdir, "waytiles55").apply { mkdir() }
        val lookupFile = File(profileDir, "lookups.dat")
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
        )

        val unodes55 = File(tmpdir, "unodes55").apply { mkdir() }
        val bordernodes = File(tmpdir, "bordernodes.dat")
        PosUnifier().process(nodes55, unodes55, borderFile, bordernodes, workingDir.absolutePath, null)

        val segments = File(tmpdir, "segments").apply { mkdir() }
        WayLinker().process(unodes55, ways55, bordernodes, resFile, lookupFile, profileAll, segments, "rd5")
        return segments
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
            val brouterFile = requireNotNull(brouterFiles[relativePath]) { "missing $relativePath in brouter output" }
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
        val timestampRange = beerouterTimestampOffset until (beerouterTimestampOffset + Long.SIZE_BYTES)
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
}

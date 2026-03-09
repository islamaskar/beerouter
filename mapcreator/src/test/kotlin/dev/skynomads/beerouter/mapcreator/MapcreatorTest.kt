package dev.skynomads.beerouter.mapcreator

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import kotlin.io.path.createTempDirectory

public class MapcreatorTest {
    @Test
    public fun mapcreatorTest() {
        System.setProperty("avoidMapPolling", "true")

        val mapUrl = javaClass.getResource("/dreieich.pbf")
        assertNotNull(mapUrl, "test-osm-map dreieich.pbf not found")
        val mapFile = File(mapUrl.file)
        val workingDir = requireNotNull(mapFile.parentFile)
        val profileDir = File("/root/src/misc/profiles2")
        val tmpdir = createTempDirectory(workingDir.toPath(), "mapcreator-test-").toFile()

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

        assertTrue(requireNotNull(segments.listFiles()).isNotEmpty(), "segments directory should not be empty")
    }
}

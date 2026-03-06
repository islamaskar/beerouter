package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import kotlinx.coroutines.runBlocking
import org.maplibre.spatialk.geojson.Position
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VoiceHintParityTest {
    private val root = File(System.getProperty("user.dir"))
    private val segmentFile = File(
        File(System.getProperty("java.io.tmpdir")), "E5_N45.rd5"
    )

    private fun waypoint(name: String, lat: Double, lon: Double): OsmNodeNamed {
        val n = OsmNodeNamed()
        n.name = name
        n.position = Position(lon, lat)
        return n
    }

    private fun routeBrouter(profileName: String, waypoints: List<OsmNodeNamed>): btools.router.OsmTrack {
        val profile = File(root, "../misc/profiles2/$profileName")
        val rc = btools.router.RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.path
        }
        val cr = btools.router.RoutingEngine(
            null,
            null,
            segmentFile.parentFile,
            waypoints.map {
                btools.router.OsmNodeNamed().apply {
                    name = it.name
                    ilon = it.position.longitude.toIntLongitude()
                    ilat = it.position.latitude.toIntLatitude()
                }
            },
            rc
        )
        cr.doRouting(60_000)
        return cr.foundTrack
    }

    private suspend fun routeBeerouter(profileName: String, waypoints: List<OsmNodeNamed>): OsmTrack? {
        val profile = File(root, "../misc/profiles2/$profileName")
        val ctx = routingContextFromFiles(profile, segmentFile.parentFile).apply {
            global.turnInstructionMode = TurnInstructionMode.LOCUS
        }
        return RoutingEngine(ctx).doRouting(waypoints)
    }

    @Test
    fun shortestRoundaboutCommandParity() = runBlocking {
        val waypoints = listOf(
            waypoint("Stuttgart", 48.7758, 9.1829),
            waypoint("Balingen", 48.2768, 8.8554)
        )
        val btrack = routeBrouter("shortest.brf", waypoints)
        val beetrack = routeBeerouter("shortest.brf", waypoints)
        assertNotNull(beetrack)

        val idx = 52
        val expected = btrack.getVoiceHint(idx)
        val actual = beetrack.getVoiceHint(idx)
        assertNotNull(expected)
        assertNotNull(actual)
        assertEquals(
            expected.cruiserCommandString,
            actual.cruiserCommandString,
            "shortest roundabout mismatch at node $idx"
        )
    }

    @Test
    fun fastbikeTurnSeverityParity() = runBlocking {
        val waypoints = listOf(
            waypoint("Tübingen", 48.5203263, 9.053596),
            waypoint("Reutlingen", 48.4914, 9.2043),
            waypoint("Stuttgart", 48.7758, 9.1829)
        )
        val btrack = routeBrouter("fastbike.brf", waypoints)
        val beetrack = routeBeerouter("fastbike.brf", waypoints)
        assertNotNull(beetrack)

        val idx = 625
        val expected = btrack.getVoiceHint(idx)
        val actual = beetrack.getVoiceHint(idx)
        assertNotNull(expected)
        assertNotNull(actual)
        assertEquals(
            expected.cruiserCommandString,
            actual.cruiserCommandString,
            "fastbike turn severity mismatch at node $idx\n" +
                    "brouter=${nodeDebug(btrack, idx)}\n" +
                    "beerouter=${nodeDebug(beetrack, idx)}"
        )
    }

    @Test
    fun shortestVoiceHintCountParity() = runBlocking {
        val waypoints = listOf(
            waypoint("Reutlingen", 48.4914, 9.2043),
            waypoint("Tübingen", 48.5203263, 9.053596)
        )
        val btrack = routeBrouter("shortest.brf", waypoints)
        val beetrack = routeBeerouter("shortest.brf", waypoints)
        assertNotNull(beetrack)
        assertEquals(
            btrack.voiceHints.transportMode,
            beetrack.voiceHints.getTransportMode(),
            "shortest transport mode mismatch"
        )

        val expected = brouterHints(btrack)
        val actual = beerouterHints(beetrack)
        assertEquals(
            expected.size,
            actual.size,
            "shortest voice hint count mismatch\n" +
                    "missing=${expected.map { it.first }.toSet() - actual.map { it.first }.toSet()}\n" +
                    "extra=${actual.map { it.first }.toSet() - expected.map { it.first }.toSet()}\n" +
                    "firstDiff=${firstDiff(expected, actual)}\n" +
                    debugExtraHint(expected, actual, btrack, beetrack) + "\n" +
                    "firstNodeLinkdistDiff=${firstNodeLinkdistDiff(btrack, beetrack)}"
        )
    }

    @Test
    fun shortestLongVoiceHintSequenceParity() = runBlocking {
        val waypoints = listOf(
            waypoint("Stuttgart", 48.7758, 9.1829),
            waypoint("Balingen", 48.2768, 8.8554)
        )
        val btrack = routeBrouter("shortest.brf", waypoints)
        val beetrack = routeBeerouter("shortest.brf", waypoints)
        assertNotNull(beetrack)

        val expected = brouterHints(btrack)
        val actual = beerouterHints(beetrack)
        assertEquals(
            expected.size,
            actual.size,
            "shortest-long voice hint count mismatch\n" +
                    "missing=${expected.map { it.first }.toSet() - actual.map { it.first }.toSet()}\n" +
                    "extra=${actual.map { it.first }.toSet() - expected.map { it.first }.toSet()}\n" +
                    "firstDiff=${firstDiff(expected, actual)}\n" +
                    debugExtraHint(expected, actual, btrack, beetrack)
        )
    }

    @Test
    fun shortestInputSignatureParity() = runBlocking {
        val waypoints = listOf(
            waypoint("Reutlingen", 48.4914, 9.2043),
            waypoint("Tübingen", 48.5203263, 9.053596)
        )
        val btrack = routeBrouter("shortest.brf", waypoints)
        val beetrack = routeBeerouter("shortest.brf", waypoints)
        assertNotNull(beetrack)

        val bs = brouterInputSignatures(btrack)
        val asig = beerouterInputSignatures(beetrack)
        assertEquals(bs[444], asig[444], "shortest input signature mismatch at 444; firstNodePrioDiff=${firstNodePrioDiff(btrack, beetrack)}")
        assertEquals(bs[441], asig[441], "shortest input signature mismatch at 441")
    }

    private fun brouterHints(track: btools.router.OsmTrack): List<Pair<Int, String>> {
        return List(track.nodes.size) { idx ->
            track.getVoiceHint(idx)?.let { idx to it.cruiserCommandString }
        }.filterNotNull()
    }

    private fun beerouterHints(track: OsmTrack): List<Pair<Int, String>> {
        return track.voiceHints.map { it.indexInTrack to it.cruiserCommandString }
            .sortedBy { it.first }
    }

    private fun firstDiff(expected: List<Pair<Int, String>>, actual: List<Pair<Int, String>>): String {
        val max = minOf(expected.size, actual.size)
        for (i in 0 until max) {
            if (expected[i] != actual[i]) {
                return "i=$i expected=${expected[i]} actual=${actual[i]}"
            }
        }
        return "none"
    }

    private fun beerouterInputSignatures(track: OsmTrack): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        var nodeNr = track.nodes.size - 1
        var node: OsmPathElement? = track.nodes.getOrNull(nodeNr)
        while (node != null) node = node.origin
        node = track.nodes[nodeNr]
        while (node != null) {
            val origin = node.origin
            if (origin != null) {
                val idx = --nodeNr
                val good = node.message
                val old = origin.message ?: node.message
                val detourParts = mutableListOf<String>()
                var holder = track.getFromDetourMap(origin.idFromPos)
                while (holder != null) {
                    val msg = startSectionBeerouter(holder.node, origin)
                    if (msg != null) {
                        detourParts += "${msg.turnangle}/${msg.priorityclassifier}/${msg.classifiermask}"
                    }
                    holder = holder.nextHolder
                }
                out[idx] = "g=${good?.priorityclassifier}/${good?.classifiermask}/${good?.linkdist};" +
                        "o=${old?.priorityclassifier}/${old?.classifiermask}/${old?.linkdist};" +
                        "b=${detourParts.sorted()}"
            }
            node = origin
        }
        return out
    }

    private fun brouterInputSignatures(track: btools.router.OsmTrack): Map<Int, String> {
        val out = mutableMapOf<Int, String>()
        var nodeNr = track.nodes.size - 1
        var node: btools.router.OsmPathElement? = track.nodes[nodeNr]
        while (node != null) node = node.origin
        node = track.nodes[nodeNr]
        while (node != null) {
            val origin = node.origin
            if (origin != null) {
                val idx = --nodeNr
                val good = node.message
                val old = origin.message ?: node.message
                val detourParts = mutableListOf<String>()
                var holder = track.getFromDetourMap(origin.idFromPos)
                while (holder != null) {
                    val msg = startSectionBrouter(holder.node, origin)
                    if (msg != null) {
                        detourParts += "${getDeclaredFloat(msg, "turnangle")}/${getDeclaredInt(msg, "priorityclassifier")}/${getDeclaredInt(msg, "classifiermask")}"
                    }
                    holder = holder.nextHolder
                }
                out[idx] = "g=${good?.let { getDeclaredInt(it, "priorityclassifier") }}/${good?.let { getDeclaredInt(it, "classifiermask") }}/${good?.let { getDeclaredInt(it, "linkdist") }};" +
                        "o=${old?.let { getDeclaredInt(it, "priorityclassifier") }}/${old?.let { getDeclaredInt(it, "classifiermask") }}/${old?.let { getDeclaredInt(it, "linkdist") }};" +
                        "b=${detourParts.sorted()}"
            }
            node = origin
        }
        return out
    }

    private fun startSectionBeerouter(
        element: OsmPathElement?,
        root: OsmPathElement
    ): MessageData? {
        var e = element
        var cnt = 0
        val rootId = root.idFromPos
        while (e != null && e.origin != null) {
            if (e.origin!!.idFromPos == rootId) return e.message
            e = e.origin
            if (cnt++ > 1_000_000) break
        }
        return null
    }

    private fun startSectionBrouter(
        element: btools.router.OsmPathElement?,
        root: btools.router.OsmPathElement
    ): Any? {
        var e = element
        var cnt = 0
        val rootId = root.idFromPos
        while (e != null && e.origin != null) {
            if (e.origin.idFromPos == rootId) return e.message
            e = e.origin
            if (cnt++ > 1_000_000) break
        }
        return null
    }

    private fun debugExtraHint(
        expected: List<Pair<Int, String>>,
        actual: List<Pair<Int, String>>,
        btrack: btools.router.OsmTrack,
        beetrack: OsmTrack
    ): String {
        val extra = actual.map { it.first }.toSet() - expected.map { it.first }.toSet()
        val idx = extra.minOrNull() ?: return "extraHint=none"
        return "extraHint[$idx] brouter=${nodeDebug(btrack, idx)} beerouter=${nodeDebug(beetrack, idx)} " +
                "detours(brouter)=${brouterDetourStartAngles(btrack, idx)} detours(beerouter)=${beerouterDetourStartAngles(beetrack, idx)} " +
                "originMsg(brouter)=${brouterOriginMsgPrio(btrack, idx)} originMsg(beerouter)=${beerouterOriginMsgPrio(beetrack, idx)} " +
                "firstDistanceDrift=${firstDistanceDrift(btrack, beetrack)}"
    }

    private fun brouterOriginMsgPrio(track: btools.router.OsmTrack, idx: Int): String {
        val origin = track.nodes[idx].origin ?: return "null"
        val msg = origin.message ?: return "msg=null"
        return "prio=${getDeclaredInt(msg, "priorityclassifier")}, turn=${getDeclaredFloat(msg, "turnangle")}"
    }

    private fun beerouterOriginMsgPrio(track: OsmTrack, idx: Int): String {
        val origin = track.nodes[idx].origin ?: return "null"
        val msg = origin.message ?: return "msg=null"
        return "prio=${msg.priorityclassifier}, turn=${msg.turnangle}"
    }

    private fun firstDistanceDrift(btrack: btools.router.OsmTrack, beetrack: OsmTrack): String {
        val expected = brouterHints(btrack)
        val actual = beerouterHints(beetrack)
        val max = minOf(expected.size, actual.size)
        for (i in 0 until max) {
            if (expected[i] != actual[i]) break
            val idx = expected[i].first
            val bh = btrack.getVoiceHint(idx) ?: continue
            val ah = beetrack.getVoiceHint(idx) ?: continue
            val bd = getDeclaredDouble(bh, "distanceToNext")
            val ad = ah.distanceToNext
            if (kotlin.math.abs(bd - ad) > 1e-6) {
                return "i=$i idx=$idx brouter=$bd beerouter=$ad " +
                        "brouterHint=${nodeDebug(btrack, idx)} beerouterHint=${nodeDebug(beetrack, idx)}"
            }
        }
        return "none"
    }

    private fun firstNodeLinkdistDiff(btrack: btools.router.OsmTrack, track: OsmTrack): String {
        val max = minOf(btrack.nodes.size, track.nodes.size)
        for (i in 0 until max) {
            val bm = btrack.nodes[i].message ?: continue
            val am = track.nodes[i].message ?: continue
            val b = getDeclaredInt(bm, "linkdist")
            val a = am.linkdist
            if (b != a) {
                return "idx=$i b=$b a=$a"
            }
        }
        return "none"
    }

    private fun firstNodePrioDiff(btrack: btools.router.OsmTrack, track: OsmTrack): String {
        val max = minOf(btrack.nodes.size, track.nodes.size)
        for (i in 0 until max) {
            val bm = btrack.nodes[i].message ?: continue
            val am = track.nodes[i].message ?: continue
            val b = getDeclaredInt(bm, "priorityclassifier")
            val a = am.priorityclassifier
            if (b != a) {
                val bmask = getDeclaredInt(bm, "classifiermask")
                val amask = am.classifiermask
                val bway = getDeclaredObject(bm, "wayKeyValues")
                val away = am.wayTags
                val bnode = btrack.nodes[i]
                val anode = track.nodes[i]
                return "idx=$i b=$b/$bmask a=$a/$amask " +
                        "bnode=${bnode.iLon},${bnode.iLat} anode=${anode.position.longitude.toIntLongitude()},${anode.position.latitude.toIntLatitude()} " +
                        "bway=$bway away=$away"
            }
        }
        return "none"
    }

    private fun brouterDetourStartAngles(track: btools.router.OsmTrack, idx: Int): List<Float> {
        val root = track.nodes[idx]
        val rootId = root.idFromPos
        val angles = mutableListOf<Float>()
        var holder = track.getFromDetourMap(rootId)
        while (holder != null) {
            var e = holder.node
            var cnt = 0
            while (e != null && e.origin != null) {
                if (e.origin.idFromPos == rootId) {
                    val msg = e.message
                    if (msg != null) {
                        angles += getDeclaredFloat(msg, "turnangle")
                    }
                    break
                }
                e = e.origin
                if (cnt++ > 1_000_000) break
            }
            holder = holder.nextHolder
        }
        return angles
    }

    private fun beerouterDetourStartAngles(track: OsmTrack, idx: Int): List<Float> {
        val root = track.nodes[idx]
        val rootId = root.idFromPos
        val angles = mutableListOf<Float>()
        var holder = track.getFromDetourMap(rootId)
        while (holder != null) {
            var e = holder.node
            var cnt = 0
            while (e != null && e.origin != null) {
                if (e.origin!!.idFromPos == rootId) {
                    val msg = e.message
                    if (msg != null) {
                        angles += msg.turnangle
                    }
                    break
                }
                e = e.origin
                if (cnt++ > 1_000_000) break
            }
            holder = holder.nextHolder
        }
        return angles
    }

    private fun nodeDebug(track: OsmTrack, idx: Int): String {
        val msg = track.nodes[idx].message
        val hint = track.getVoiceHint(idx)
        return "hint=${hint?.cruiserCommandString}, angle=${hint?.angle}, lower=${hint?.lowerBadWayAngle}, higher=${hint?.higherBadWayAngle}, maxBadPrio=${hint?.maxBadPrio}, " +
                "turn=${msg?.turnangle}, prio=${msg?.priorityclassifier}, " +
                "mask=${msg?.classifiermask}, linkdist=${msg?.linkdist}, hintGoodPrio=${hint?.goodWay?.priorityclassifier}, " +
                "hintOldPrio=${hint?.oldWay?.priorityclassifier}, hintGoodDist=${hint?.goodWay?.linkdist}, hintOldDist=${hint?.oldWay?.linkdist}, " +
                "badWays=${hint?.badWays?.map { "a=${it.turnangle},p=${it.priorityclassifier},m=${it.classifiermask}" }}"
    }

    private fun nodeDebug(track: btools.router.OsmTrack, idx: Int): String {
        val msg = track.nodes[idx].message
        val turn = msg?.let { getDeclaredFloat(it, "turnangle") }
        val prio = msg?.let { getDeclaredInt(it, "priorityclassifier") }
        val mask = msg?.let { getDeclaredInt(it, "classifiermask") }
        val linkdist = msg?.let { getDeclaredInt(it, "linkdist") }
        val hint = track.getVoiceHint(idx)
        if (hint == null) {
            return "hint=null, turn=$turn, prio=$prio, mask=$mask, linkdist=$linkdist"
        }
        val angle = getDeclaredFloat(hint, "angle")
        val lower = getDeclaredFloat(hint, "lowerBadWayAngle")
        val higher = getDeclaredFloat(hint, "higherBadWayAngle")
        val maxBadPrio = getDeclaredInt(hint, "maxBadPrio")
        val badWays = getBadWayAngles(hint)
        val badWayMasks = getBadWayMasks(hint)
        val goodWay = getDeclaredObject(hint, "goodWay")
        val oldWay = getDeclaredObject(hint, "oldWay")
        val hintGoodPrio = goodWay?.let { getDeclaredInt(it, "priorityclassifier") }
        val hintOldPrio = oldWay?.let { getDeclaredInt(it, "priorityclassifier") }
        val hintGoodDist = goodWay?.let { getDeclaredInt(it, "linkdist") }
        val hintOldDist = oldWay?.let { getDeclaredInt(it, "linkdist") }
        return "hint=${hint.cruiserCommandString}, angle=$angle, lower=$lower, higher=$higher, maxBadPrio=$maxBadPrio, " +
                "turn=$turn, prio=$prio, mask=$mask, linkdist=$linkdist, " +
                "hintGoodPrio=$hintGoodPrio, hintOldPrio=$hintOldPrio, hintGoodDist=$hintGoodDist, hintOldDist=$hintOldDist, " +
                "badWays=$badWays, badWayMasks=$badWayMasks"
    }

    private fun getDeclaredFloat(target: Any, field: String): Float {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getFloat(target)
    }

    private fun getDeclaredInt(target: Any, field: String): Int {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getInt(target)
    }

    private fun getDeclaredDouble(target: Any, field: String): Double {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getDouble(target)
    }

    private fun getDeclaredObject(target: Any, field: String): Any? {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.get(target)
    }

    private fun getBadWayAngles(hint: Any): List<Float> {
        val badWaysField = hint.javaClass.getDeclaredField("badWays")
        badWaysField.isAccessible = true
        val badWays = badWaysField.get(hint) as? List<*> ?: return emptyList()
        return badWays.mapNotNull { badWay ->
            try {
                val f = badWay!!.javaClass.getDeclaredField("turnangle")
                f.isAccessible = true
                f.getFloat(badWay)
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun getBadWayMasks(hint: Any): List<Int> {
        val badWaysField = hint.javaClass.getDeclaredField("badWays")
        badWaysField.isAccessible = true
        val badWays = badWaysField.get(hint) as? List<*> ?: return emptyList()
        return badWays.mapNotNull { badWay ->
            try {
                val f = badWay!!.javaClass.getDeclaredField("classifiermask")
                f.isAccessible = true
                f.getInt(badWay)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

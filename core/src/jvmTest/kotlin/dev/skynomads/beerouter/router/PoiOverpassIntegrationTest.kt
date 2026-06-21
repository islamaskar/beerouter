package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.DefaultMapSource
import dev.skynomads.beerouter.map.PoiFinder
import dev.skynomads.beerouter.map.PoiQuery
import kotlinx.io.buffered
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PoiOverpassIntegrationTest {
    private companion object {
        const val EXPECTED_TOILET_COUNT = 10
    }

    @BeforeTest
    fun before() {
        ensureGeneratedTestSegmentDir()
    }

    @Test
    fun toiletsAroundReutlingenAreComparableToOverpass() {
        val bbox = BoundingBox(
            south = 48.4550,
            west = 9.1400,
            north = 48.5450,
            east = 9.2800,
        )
        val segmentDir = generatedTestSegmentDir
        val lookupContent = SystemFileSystem.source(lookupPathForSegments(segmentDir)).buffered()
            .use { it.readString() }
        val finder = PoiFinder(
            mapSource = DefaultMapSource(segmentDir),
            lookupContent = lookupContent,
        )
        // Filter matches the pois.conf rule: amenity=toilets[access=yes]
        // (only access=yes toilets are indexed in the rd5)
        val beerouterCount = finder.find(
            PoiQuery(
                minLon = Position(bbox.west, bbox.south).longitude,
                minLat = Position(bbox.west, bbox.south).latitude,
                maxLon = Position(bbox.east, bbox.north).longitude,
                maxLat = Position(bbox.east, bbox.north).latitude,
                requiredTags = mapOf("amenity" to setOf("toilets"), "access" to setOf("yes")),
            ),
        ).size
        finder.close()

        val overpassCount = fetchOverpassToiletCount(bbox)
        assertEquals(
            EXPECTED_TOILET_COUNT,
            beerouterCount,
            "unexpected toilet count in Beerouter rd5 around Reutlingen",
        )
        assertEquals(
            EXPECTED_TOILET_COUNT,
            overpassCount,
            "unexpected toilet count from Overpass around Reutlingen",
        )
        assertEquals(
            overpassCount,
            beerouterCount,
            "Beerouter and Overpass toilet counts diverged around Reutlingen",
        )
    }

    private fun fetchOverpassToiletCount(bbox: BoundingBox): Int {
        val query = """
            [out:json][timeout:25];
            node["amenity"="toilets"]["access"="yes"](${bbox.south},${bbox.west},${bbox.north},${bbox.east});
            out ids;
        """.trimIndent()
        val requestBody = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
        val request = HttpRequest.newBuilder(URI("https://overpass-api.de/api/interpreter"))
            .timeout(Duration.ofSeconds(40))
            .header("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        val client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "overpass request failed with HTTP ${response.statusCode()}: ${
                response.body().take(200)
            }"
        }
        return "\"type\"\\s*:\\s*\"node\"".toRegex().findAll(response.body()).count()
    }

    private data class BoundingBox(
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
    )
}

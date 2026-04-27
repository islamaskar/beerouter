package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.geo.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class OsmNodeTest {
    @Test
    fun constructorStoresElevationOutsidePosition() {
        val node = OsmNode(Position(123, 456, 789))

        assertEquals(789.toShort(), node.altitude)
        assertEquals(Position(123, 456), node.position)
    }
}

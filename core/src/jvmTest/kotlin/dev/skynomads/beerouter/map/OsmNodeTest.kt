package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.geo.UNSET_ELEVATION
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class OsmNodeTest {
    @Test
    fun constructorStoresElevationOutsideBasePosition() {
        val node = OsmNode(Position(123, 456, 789))

        assertEquals(789.toShort(), node.altitude)
        assertEquals(Position(123, 456), node.position)
    }

    @Test
    fun positionWithAltitudeReusesCachedAltitudePosition() {
        val node = OsmNode(Position(123, 456))

        node.altitude = 789

        val first = node.positionWithAltitude()
        val second = node.positionWithAltitude()

        assertEquals(Position(123, 456, 789), first)
        assertSame(first, second)
    }

    @Test
    fun constructorReusesPositionWhenAltitudeIsUnset() {
        val position = Position(123, 456, UNSET_ELEVATION)

        val node = OsmNode(position)

        assertSame(position, node.position)
    }

    @Test
    fun addLinkByCoordinatesReusesExistingHollowNode() {
        val source = OsmNode(Position(1, 2))
        val target = OsmNode(Position(3, 4)).apply { setHollow() }
        val hollowNodes = OsmNodesMap()
        hollowNodes.put(target)
        val description = byteArrayOf(1, 2, 3)

        source.addLink(3, 4, description, null, hollowNodes, false)

        val link = source.firstlink!!
        assertSame(target, link.getTarget(source))
        assertSame(description, link.descriptionBitmap)
    }

    @Test
    fun coordinateAccessorsTrackPositionChanges() {
        val node = OsmNode(1, 2)

        assertEquals(1, node.longitude)
        assertEquals(2, node.latitude)
        assertEquals(Position(1, 2).id, node.idFromPos)

        node.position = Position(3, 4)

        assertEquals(3, node.longitude)
        assertEquals(4, node.latitude)
        assertEquals(Position(3, 4).id, node.idFromPos)
    }

    @Test
    fun positionGetterReturnsCachedPositionView() {
        val node = OsmNode(1, 2)

        val first = node.position
        val second = node.position

        assertEquals(Position(1, 2), first)
        assertSame(first, second)

        node.position = Position(3, 4)

        assertEquals(Position(3, 4), node.position)
    }

    @Test
    fun positionSetterKeepsBasePositionAltitudeFree() {
        val node = OsmNode()

        node.position = Position(3, 4, 9)
        node.altitude = 12

        assertEquals(Position(3, 4), node.position)
        assertEquals(Position(3, 4, 12), node.positionWithAltitude())
    }
}

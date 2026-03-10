package dev.skynomads.beerouter.router

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutingContextDefaultsTest {
    private fun loadContext(profileName: String): RoutingContext {
        val profile = profilePath(profileName)
        return routingContextFromFiles(profile, requireNotNull(profile.parent))
    }

    @Test
    fun shortestProfileDisablesRoundaboutInstructionsByDefault() {
        val context = loadContext("shortest.brf")

        assertTrue(context.global.footMode)
        assertFalse(context.global.turnInstructionRoundabouts)
    }

    @Test
    fun fastbikeProfileKeepsRoundaboutInstructionsByDefault() {
        val context = loadContext("fastbike.brf")

        assertFalse(context.global.footMode)
        assertTrue(context.global.turnInstructionRoundabouts)
    }
}

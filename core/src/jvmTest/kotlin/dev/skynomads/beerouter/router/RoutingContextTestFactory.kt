package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.DefaultMapAccessFileSystem
import java.io.File
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal fun routingContextFromFiles(profile: File, segmentDir: File): RoutingContext {
    require(profile.exists()) { "profile exists ${profile.path}" }
    val lookup = File(profile.parentFile, "lookups.dat")
    require(lookup.exists()) { "lookup exists ${lookup.path}" }
    return SystemFileSystem.source(Path(profile.path)).buffered().use { profileSource ->
        SystemFileSystem.source(Path(lookup.path)).buffered().use { lookupSource ->
            RoutingContext(
                profileName = profile.name,
                profileSource = profileSource,
                lookupSource = lookupSource,
                mapAccessFileSystem = DefaultMapAccessFileSystem(Path(segmentDir.path))
            )
        }
    }
}

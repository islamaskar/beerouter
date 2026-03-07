package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.DefaultMapAccessFileSystem
import java.io.File
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal fun routingContextFromFiles(profile: File, segmentDir: File): RoutingContext {
    return routingContextFromFiles(Path(profile.path), Path(segmentDir.path))
}

internal fun routingContextFromFiles(profile: Path, segmentDir: Path): RoutingContext {
    require(SystemFileSystem.exists(profile)) { "profile exists $profile" }
    val lookup = Path(profile.parent ?: error("profile has no parent: $profile"), "lookups.dat")
    require(SystemFileSystem.exists(lookup)) { "lookup exists $lookup" }
    return SystemFileSystem.source(profile).buffered().use { profileSource ->
        SystemFileSystem.source(lookup).buffered().use { lookupSource ->
            RoutingContext(
                profileName = profile.name,
                profileSource = profileSource,
                lookupSource = lookupSource,
                mapAccessFileSystem = DefaultMapAccessFileSystem(segmentDir)
            )
        }
    }
}

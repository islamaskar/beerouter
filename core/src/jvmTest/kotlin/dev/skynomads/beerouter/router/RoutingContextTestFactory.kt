package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.DefaultMapAccessFileSystem
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

internal fun routingContextFromFiles(profile: Path, segmentDir: Path): RoutingContext {
    require(SystemFileSystem.exists(profile)) { "profile exists $profile" }
    val lookup = Path(profile.parent ?: error("profile has no parent: $profile"), "lookups.dat")
    require(SystemFileSystem.exists(lookup)) { "lookup exists $lookup" }
    return SystemFileSystem.source(profile).buffered().use { profileSource ->
        SystemFileSystem.source(lookup).buffered().use { lookupSource ->
            RoutingContext(
                profileContent = profileSource.readString(),
                lookupContent = lookupSource.readString(),
                mapAccessFileSystem = DefaultMapAccessFileSystem(segmentDir)
            )
        }
    }
}

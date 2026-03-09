package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.geo.Position

public data class PoiResult(
    val nodeId: Long,
    val position: Position,
    val tags: Map<String, String>,
    val sourceFile: String?,
)

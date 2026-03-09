package dev.skynomads.beerouter.mapcreator

public data class PoiRule(
    val key: String,
    val value: String,
) {
    public fun matches(tags: Map<String, String>): Boolean = tags[key] == value
}

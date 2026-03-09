package dev.skynomads.beerouter.map.generator

public data class PoiRule(
    val key: String,
    val value: String,
) {
    public fun matches(tags: Map<String, String>): Boolean = tags[key] == value
}

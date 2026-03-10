package dev.skynomads.beerouter.map.generator

public data class PoiRule(
    public val key: String,
    public val value: String,
) {
    public fun matches(tags: Map<String, String>): Boolean = tags[key] == value
}

package dev.skynomads.beerouter.map.generator

public object PoiConfigParser {
    public fun parse(content: String): PoiGenerationConfig {
        val rules = mutableListOf<PoiRule>()
        for ((index, rawLine) in content.lineSequence().withIndex()) {
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) {
                continue
            }
            val separator = line.indexOf('=')
            require(separator > 0 && separator < line.lastIndex) {
                "invalid POI rule on line ${index + 1}: $rawLine"
            }
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim()
            require(key.isNotEmpty() && value.isNotEmpty()) {
                "invalid POI rule on line ${index + 1}: $rawLine"
            }
            require(value != "*") {
                "wildcard POI rules are not supported by lookup-driven rd5 encoding: $rawLine"
            }
            rules += PoiRule(key, value)
        }
        return PoiGenerationConfig(rules.distinct())
    }
}

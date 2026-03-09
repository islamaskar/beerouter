package dev.skynomads.beerouter.mapcreator

public data class PoiGenerationConfig(
    val rules: List<PoiRule>,
) {
    public val isEmpty: Boolean
        get() = rules.isEmpty()

    public fun matchingRules(tags: Map<String, String>): List<PoiRule> = rules.filter { it.matches(tags) }

    public companion object {
        public val EMPTY: PoiGenerationConfig = PoiGenerationConfig(emptyList())
    }
}

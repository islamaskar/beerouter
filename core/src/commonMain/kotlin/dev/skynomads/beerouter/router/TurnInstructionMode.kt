package dev.skynomads.beerouter.router

public enum class TurnInstructionMode(public val id: Int) {
    NONE(0),
    AUTO(1),
    LOCUS(2),
    OSMAND(3),
    COMMENT_STYLE(4),
    GPSIES_STYLE(5),
    TRKPT_STYLE(8),
    LOCUS_TRKPT_STYLE(9);

    public companion object {
        public fun fromId(id: Int): TurnInstructionMode? =
            entries.firstOrNull { it.id == id }

        public fun supportsGuideTrackDetours(id: Int): Boolean = id > NONE.id

        public fun supportsBeelineHints(id: Int): Boolean =
            id == TRKPT_STYLE.id ||
                id == COMMENT_STYLE.id ||
                id == LOCUS.id ||
                id == LOCUS_TRKPT_STYLE.id

        public fun usesExplicitExitCommands(id: Int): Boolean =
            id == LOCUS.id || id == LOCUS_TRKPT_STYLE.id
    }
}

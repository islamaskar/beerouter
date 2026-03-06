/**
 * Container for a voice hint
 * (both input- and result data for voice hint processing)
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

public class VoiceHintList(
    public val list: MutableList<VoiceHint> = ArrayList()
) : MutableList<VoiceHint> by list {
    public var transportMode: Int = TRANS_MODE_BIKE
        private set

    public var turnInstructionMode: Int = 0

    public fun setTransportMode(isCar: Boolean, isBike: Boolean) {
        transportMode =
            if (isCar) TRANS_MODE_CAR else (if (isBike) TRANS_MODE_BIKE else TRANS_MODE_FOOT)
    }

    public fun getTransportMode(): String {
        return when (transportMode) {
            TRANS_MODE_FOOT -> "foot"
            TRANS_MODE_CAR -> "car"
            TRANS_MODE_BIKE -> "bike"
            else -> "bike"
        }
    }

    public val locusRouteType: Int
        get() {
            if (transportMode == TRANS_MODE_CAR) {
                return 0
            }
            if (transportMode == TRANS_MODE_BIKE) {
                return 5
            }
            return 3 // foot
        }

    public companion object {
        public const val TRANS_MODE_NONE: Int = 0
        public const val TRANS_MODE_FOOT: Int = 1
        public const val TRANS_MODE_BIKE: Int = 2
        public const val TRANS_MODE_CAR: Int = 3
    }
}

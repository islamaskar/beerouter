/**
 * Container for a turn restriction
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

public class TurnRestriction {
    public var isPositive: Boolean = false
    public var exceptions: Short = 0

    public var fromLon: Int = 0
    public var fromLat: Int = 0

    public var toLon: Int = 0
    public var toLat: Int = 0

    public var next: TurnRestriction? = null

    public fun exceptBikes(): Boolean {
        return (exceptions.toInt() and 1) != 0
    }

    public fun exceptMotorcars(): Boolean {
        return (exceptions.toInt() and 2) != 0
    }

    override fun toString(): String {
        return "pos=$isPositive fromLon=$fromLon fromLat=$fromLat toLon=$toLon toLat=$toLat"
    }

    public companion object {
        @JvmStatic
        public fun isTurnForbidden(
            first: TurnRestriction?,
            fromLon: Int,
            fromLat: Int,
            toLon: Int,
            toLat: Int,
            bikeMode: Boolean,
            carMode: Boolean
        ): Boolean {
            var hasAnyPositive = false
            var hasPositive = false
            var hasNegative = false
            var tr = first
            while (tr != null) {
                if ((tr.exceptBikes() && bikeMode) || (tr.exceptMotorcars() && carMode)) {
                    tr = tr.next
                    continue
                }
                if (tr.fromLon == fromLon && tr.fromLat == fromLat) {
                    if (tr.isPositive) {
                        hasAnyPositive = true
                    }
                    if (tr.toLon == toLon && tr.toLat == toLat) {
                        if (tr.isPositive) {
                            hasPositive = true
                        } else {
                            hasNegative = true
                        }
                    }
                }
                tr = tr.next
            }
            return !hasPositive && (hasAnyPositive || hasNegative)
        }
    }
}

/**
 * Container for a turn restriction
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

public data class TurnRestriction(
    public var isPositive: Boolean = false,
    public var exceptions: Short = 0,
    public var fromLon: Int = 0,
    public var fromLat: Int = 0,
    public var toLon: Int = 0,
    public var toLat: Int = 0,
    public var next: TurnRestriction? = null
) {
    public enum class RestrictionException(public val bit: Int) {
        BICYCLES(1),
        MOTORCARS(2)
    }

    private fun hasException(exception: RestrictionException): Boolean =
        (exceptions.toInt() and exception.bit) != 0

    public fun exceptBikes(): Boolean {
        return hasException(RestrictionException.BICYCLES)
    }

    public fun exceptMotorcars(): Boolean {
        return hasException(RestrictionException.MOTORCARS)
    }

    override fun toString(): String {
        return "pos=$isPositive fromLon=$fromLon fromLat=$fromLat toLon=$toLon toLat=$toLat"
    }

    public companion object {
        public fun isTurnForbidden(
            first: TurnRestriction?,
            fromLon: Int,
            fromLat: Int,
            toLon: Int,
            toLat: Int,
            bikeMode: Boolean,
            carMode: Boolean
        ): Boolean {
            val matchingRestrictions = generateSequence(first) { it.next }
                .filterNot { (it.exceptBikes() && bikeMode) || (it.exceptMotorcars() && carMode) }
                .filter { it.fromLon == fromLon && it.fromLat == fromLat }
                .toList()

            val hasAnyPositive = matchingRestrictions.any(TurnRestriction::isPositive)
            val hasPositive = matchingRestrictions.any {
                it.isPositive && it.toLon == toLon && it.toLat == toLat
            }
            val hasNegative = matchingRestrictions.any {
                !it.isPositive && it.toLon == toLon && it.toLat == toLat
            }

            return !hasPositive && (hasAnyPositive || hasNegative)
        }
    }
}

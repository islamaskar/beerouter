package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.expressions.BExpressionContext
import kotlin.math.abs

public class AreaInfo(public var direction: Int) {
    public var numForest: Int = -1
    public var numRiver: Int = -1

    public var polygon: OsmNogoPolygon? = null

    public var ways: Int = 0
    public var greenWays: Int = 0
    public var riverWays: Int = 0
    public var elevStart: Double = 0.0
    public var elev50: Int = 0

    public fun checkAreaInfo(expctxWay: BExpressionContext, elev: Double, ab: ByteArray) {
        ways++

        val test = elevStart - elev
        if (abs(test) < 50) elev50++

        val ld2 = expctxWay.createNewLookupData()
        expctxWay.decode(ld2!!, false, ab)

        if (numForest != -1 && ld2[numForest] > 1) {
            greenWays++
        }

        if (numRiver != -1 && ld2[numRiver] > 1) {
            riverWays++
        }
    }

    public val elev50Weight: Int
        get() {
            if (ways == 0) return 0
            return (elev50 * 100.0 / ways).toInt()
        }

    public val green: Int
        get() {
            if (ways == 0) return 0
            return (greenWays * 100.0 / ways).toInt()
        }

    public val river: Int
        get() {
            if (ways == 0) return 0
            return (riverWays * 100.0 / ways).toInt()
        }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("Area ").append(direction).append(" ").append(elevStart).append("m ways ")
            .append(ways)
        if (ways > 0) {
            sb.append("\nArea ways <50m  ").append(elev50).append(" ").append(this.elev50Weight)
                .append("%")
            sb.append("\nArea ways green ").append(greenWays).append(" ").append(this.green)
                .append("%")
            sb.append("\nArea ways river ").append(riverWays).append(" ").append(this.river)
                .append("%")
        }
        return sb.toString()
    }

    public companion object {
        public const val RESULT_TYPE_NONE: Int = 0
        public const val RESULT_TYPE_ELEV50: Int = 1
        public const val RESULT_TYPE_GREEN: Int = 4
        public const val RESULT_TYPE_RIVER: Int = 5
    }
}

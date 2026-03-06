/**
 * Container for a voice hint
 * (both input- and result data for voice hint processing)
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import kotlin.math.abs

public class VoiceHint {
    public var ilon: Int = 0
    public var ilat: Int = 0
    public var selev: Short = 0
    public var command: Int = 0
    public var oldWay: MessageData? = null
    public var goodWay: MessageData? = null
    public var badWays: MutableList<MessageData>? = null
    public var distanceToNext: Double = 0.0
    public var indexInTrack: Int = 0

    public val time: Float
        get() = if (oldWay == null) 0f else oldWay!!.time

    public var angle: Float = Float.MAX_VALUE
    public var lowerBadWayAngle: Float = -181f
    public var higherBadWayAngle: Float = 181f

    public var turnAngleConsumed: Boolean = false
    public var needsRealTurn: Boolean = false
    public var maxBadPrio: Int = -1

    public var exitNumber: Int = 0

    public val isRoundabout: Boolean
        get() = this.exitNumber != 0

    public fun addBadWay(badWay: MessageData?) {
        if (badWay == null) {
            return
        }
        if (badWays == null) {
            badWays = ArrayList()
        }
        badWays!!.add(badWay)
    }

    public fun getJsonCommandIndex(timode: Int): Int {
        when (this.command) {
            TLU -> return 10
            TU -> return 15
            TSHL -> return 4
            TL -> return 2
            TSLL -> return 3
            KL -> return 8
            C -> return 1
            KR -> return 9
            TSLR -> return 6
            TR -> return 5
            TSHR -> return 7
            TRU -> return 11
            RNDB -> return 13
            RNLB -> return 14
            BL -> return 16
            EL -> return if (timode == 2 || timode == 9) 17 else 8
            ER -> return if (timode == 2 || timode == 9) 18 else 9
            OFFR -> return 12
            else -> throw IllegalArgumentException("unknown command: " + this.command)
        }
    }

    /*
   * used by comment style, osmand style
   */
    public fun getCommandString(timode: Int): String {
        when (this.command) {
            TLU -> return "TU" // should be changed to TLU when osmand uses new voice hint constants
            TU -> return "TU"
            TSHL -> return "TSHL"
            TL -> return "TL"
            TSLL -> return "TSLL"
            KL -> return "KL"
            C -> return "C"
            KR -> return "KR"
            TSLR -> return "TSLR"
            TR -> return "TR"
            TSHR -> return "TSHR"
            TRU -> return "TRU"
            RNDB -> return "RNDB" + this.exitNumber
            RNLB -> return "RNLB" + (-this.exitNumber)
            BL -> return "BL"
            EL -> return if (timode == 2 || timode == 9) "EL" else "KL"
            ER -> return if (timode == 2 || timode == 9) "ER" else "KR"
            OFFR -> return "OFFR"
            END -> return "END"
            else -> throw IllegalArgumentException("unknown command: " + this.command)
        }
    }

    /*
   * used by trkpt/sym style
   */
    public fun getCommandString(c: Int, timode: Int): String {
        when (c) {
            TLU -> return "TLU"
            TU -> return "TU"
            TSHL -> return "TSHL"
            TL -> return "TL"
            TSLL -> return "TSLL"
            KL -> return "KL"
            C -> return "C"
            KR -> return "KR"
            TSLR -> return "TSLR"
            TR -> return "TR"
            TSHR -> return "TSHR"
            TRU -> return "TRU"
            RNDB -> return "RNDB" + this.exitNumber
            RNLB -> return "RNLB" + (-this.exitNumber)
            BL -> return "BL"
            EL -> return if (timode == 2 || timode == 9) "EL" else "KL"
            ER -> return if (timode == 2 || timode == 9) "ER" else "KR"
            OFFR -> return "OFFR"
            else -> return "unknown command: $c"
        }
    }

    /*
   * used by gpsies style
   */
    public fun getSymbolString(timode: Int): String {
        when (this.command) {
            TLU -> return "TU"
            TU -> return "TU"
            TSHL -> return "TSHL"
            TL -> return "Left"
            TSLL -> return "TSLL"
            KL -> return "TSLL" // ?
            C -> return "Straight"
            KR -> return "TSLR" // ?
            TSLR -> return "TSLR"
            TR -> return "Right"
            TSHR -> return "TSHR"
            TRU -> return "TU"
            RNDB -> return "RNDB" + this.exitNumber
            RNLB -> return "RNLB" + (-this.exitNumber)
            BL -> return "BL"
            EL -> return if (timode == 2 || timode == 9) "EL" else "KL"
            ER -> return if (timode == 2 || timode == 9) "ER" else "KR"
            OFFR -> return "OFFR"
            else -> throw IllegalArgumentException("unknown command: " + this.command)
        }
    }

    public val locusSymbolString: String
        /*
           * used by new locus trkpt style
           */
        get() {
            when (this.command) {
                TLU -> return "u-turn_left"
                TU -> return "u-turn"
                TSHL -> return "left_sharp"
                TL -> return "left"
                TSLL -> return "left_slight"
                KL -> return "stay_left" // ?
                C -> return "straight"
                KR -> return "stay_right" // ?
                TSLR -> return "right_slight"
                TR -> return "right"
                TSHR -> return "right_sharp"
                TRU -> return "u-turn_right"
                RNDB -> return "roundabout_e" + this.exitNumber
                RNLB -> return "roundabout_e" + (-this.exitNumber)
                BL -> return "beeline"
                EL -> return "exit_left"
                ER -> return "exit_right"
                else -> throw IllegalArgumentException("unknown command: " + this.command)
            }
        }

    /*
  * used by osmand style
  */
    public fun getMessageString(timode: Int): String {
        when (this.command) {
            TLU -> return "u-turn" // should be changed to u-turn-left when osmand uses new voice hint constants
            TU -> return "u-turn"
            TSHL -> return "sharp left"
            TL -> return "left"
            TSLL -> return "slight left"
            KL -> return "keep left"
            C -> return "straight"
            KR -> return "keep right"
            TSLR -> return "slight right"
            TR -> return "right"
            TSHR -> return "sharp right"
            TRU -> return "u-turn" // should be changed to u-turn-right when osmand uses new voice hint constants
            RNDB -> return "Take exit " + this.exitNumber
            RNLB -> return "Take exit " + (-this.exitNumber)
            EL -> return if (timode == 2 || timode == 9) "exit left" else "keep left"
            ER -> return if (timode == 2 || timode == 9) "exit right" else "keep right"
            else -> throw IllegalArgumentException("unknown command: " + this.command)
        }
    }

    public val locusAction: Int
        /*
           * used by old locus style
           */
        get() {
            when (this.command) {
                TLU -> return 13
                TU -> return 12
                TSHL -> return 5
                TL -> return 4
                TSLL -> return 3
                KL -> return 9 // ?
                C -> return 1
                KR -> return 10 // ?
                TSLR -> return 6
                TR -> return 7
                TSHR -> return 8
                TRU -> return 14
                RNDB -> return 26 + this.exitNumber
                RNLB -> return 26 - this.exitNumber
                EL -> return 9
                ER -> return 10
                else -> throw IllegalArgumentException("unknown command: " + this.command)
            }
        }

    public val oruxAction: Int
        /*
           * used by orux style
           */
        get() {
            when (this.command) {
                TLU -> return 1003
                TU -> return 1003
                TSHL -> return 1019
                TL -> return 1000
                TSLL -> return 1017
                KL -> return 1015 // ?
                C -> return 1002
                KR -> return 1014 // ?
                TSLR -> return 1016
                TR -> return 1001
                TSHR -> return 1018
                TRU -> return 1003
                RNDB -> return 1008 + this.exitNumber
                RNLB -> return 1008 + this.exitNumber
                EL -> return 1015
                ER -> return 1014
                else -> throw IllegalArgumentException("unknown command: " + this.command)
            }
        }

    public val cruiserCommandString: String
        /*
           * used by cruiser, equivalent to getCommandString() - osmand style - when osmand changes the voice hint  constants
           */
        get() {
            when (this.command) {
                TLU -> return "TLU"
                TU -> return "TU"
                TSHL -> return "TSHL"
                TL -> return "TL"
                TSLL -> return "TSLL"
                KL -> return "KL"
                C -> return "C"
                KR -> return "KR"
                TSLR -> return "TSLR"
                TR -> return "TR"
                TSHR -> return "TSHR"
                TRU -> return "TRU"
                RNDB -> return "RNDB" + this.exitNumber
                RNLB -> return "RNLB" + (-this.exitNumber)
                BL -> return "BL"
                EL -> return "EL"
                ER -> return "ER"
                OFFR -> return "OFFR"
                else -> throw IllegalArgumentException("unknown command: " + this.command)
            }
        }

    public val cruiserMessageString: String
        /*
           * used by cruiser, equivalent to getMessageString() - osmand style - when osmand changes the voice hint  constants
           */
        get() {
            when (this.command) {
                TLU -> return "u-turn left"
                TU -> return "u-turn"
                TSHL -> return "sharp left"
                TL -> return "left"
                TSLL -> return "slight left"
                KL -> return "keep left"
                C -> return "straight"
                KR -> return "keep right"
                TSLR -> return "slight right"
                TR -> return "right"
                TSHR -> return "sharp right"
                TRU -> return "u-turn right"
                RNDB -> return "take exit " + this.exitNumber
                RNLB -> return "take exit " + (-this.exitNumber)
                BL -> return "beeline"
                EL -> return "exit left"
                ER -> return "exit right"
                OFFR -> return "offroad"
                else -> throw IllegalArgumentException("unknown command: " + this.command)
            }
        }

    public fun calcCommand() {
        if (badWays != null) {
            for (badWay in badWays) {
                if (badWay.isBadOneway) {
                    continue
                }
                if (lowerBadWayAngle < badWay.turnangle && badWay.turnangle < goodWay!!.turnangle) {
                    lowerBadWayAngle = badWay.turnangle
                }
                if (higherBadWayAngle > badWay.turnangle && badWay.turnangle > goodWay!!.turnangle) {
                    higherBadWayAngle = badWay.turnangle
                }
            }
        }

        var cmdAngle = angle

        // fall back to local angle if otherwise inconsistent
        //if ( lowerBadWayAngle > angle || higherBadWayAngle < angle )
        //{
        //cmdAngle = goodWay.turnangle;
        //}
        if (angle == Float.MAX_VALUE) {
            cmdAngle = goodWay!!.turnangle
        }
        if (this.command == BL) return

        if (this.exitNumber > 0) {
            this.command = RNDB
        } else if (this.exitNumber < 0) {
            this.command = RNLB
        } else if (is180DegAngle(cmdAngle) && cmdAngle <= -179f && higherBadWayAngle == 181f && lowerBadWayAngle == -181f) {
            this.command = TU
        } else if (cmdAngle < -159f) {
            this.command = TLU
        } else if (cmdAngle < -135f) {
            this.command = TSHL
        } else if (cmdAngle < -45f) {
            // a TL can be pushed in either direction by a close-by alternative
            if (cmdAngle < -95f && higherBadWayAngle < -30f && lowerBadWayAngle < -180f) {
                this.command = TSHL
            } else if (cmdAngle > -85f && lowerBadWayAngle > -180f && higherBadWayAngle > -10f) {
                this.command = TSLL
            } else {
                if (cmdAngle < -110f) {
                    this.command = TSHL
                } else if (cmdAngle > -60f) {
                    this.command = TSLL
                } else {
                    this.command = TL
                }
            }
        } else if (cmdAngle < -21f) {
            if (this.command != KR) { // don't overwrite KR with TSLL
                this.command = TSLL
            }
        } else if (cmdAngle < -5f) {
            if (lowerBadWayAngle < -100f && higherBadWayAngle < 45f) {
                this.command = TSLL
            } else if (lowerBadWayAngle >= -100f && higherBadWayAngle < 45f) {
                this.command = KL
            } else {
                if (lowerBadWayAngle > -35f && higherBadWayAngle > 55f) {
                    this.command = KR
                } else {
                    this.command = C
                }
            }
        } else if (cmdAngle < 5f) {
            if (lowerBadWayAngle > -30f) {
                this.command = KR
            } else if (higherBadWayAngle < 30f) {
                this.command = KL
            } else {
                this.command = C
            }
        } else if (cmdAngle < 21f) {
            // a TR can be pushed in either direction by a close-by alternative
            if (lowerBadWayAngle > -45f && higherBadWayAngle > 100f) {
                this.command = TSLR
            } else if (lowerBadWayAngle > -45f && higherBadWayAngle <= 100f) {
                this.command = KR
            } else {
                if (lowerBadWayAngle < -55f && higherBadWayAngle < 35f) {
                    this.command = KL
                } else {
                    this.command = C
                }
            }
        } else if (cmdAngle < 45f) {
            this.command = TSLR
        } else if (cmdAngle < 135f) {
            if (cmdAngle < 85f && higherBadWayAngle < 180f && lowerBadWayAngle < 10f) {
                this.command = TSLR
            } else if (cmdAngle > 95f && lowerBadWayAngle > 30f && higherBadWayAngle > 180f) {
                this.command = TSHR
            } else {
                if (cmdAngle > 110.0) {
                    this.command = TSHR
                } else if (cmdAngle < 60.0) {
                    this.command = TSLR
                } else {
                    this.command = TR
                }
            }
        } else if (cmdAngle < 159f) {
            this.command = TSHR
        } else if (is180DegAngle(cmdAngle) && cmdAngle >= 179f && higherBadWayAngle == 181f && lowerBadWayAngle == -181f) {
            this.command = TU
        } else {
            this.command = TRU
        }
    }

    public fun formatGeometry(): String {
        val oldPrio = if (oldWay == null) 0f else oldWay!!.priorityclassifier.toFloat()
        val sb = StringBuilder(30)
        sb.append(' ').append(oldPrio.toInt())
        appendTurnGeometry(sb, goodWay!!)
        if (badWays != null) {
            for (badWay in badWays) {
                sb.append(" ")
                appendTurnGeometry(sb, badWay)
            }
        }
        return sb.toString()
    }

    private fun appendTurnGeometry(sb: StringBuilder, msg: MessageData) {
        sb.append("(").append((msg.turnangle + 0.5).toInt()).append(")")
            .append((msg.priorityclassifier))
    }

    public fun hasGiveWay(): Boolean {
        val tags = oldWay?.wayTags
        if (tags != null) {
            return if (tags["reversedirection"] == "yes") {
                (tags["highway"] == "give_way" || tags["highway"] == "stop") && tags["direction"] == "backward"
            } else {
                (tags["highway"] == "give_way" || tags["highway"] == "stop") && tags["direction"] != "backward"
            }
        }
        return false
    }

    public companion object {
        public const val C: Int = 1 // continue (go straight)
        public const val TL: Int = 2 // turn left
        public const val TSLL: Int = 3 // turn slightly left
        public const val TSHL: Int = 4 // turn sharply left
        public const val TR: Int = 5 // turn right
        public const val TSLR: Int = 6 // turn slightly right
        public const val TSHR: Int = 7 // turn sharply right
        public const val KL: Int = 8 // keep left
        public const val KR: Int = 9 // keep right
        public const val TLU: Int = 10 // U-turn
        public const val TRU: Int = 11 // Right U-turn
        public const val OFFR: Int = 12 // Off route
        public const val RNDB: Int = 13 // Roundabout
        public const val RNLB: Int = 14 // Roundabout left
        public const val TU: Int = 15 // 180 degree u-turn
        public const val BL: Int = 16 // Beeline routing
        public const val EL: Int = 17 // exit left
        public const val ER: Int = 18 // exit right

        public const val END: Int = 100 // end point

        public fun is180DegAngle(angle: Float): Boolean {
            return (abs(angle) in 179f..180f)
        }
    }
}

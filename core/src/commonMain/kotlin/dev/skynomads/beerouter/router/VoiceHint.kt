/**
 * Container for a voice hint
 * (both input- and result data for voice hint processing)
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import kotlin.math.abs

public class VoiceHint {
    public enum class Command {
        UNSET,
        C,    // continue (go straight)
        TL,   // turn left
        TSLL, // turn slightly left
        TSHL, // turn sharply left
        TR,   // turn right
        TSLR, // turn slightly right
        TSHR, // turn sharply right
        KL,   // keep left
        KR,   // keep right
        TLU,  // U-turn left
        TRU,  // U-turn right
        OFFR, // Off route
        RNDB, // Roundabout
        RNLB, // Roundabout left
        TU,   // 180 degree u-turn
        BL,   // Beeline routing
        EL,   // exit left
        ER,   // exit right
        END,  // end point
    }

    public var ilon: Int = 0
    public var ilat: Int = 0
    public var selev: Short = 0
    public var command: Command = Command.UNSET
    public var oldWay: MessageData? = null
    public var goodWay: MessageData? = null
    public var badWays: MutableList<MessageData> = mutableListOf()
    public var distanceToNext: Double = 0.0
    public var indexInTrack: Int = 0

    public val time: Float
        get() = oldWay?.time ?: 0f

    public var angle: Float = Float.MAX_VALUE
    public var lowerBadWayAngle: Float = -181f
    public var higherBadWayAngle: Float = 181f

    public var turnAngleConsumed: Boolean = false
    public var needsRealTurn: Boolean = false
    public var maxBadPrio: Int = -1

    public var exitNumber: Int = 0

    public val isRoundabout: Boolean
        get() = this.exitNumber != 0

    private fun usesExplicitExitCommands(timode: Int): Boolean = timode == 2 || timode == 9

    public fun addBadWay(badWay: MessageData?) {
        if (badWay != null) {
            badWays.add(badWay)
        }
    }

    public fun getJsonCommandIndex(timode: Int): Int {
        return when (command) {
            Command.TLU -> 10
            Command.TU -> 15
            Command.TSHL -> 4
            Command.TL -> 2
            Command.TSLL -> 3
            Command.KL -> 8
            Command.C -> 1
            Command.KR -> 9
            Command.TSLR -> 6
            Command.TR -> 5
            Command.TSHR -> 7
            Command.TRU -> 11
            Command.RNDB -> 13
            Command.RNLB -> 14
            Command.BL -> 16
            Command.EL -> if (usesExplicitExitCommands(timode)) 17 else 8
            Command.ER -> if (usesExplicitExitCommands(timode)) 18 else 9
            Command.OFFR -> 12
            else -> throw IllegalArgumentException("unknown command: $command")
        }
    }

    /*
   * used by comment style, osmand style
   */
    public fun getCommandString(timode: Int): String {
        return when (command) {
            Command.TLU -> "TU" // should be changed to TLU when osmand uses new voice hint constants
            Command.TU -> "TU"
            Command.TSHL -> "TSHL"
            Command.TL -> "TL"
            Command.TSLL -> "TSLL"
            Command.KL -> "KL"
            Command.C -> "C"
            Command.KR -> "KR"
            Command.TSLR -> "TSLR"
            Command.TR -> "TR"
            Command.TSHR -> "TSHR"
            Command.TRU -> "TRU"
            Command.RNDB -> "RNDB" + exitNumber
            Command.RNLB -> "RNLB" + (-exitNumber)
            Command.BL -> "BL"
            Command.EL -> if (usesExplicitExitCommands(timode)) "EL" else "KL"
            Command.ER -> if (usesExplicitExitCommands(timode)) "ER" else "KR"
            Command.OFFR -> "OFFR"
            Command.END -> "END"
            else -> throw IllegalArgumentException("unknown command: $command")
        }
    }

    /*
   * used by trkpt/sym style
   */
    public fun getCommandString(c: Command, timode: Int): String {
        return when (c) {
            Command.TLU -> "TLU"
            Command.TU -> "TU"
            Command.TSHL -> "TSHL"
            Command.TL -> "TL"
            Command.TSLL -> "TSLL"
            Command.KL -> "KL"
            Command.C -> "C"
            Command.KR -> "KR"
            Command.TSLR -> "TSLR"
            Command.TR -> "TR"
            Command.TSHR -> "TSHR"
            Command.TRU -> "TRU"
            Command.RNDB -> "RNDB" + exitNumber
            Command.RNLB -> "RNLB" + (-exitNumber)
            Command.BL -> "BL"
            Command.EL -> if (usesExplicitExitCommands(timode)) "EL" else "KL"
            Command.ER -> if (usesExplicitExitCommands(timode)) "ER" else "KR"
            Command.OFFR -> "OFFR"
            else -> "unknown command: $c"
        }
    }

    /*
   * used by gpsies style
   */
    public fun getSymbolString(timode: Int): String {
        return when (command) {
            Command.TLU -> "TU"
            Command.TU -> "TU"
            Command.TSHL -> "TSHL"
            Command.TL -> "Left"
            Command.TSLL -> "TSLL"
            Command.KL -> "TSLL" // ?
            Command.C -> "Straight"
            Command.KR -> "TSLR" // ?
            Command.TSLR -> "TSLR"
            Command.TR -> "Right"
            Command.TSHR -> "TSHR"
            Command.TRU -> "TU"
            Command.RNDB -> "RNDB" + exitNumber
            Command.RNLB -> "RNLB" + (-exitNumber)
            Command.BL -> "BL"
            Command.EL -> if (usesExplicitExitCommands(timode)) "EL" else "KL"
            Command.ER -> if (usesExplicitExitCommands(timode)) "ER" else "KR"
            Command.OFFR -> "OFFR"
            else -> throw IllegalArgumentException("unknown command: $command")
        }
    }

    /*
       * used by new locus trkpt style
       */
    public val locusSymbolString: String
        get() {
            return when (command) {
                Command.TLU -> "u-turn_left"
                Command.TU -> "u-turn"
                Command.TSHL -> "left_sharp"
                Command.TL -> "left"
                Command.TSLL -> "left_slight"
                Command.KL -> "stay_left" // ?
                Command.C -> "straight"
                Command.KR -> "stay_right" // ?
                Command.TSLR -> "right_slight"
                Command.TR -> "right"
                Command.TSHR -> "right_sharp"
                Command.TRU -> "u-turn_right"
                Command.RNDB -> "roundabout_e" + exitNumber
                Command.RNLB -> "roundabout_e" + (-exitNumber)
                Command.BL -> "beeline"
                Command.EL -> "exit_left"
                Command.ER -> "exit_right"
                else -> throw IllegalArgumentException("unknown command: $command")
            }
        }

    /*
  * used by osmand style
  */
    public fun getMessageString(timode: Int): String {
        return when (command) {
            Command.TLU -> "u-turn" // should be changed to u-turn-left when osmand uses new voice hint constants
            Command.TU -> "u-turn"
            Command.TSHL -> "sharp left"
            Command.TL -> "left"
            Command.TSLL -> "slight left"
            Command.KL -> "keep left"
            Command.C -> "straight"
            Command.KR -> "keep right"
            Command.TSLR -> "slight right"
            Command.TR -> "right"
            Command.TSHR -> "sharp right"
            Command.TRU -> "u-turn" // should be changed to u-turn-right when osmand uses new voice hint constants
            Command.RNDB -> "Take exit " + exitNumber
            Command.RNLB -> "Take exit " + (-exitNumber)
            Command.EL -> if (usesExplicitExitCommands(timode)) "exit left" else "keep left"
            Command.ER -> if (usesExplicitExitCommands(timode)) "exit right" else "keep right"
            else -> throw IllegalArgumentException("unknown command: $command")
        }
    }

    /*
       * used by old locus style
       */
    public val locusAction: Int
        get() {
            return when (command) {
                Command.TLU -> 13
                Command.TU -> 12
                Command.TSHL -> 5
                Command.TL -> 4
                Command.TSLL -> 3
                Command.KL -> 9 // ?
                Command.C -> 1
                Command.KR -> 10 // ?
                Command.TSLR -> 6
                Command.TR -> 7
                Command.TSHR -> 8
                Command.TRU -> 14
                Command.RNDB -> 26 + exitNumber
                Command.RNLB -> 26 - exitNumber
                Command.EL -> 9
                Command.ER -> 10
                else -> throw IllegalArgumentException("unknown command: $command")
            }
        }

    /*
       * used by orux style
       */
    public val oruxAction: Int
        get() {
            return when (command) {
                Command.TLU -> 1003
                Command.TU -> 1003
                Command.TSHL -> 1019
                Command.TL -> 1000
                Command.TSLL -> 1017
                Command.KL -> 1015 // ?
                Command.C -> 1002
                Command.KR -> 1014 // ?
                Command.TSLR -> 1016
                Command.TR -> 1001
                Command.TSHR -> 1018
                Command.TRU -> 1003
                Command.RNDB -> 1008 + exitNumber
                Command.RNLB -> 1008 + exitNumber
                Command.EL -> 1015
                Command.ER -> 1014
                else -> throw IllegalArgumentException("unknown command: $command")
            }
        }

    /*
       * used by cruiser, equivalent to getCommandString() - osmand style - when osmand changes the voice hint constants
       */
    public val cruiserCommandString: String
        get() {
            return when (command) {
                Command.TLU -> "TLU"
                Command.TU -> "TU"
                Command.TSHL -> "TSHL"
                Command.TL -> "TL"
                Command.TSLL -> "TSLL"
                Command.KL -> "KL"
                Command.C -> "C"
                Command.KR -> "KR"
                Command.TSLR -> "TSLR"
                Command.TR -> "TR"
                Command.TSHR -> "TSHR"
                Command.TRU -> "TRU"
                Command.RNDB -> "RNDB" + exitNumber
                Command.RNLB -> "RNLB" + (-exitNumber)
                Command.BL -> "BL"
                Command.EL -> "EL"
                Command.ER -> "ER"
                Command.OFFR -> "OFFR"
                else -> throw IllegalArgumentException("unknown command: $command")
            }
        }

    /*
       * used by cruiser, equivalent to getMessageString() - osmand style - when osmand changes the voice hint constants
       */
    public val cruiserMessageString: String
        get() {
            return when (command) {
                Command.TLU -> "u-turn left"
                Command.TU -> "u-turn"
                Command.TSHL -> "sharp left"
                Command.TL -> "left"
                Command.TSLL -> "slight left"
                Command.KL -> "keep left"
                Command.C -> "straight"
                Command.KR -> "keep right"
                Command.TSLR -> "slight right"
                Command.TR -> "right"
                Command.TSHR -> "sharp right"
                Command.TRU -> "u-turn right"
                Command.RNDB -> "take exit " + exitNumber
                Command.RNLB -> "take exit " + (-exitNumber)
                Command.BL -> "beeline"
                Command.EL -> "exit left"
                Command.ER -> "exit right"
                Command.OFFR -> "offroad"
                else -> throw IllegalArgumentException("unknown command: $command")
            }
        }

    public fun calcCommand() {
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

        var cmdAngle = angle

        if (angle == Float.MAX_VALUE) {
            cmdAngle = goodWay!!.turnangle
        }
        if (this.command == Command.BL) return

        if (this.exitNumber > 0) {
            this.command = Command.RNDB
        } else if (this.exitNumber < 0) {
            this.command = Command.RNLB
        } else if (is180DegAngle(cmdAngle) && cmdAngle <= -179f && higherBadWayAngle == 181f && lowerBadWayAngle == -181f) {
            this.command = Command.TU
        } else if (cmdAngle < -159f) {
            this.command = Command.TLU
        } else if (cmdAngle < -135f) {
            this.command = Command.TSHL
        } else if (cmdAngle < -45f) {
            if (cmdAngle < -95f && higherBadWayAngle < -30f && lowerBadWayAngle < -180f) {
                this.command = Command.TSHL
            } else if (cmdAngle > -85f && lowerBadWayAngle > -180f && higherBadWayAngle > -10f) {
                this.command = Command.TSLL
            } else {
                if (cmdAngle < -110f) {
                    this.command = Command.TSHL
                } else if (cmdAngle > -60f) {
                    this.command = Command.TSLL
                } else {
                    this.command = Command.TL
                }
            }
        } else if (cmdAngle < -21f) {
            if (this.command != Command.KR) { // don't overwrite KR with TSLL
                this.command = Command.TSLL
            }
        } else if (cmdAngle < -5f) {
            if (lowerBadWayAngle < -100f && higherBadWayAngle < 45f) {
                this.command = Command.TSLL
            } else if (lowerBadWayAngle >= -100f && higherBadWayAngle < 45f) {
                this.command = Command.KL
            } else {
                if (lowerBadWayAngle > -35f && higherBadWayAngle > 55f) {
                    this.command = Command.KR
                } else {
                    this.command = Command.C
                }
            }
        } else if (cmdAngle < 5f) {
            if (lowerBadWayAngle > -30f) {
                this.command = Command.KR
            } else if (higherBadWayAngle < 30f) {
                this.command = Command.KL
            } else {
                this.command = Command.C
            }
        } else if (cmdAngle < 21f) {
            if (lowerBadWayAngle > -45f && higherBadWayAngle > 100f) {
                this.command = Command.TSLR
            } else if (lowerBadWayAngle > -45f && higherBadWayAngle <= 100f) {
                this.command = Command.KR
            } else {
                if (lowerBadWayAngle < -55f && higherBadWayAngle < 35f) {
                    this.command = Command.KL
                } else {
                    this.command = Command.C
                }
            }
        } else if (cmdAngle < 45f) {
            this.command = Command.TSLR
        } else if (cmdAngle < 135f) {
            if (cmdAngle < 85f && higherBadWayAngle < 180f && lowerBadWayAngle < 10f) {
                this.command = Command.TSLR
            } else if (cmdAngle > 95f && lowerBadWayAngle > 30f && higherBadWayAngle > 180f) {
                this.command = Command.TSHR
            } else {
                if (cmdAngle > 110.0) {
                    this.command = Command.TSHR
                } else if (cmdAngle < 60.0) {
                    this.command = Command.TSLR
                } else {
                    this.command = Command.TR
                }
            }
        } else if (cmdAngle < 159f) {
            this.command = Command.TSHR
        } else if (is180DegAngle(cmdAngle) && cmdAngle >= 179f && higherBadWayAngle == 181f && lowerBadWayAngle == -181f) {
            this.command = Command.TU
        } else {
            this.command = Command.TRU
        }
    }

    public fun formatGeometry(): String {
        val oldPrio = oldWay?.priorityclassifier?.toFloat() ?: 0f
        val sb = StringBuilder(30)
        sb.append(' ').append(oldPrio.toInt())
        appendTurnGeometry(sb, goodWay!!)
        for (badWay in badWays) {
            sb.append(" ")
            appendTurnGeometry(sb, badWay)
        }
        return sb.toString()
    }

    private fun appendTurnGeometry(sb: StringBuilder, msg: MessageData) {
        sb.append("(").append((msg.turnangle + 0.5).toInt()).append(")")
            .append((msg.priorityclassifier))
    }

    public fun hasGiveWay(): Boolean {
        val wayTags = oldWay?.wayTags
        val nodeTags = oldWay?.nodeTags
        if (wayTags != null && nodeTags != null) {
            return if (wayTags["reversedirection"] == "yes") {
                (nodeTags["highway"] == "give_way" || nodeTags["highway"] == "stop") &&
                        nodeTags["direction"] == "backward"
            } else {
                (nodeTags["highway"] == "give_way" || nodeTags["highway"] == "stop") &&
                        nodeTags["direction"] != "backward"
            }
        }
        return false
    }

    public companion object {
        public fun is180DegAngle(angle: Float): Boolean {
            return (abs(angle) in 179f..180f)
        }
    }
}

package dev.skynomads.beerouter.mapcreator

import java.io.File

public interface WayListener {
    public fun wayFileStart(wayfile: File): Boolean

    public fun nextWay(data: WayData)

    public fun wayFileEnd(wayfile: File)
}

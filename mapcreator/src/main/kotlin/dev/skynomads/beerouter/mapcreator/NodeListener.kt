package dev.skynomads.beerouter.mapcreator

import java.io.File

public interface NodeListener {
    public fun nodeFileStart(nodefile: File?)

    public fun nextNode(data: NodeData)

    public fun nodeFileEnd(nodefile: File?)
}

package dev.skynomads.beerouter.mapcreator

import androidx.collection.MutableLongIntMap
import dev.skynomads.beerouter.mapcreator.DiffCoderDataOutputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

public class NodeFilter : MapCreatorBase() {
    private lateinit var nodesOutStream: DiffCoderDataOutputStream
    private lateinit var nodeTilesOut: File
    public lateinit var nodebitmap: MutableLongIntMap
    public var retainDescribedNodes: Boolean = false

    public fun init() {
        nodebitmap = MutableLongIntMap()
    }

    public fun process(nodeTilesIn: File, wayFileIn: File, nodeTilesOut: File) {
        init()
        this.nodeTilesOut = nodeTilesOut
        WayIterator(this, false).processFile(wayFileIn)
        NodeIterator(this, true).processDir(nodeTilesIn, ".tls")
    }

    override fun nextWay(data: WayData) {
        for (i in 0 until data.nodes.size) {
            nodebitmap[data.nodes.get(i)] = 0
        }
    }

    override fun nodeFileStart(nodefile: File?) {
        val outfile = File(nodeTilesOut, requireNotNull(nodefile).name)
        nodesOutStream = DiffCoderDataOutputStream(BufferedOutputStream(FileOutputStream(outfile)))
    }

    override fun nextNode(data: NodeData) {
        if (isRelevant(data)) {
            data.writeTo(nodesOutStream)
        }
    }

    public fun isRelevant(node: NodeData): Boolean =
        nodebitmap.getOrDefault(node.nid, -1) == 0 || (retainDescribedNodes && node.description != null)

    override fun nodeFileEnd(nodefile: File?) {
        nodesOutStream.close()
    }
}

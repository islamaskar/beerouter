package dev.skynomads.beerouter.mapcreator

import dev.skynomads.beerouter.mapcreator.DiffCoderDataInputStream
import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream

public class NodeIterator(
    private val listener: NodeListener,
    private val deleteAfterReading: Boolean
) : MapCreatorBase() {
    public fun processDir(indir: File, inSuffix: String) {
        if (!indir.isDirectory) {
            throw IllegalArgumentException("not a directory: $indir")
        }
        for (nodefile in sortBySizeAsc(requireNotNull(indir.listFiles()))) {
            if (nodefile.name.endsWith(inSuffix)) {
                processFile(nodefile)
            }
        }
    }

    public fun processFile(nodefile: File) {
        println("*** NodeIterator reading: $nodefile")
        listener.nodeFileStart(nodefile)
        val di = DiffCoderDataInputStream(BufferedInputStream(FileInputStream(nodefile)))
        try {
            while (true) {
                listener.nextNode(NodeData(di))
            }
        } catch (_: EOFException) {
            di.close()
        }
        listener.nodeFileEnd(nodefile)
        if (deleteAfterReading && System.getProperty("deletetmpfiles") == "true") {
            nodefile.delete()
        }
    }
}

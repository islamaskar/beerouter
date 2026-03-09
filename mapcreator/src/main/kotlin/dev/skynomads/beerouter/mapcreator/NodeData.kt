package dev.skynomads.beerouter.mapcreator

import dev.skynomads.beerouter.mapcreator.DiffCoderDataInputStream
import dev.skynomads.beerouter.mapcreator.DiffCoderDataOutputStream

public class NodeData : MapCreatorBase {
    public var nid: Long
    public var ilon: Int
    public var ilat: Int
    public var description: ByteArray? = null
    public var selev: Short = Short.MIN_VALUE

    public constructor(id: Long, lon: Double, lat: Double) {
        nid = id
        ilat = ((lat + 90.0) * 1_000_000.0 + 0.5).toInt()
        ilon = ((lon + 180.0) * 1_000_000.0 + 0.5).toInt()
    }

    public constructor(dis: DiffCoderDataInputStream) {
        nid = dis.readDiffed(0)
        ilon = dis.readDiffed(1).toInt()
        ilat = dis.readDiffed(2).toInt()
        val mode = dis.readByte().toInt()
        if ((mode and 1) != 0) {
            val dlen = dis.readShort().toInt()
            description = ByteArray(dlen)
            dis.readFully(description)
        }
        if ((mode and 2) != 0) {
            selev = dis.readShort()
        }
    }

    public fun writeTo(dos: DiffCoderDataOutputStream) {
        dos.writeDiffed(nid, 0)
        dos.writeDiffed(ilon.toLong(), 1)
        dos.writeDiffed(ilat.toLong(), 2)
        val mode = (if (description == null) 0 else 1) or (if (selev == Short.MIN_VALUE) 0 else 2)
        dos.writeByte(mode)
        if ((mode and 1) != 0) {
            dos.writeShort(description!!.size)
            dos.write(description)
        }
        if ((mode and 2) != 0) {
            dos.writeShort(selev.toInt())
        }
    }
}

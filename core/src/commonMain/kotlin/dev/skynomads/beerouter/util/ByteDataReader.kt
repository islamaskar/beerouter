/**
 * fast data-reading from a byte-array
 *
 * @author ab
 */
package dev.skynomads.beerouter.util


public open class ByteDataReader {
    protected var ab: ByteArray = ByteArray(0)
    protected var aboffset: Int = 0
    protected var aboffsetEnd: Int

    public constructor(byteArray: ByteArray = ByteArray(0)) {
        ab = byteArray
        aboffsetEnd = ab.size
    }

    public constructor(byteArray: ByteArray, offset: Int) {
        ab = byteArray
        aboffset = offset
        aboffsetEnd = ab.size
    }

    public fun reset(byteArray: ByteArray) {
        ab = byteArray
        aboffset = 0
        aboffsetEnd = ab.size
    }


    public fun readInt(): Int {
        val i3 = ab[aboffset++].toInt() and 0xff
        val i2 = ab[aboffset++].toInt() and 0xff
        val i1 = ab[aboffset++].toInt() and 0xff
        val i0 = ab[aboffset++].toInt() and 0xff
        return (i3 shl 24) + (i2 shl 16) + (i1 shl 8) + i0
    }

    public fun readLong(): Long {
        val i7 = (ab[aboffset++].toInt() and 0xff).toLong()
        val i6 = (ab[aboffset++].toInt() and 0xff).toLong()
        val i5 = (ab[aboffset++].toInt() and 0xff).toLong()
        val i4 = (ab[aboffset++].toInt() and 0xff).toLong()
        val i3 = (ab[aboffset++].toInt() and 0xff).toLong()
        val i2 = (ab[aboffset++].toInt() and 0xff).toLong()
        val i1 = (ab[aboffset++].toInt() and 0xff).toLong()
        val i0 = (ab[aboffset++].toInt() and 0xff).toLong()
        return (i7 shl 56) + (i6 shl 48) + (i5 shl 40) + (i4 shl 32) + (i3 shl 24) + (i2 shl 16) + (i1 shl 8) + i0
    }

    public fun readBoolean(): Boolean {
        val i0 = ab[aboffset++].toInt() and 0xff
        return i0 != 0
    }

    public fun readByte(): Byte {
        val i0 = ab[aboffset++].toInt() and 0xff
        return (i0).toByte()
    }

    public fun readShort(): Short {
        val i1 = ab[aboffset++].toInt() and 0xff
        val i0 = ab[aboffset++].toInt() and 0xff
        return ((i1 shl 8) or i0).toShort()
    }

    public val endPointer: Int
        /**
         * Read a size value and return a pointer to the end of a data section of that size
         *
         * @return the pointer to the first byte after that section
         */
        get() {
            val size = readVarLengthUnsigned()
            return aboffset + size
        }

    public fun readDataUntil(endPointer: Int): ByteArray? {
        val size = endPointer - aboffset
        if (size == 0) {
            return null
        }
        val data = ByteArray(size)
        readFully(data)
        return data
    }

    public fun readVarBytes(): ByteArray? {
        val len = readVarLengthUnsigned()
        if (len == 0) {
            return null
        }
        val bytes = ByteArray(len)
        readFully(bytes)
        return bytes
    }

    public fun readVarLengthSigned(): Int {
        val v = readVarLengthUnsigned()
        return if ((v and 1) == 0) v shr 1 else -(v shr 1)
    }

    public fun readVarLengthUnsigned(): Int {
        var b: Byte
        var v = (ab[aboffset++].also { b = it }).toInt() and 0x7f
        if (b >= 0) return v
        v = v or (((ab[aboffset++].also { b = it }).toInt() and 0x7f) shl 7)
        if (b >= 0) return v
        v = v or (((ab[aboffset++].also { b = it }).toInt() and 0x7f) shl 14)
        if (b >= 0) return v
        v = v or (((ab[aboffset++].also { b = it }).toInt() and 0x7f) shl 21)
        if (b >= 0) return v
        v = v or (((ab[aboffset++].also { b = it }).toInt() and 0xf) shl 28)
        return v
    }

    public fun readFully(ta: ByteArray) {
        ab.copyInto(ta, 0, aboffset, aboffset + ta.size)
        aboffset += ta.size
    }

    public fun hasMoreData(): Boolean {
        return aboffset < aboffsetEnd
    }

    override fun toString(): String {
        val sb = StringBuilder("[")
        for (i in ab.indices) sb.append(if (i == 0) " " else ", ")
            .append(ab[i].toInt().toString())
        sb.append(" ]")
        return sb.toString()
    }
}

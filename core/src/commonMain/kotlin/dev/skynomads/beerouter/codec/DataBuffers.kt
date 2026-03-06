package dev.skynomads.beerouter.codec

import dev.skynomads.beerouter.util.BitCoderContext

/**
 * Container for some re-usable databuffers for the decoder
 */
public class DataBuffers(public var iobuffer: ByteArray = ByteArray(65636)) {
    public var tagbuf1: ByteArray = ByteArray(256)
    public var bctx1: BitCoderContext = BitCoderContext(tagbuf1)
    public var bbuf1: ByteArray = ByteArray(65636)
    public var ibuf1: IntArray = IntArray(4096)
    public var ibuf2: IntArray = IntArray(2048)
    public var ibuf3: IntArray = IntArray(2048)
    public var alon: IntArray = IntArray(2048)
    public var alat: IntArray = IntArray(2048)
}

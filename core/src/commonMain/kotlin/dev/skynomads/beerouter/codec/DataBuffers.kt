package dev.skynomads.beerouter.codec

import dev.skynomads.beerouter.util.BitCoderContext

/**
 * Container for some re-usable databuffers for the decoder
 */
public class DataBuffers(public val iobuffer: ByteArray = ByteArray(65636)) {
    public val tagbuf1: ByteArray = ByteArray(256)
    public val bctx1: BitCoderContext = BitCoderContext(tagbuf1)
    public val bbuf1: ByteArray = ByteArray(65636)
    public val ibuf1: IntArray = IntArray(4096)
    public val ibuf2: IntArray = IntArray(2048)
    public val ibuf3: IntArray = IntArray(2048)
    public val alon: IntArray = IntArray(2048)
    public val alat: IntArray = IntArray(2048)
}

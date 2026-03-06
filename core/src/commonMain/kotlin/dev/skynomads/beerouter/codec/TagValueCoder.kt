package dev.skynomads.beerouter.codec

import dev.skynomads.beerouter.util.BitCoderContext

/**
 * Encoder/Decoder for way-/node-descriptions
 *
 * It detects identical descriptions and sorts them
 * into a huffman-tree according to their frequencies
 *
 * Adapted for 3-pass encoding (counters -> statistics -> encoding )
 * but doesn't do anything at pass1
 */
public class TagValueCoder {
    private var identityMap: MutableMap<TagValueSet?, TagValueSet?>? = null
    private var tree: Any? = null
    private var bc: BitCoderContext? = null
    private var pass = 0
    private var nextTagValueSetId = 0

    public fun encodeTagValueSet(data: ByteArray?) {
        if (pass == 1) {
            return
        }
        val tvsProbe = TagValueSet(nextTagValueSetId)
        tvsProbe.data = data
        var tvs = identityMap!![tvsProbe]
        if (pass == 3) {
            bc!!.encodeBounded(tvs!!.range - 1, tvs.code)
        } else if (pass == 2) {
            if (tvs == null) {
                tvs = tvsProbe
                nextTagValueSetId++
                identityMap!![tvs] = tvs
            }
            tvs.frequency++
        }
    }

    public fun decodeTagValueSet(): TagValueWrapper? {
        var node = tree
        while (node is TreeNode) {
            val tn = node
            val nextBit = bc!!.decodeBit()
            node = if (nextBit) tn.child2 else tn.child1
        }
        return node as TagValueWrapper?
    }

    public fun encodeDictionary(bc: BitCoderContext) {
        if (++pass == 3) {
            if (identityMap!!.isEmpty()) {
                val dummy = TagValueSet(nextTagValueSetId++)
                identityMap!![dummy] = dummy
            }
            val comparator = TagValueSet.FrequencyComparator()
            val queue = ArrayDeque<TagValueSet>()
            queue.addAll(identityMap!!.values.filterNotNull())
            queue.sortWith(comparator)
            while (queue.size > 1) {
                val node = TagValueSet(nextTagValueSetId++)
                node.child1 = queue.removeFirst()
                node.child2 = queue.removeFirst()
                node.frequency = node.child1!!.frequency + node.child2!!.frequency
                val insertIdx = queue.indexOfFirst { comparator.compare(node, it) <= 0 }
                if (insertIdx == -1) queue.addLast(node) else queue.add(insertIdx, node)
            }
            val root = queue.removeFirst()
            root.encode(bc, 1, 0)
        }
        this.bc = bc
    }

    public constructor(bc: BitCoderContext, buffers: DataBuffers, validator: TagValueValidator?) {
        tree = decodeTree(bc, buffers, validator)
        this.bc = bc
    }

    public constructor() {
        identityMap = HashMap()
    }

    private fun decodeTree(
        bc: BitCoderContext,
        buffers: DataBuffers,
        validator: TagValueValidator?
    ): Any? {
        val isNode = bc.decodeBit()
        if (isNode) {
            val node = TreeNode()
            node.child1 = decodeTree(bc, buffers, validator)
            node.child2 = decodeTree(bc, buffers, validator)
            return node
        }

        val buffer = buffers.tagbuf1
        val ctx = buffers.bctx1
        ctx.reset(buffer)

        var inum = 0
        var lastEncodedInum = 0

        var hasdata = false
        while (true) {
            val delta = bc.decodeVarBits()
            if (!hasdata) {
                if (delta == 0) {
                    return null
                }
            }
            if (delta == 0) {
                ctx.encodeVarBits(0)
                break
            }
            inum += delta

            val data = bc.decodeVarBits()

            if (validator == null || validator.isLookupIdxUsed(inum)) {
                hasdata = true
                ctx.encodeVarBits(inum - lastEncodedInum)
                ctx.encodeVarBits(data)
                lastEncodedInum = inum
            }
        }

        val res: ByteArray?
        val len = ctx.closeAndGetEncodedLength()
        if (validator == null) {
            res = ByteArray(len)
            buffer.copyInto(res, 0, 0, len)
        } else {
            res = validator.unify(buffer, 0, len)
        }

        val accessType = validator?.accessType(res) ?: 2
        if (accessType > 0) {
            return TagValueWrapper(res, accessType)
        }
        return null
    }

    public class TreeNode {
        public var child1: Any? = null
        public var child2: Any? = null
    }

    public class TagValueSet(
        private val id: Int
    ) {
        public var data: ByteArray? = null
        public var frequency: Int = 0
        public var code: Int = 0
        public var range: Int = 0
        public var child1: TagValueSet? = null
        public var child2: TagValueSet? = null

        public fun encode(bc: BitCoderContext, range: Int, code: Int) {
            this.range = range
            this.code = code
            val isNode = child1 != null
            bc.encodeBit(isNode)
            if (isNode) {
                child1!!.encode(bc, range shl 1, code)
                child2!!.encode(bc, range shl 1, code + range)
            } else {
                if (data == null) {
                    bc.encodeVarBits(0)
                    return
                }
                val src = BitCoderContext(data!!)
                while (true) {
                    val delta = src.decodeVarBits()
                    bc.encodeVarBits(delta)
                    if (delta == 0) {
                        break
                    }
                    val data = src.decodeVarBits()
                    bc.encodeVarBits(data)
                }
            }
        }

        override fun equals(other: Any?): Boolean {
            if (other !is TagValueSet) return false
            if (data == null) return other.data == null
            if (other.data == null) return false
            if (data!!.size != other.data!!.size) return false
            return data!!.contentEquals(other.data!!)
        }

        override fun hashCode(): Int {
            return data?.contentHashCode() ?: 0
        }

        public class FrequencyComparator : Comparator<TagValueSet> {
            override fun compare(tvs1: TagValueSet, tvs2: TagValueSet): Int {
                if (tvs1.frequency < tvs2.frequency) return -1
                if (tvs1.frequency > tvs2.frequency) return 1

                // to avoid ordering instability, decide on the id if frequency is equal
                if (tvs1.id < tvs2.id) return -1
                if (tvs1.id > tvs2.id) return 1

                if (tvs1 !== tvs2) {
                    throw RuntimeException("identity corruption!")
                }
                return 0
            }
        }
    }
}

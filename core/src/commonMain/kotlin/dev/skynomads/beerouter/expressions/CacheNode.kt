package dev.skynomads.beerouter.expressions

public class CacheNode {
    public var ab: ByteArray? = null
    public var vars: FloatArray? = null
    public var hash: Int = 0

    override fun hashCode(): Int {
        return hash
    }

    override fun equals(o: Any?): Boolean {
        val n = o as CacheNode
        if (hash != n.hash) {
            return false
        }
        if (ab == null) {
            return true // hack: null = crc match only
        }
        return ab.contentEquals(n.ab)
    }
}

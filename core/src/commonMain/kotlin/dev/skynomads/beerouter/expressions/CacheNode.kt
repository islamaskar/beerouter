package dev.skynomads.beerouter.expressions

public class CacheNode {
    public var ab: ByteArray? = null
    public var vars: FloatArray? = null
    public var hash: Int = 0

    override fun hashCode(): Int {
        return hash
    }

    override fun equals(o: Any?): Boolean {
        if (this === o) return true
        val n = o as? CacheNode ?: return false
        if (hash != n.hash) {
            return false
        }
        val currentAb = ab ?: return true // hack: null = crc match only
        val otherAb = n.ab ?: return false
        if (currentAb === otherAb) return true
        return currentAb.contentEquals(otherAb)
    }
}

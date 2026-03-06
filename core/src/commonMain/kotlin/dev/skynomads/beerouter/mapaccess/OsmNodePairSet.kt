/**
 * Set holding pairs of osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import androidx.collection.MutableLongObjectMap


public class OsmNodePairSet(maxTempNodeCount: Int) {
    private val n1a: LongArray
    private val n2a: LongArray
    private var tempNodes = 0
    public var maxTmpNodes: Int = 0
        private set
    private var npairs = 0
    public var freezeCount: Int = 0
        private set

    private class OsmNodePair {
        public var node2: Long = 0
        public var next: OsmNodePair? = null
    }

    private var map: MutableLongObjectMap<OsmNodePair> = MutableLongObjectMap()

    init {
        this.maxTmpNodes = maxTempNodeCount
        n1a = LongArray(this.maxTmpNodes)
        n2a = LongArray(this.maxTmpNodes)
    }

    public fun addTempPair(n1: Long, n2: Long) {
        if (tempNodes < this.maxTmpNodes) {
            n1a[tempNodes] = n1
            n2a[tempNodes] = n2
            tempNodes++
        }
    }

    public fun freezeTempPairs() {
        this.freezeCount++
        for (i in 0..<tempNodes) {
            addPair(n1a[i], n2a[i])
        }
        tempNodes = 0
    }

    public fun clearTempPairs() {
        tempNodes = 0
    }

    private fun addPair(n1: Long, n2: Long) {
        npairs++

        var e = getElement(n1, n2)
        if (e == null) {
            e = OsmNodePair()
            e.node2 = n2

            var e0 = map[n1]
            if (e0 != null) {
                while (e0!!.next != null) {
                    e0 = e0.next
                }
                e0.next = e
            } else {
                map.put(n1, e)
            }
        }
    }

    public fun size(): Int {
        return npairs
    }

    public fun hasPair(n1: Long, n2: Long): Boolean {
        return getElement(n1, n2) != null || getElement(n2, n1) != null
    }

    private fun getElement(n1: Long, n2: Long): OsmNodePair? {
        var e = map[n1]
        while (e != null) {
            if (e.node2 == n2) {
                return e
            }
            e = e.next
        }
        return null
    }
}

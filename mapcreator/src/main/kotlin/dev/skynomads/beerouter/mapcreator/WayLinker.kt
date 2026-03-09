package dev.skynomads.beerouter.mapcreator

import androidx.collection.MutableLongObjectMap
import androidx.collection.MutableLongSet
import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.codec.MicroCache
import dev.skynomads.beerouter.codec.MicroCache2
import dev.skynomads.beerouter.codec.StatCoderContext
import dev.skynomads.beerouter.expressions.BExpressionContextWay
import dev.skynomads.beerouter.expressions.BExpressionMetaData
import dev.skynomads.beerouter.util.ByteArrayUnifier
import dev.skynomads.beerouter.util.Crc32
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.TreeMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

public class WayLinker : MapCreatorBase(), Runnable {
    private lateinit var nodeTilesIn: File
    private lateinit var wayTilesIn: File
    private lateinit var dataTilesOut: File
    private lateinit var borderFileIn: File
    private lateinit var dataTilesSuffix: String
    private var readingBorder: Boolean = false
    private lateinit var nodesMap: MutableLongObjectMap<OsmNodeP>
    private var nodesList: MutableList<OsmNodeP>? = null
    private lateinit var borderSet: MutableLongSet
    private var lookupVersion: Short = 0
    private var lookupMinorVersion: Short = 0
    private var creationTimeStamp: Long = 0
    private var elevationType: Byte = 0
    private lateinit var expctxWay: BExpressionContextWay
    private lateinit var abUnifier: ByteArrayUnifier
    private var minLon: Int = -1
    private var minLat: Int = -1
    private val microCacheEncoding: Int = 2
    private val divisor: Int = 32
    private val cellsize: Int = 1_000_000 / divisor
    private var skipEncodingCheck: Boolean = false
    private var isSlave: Boolean = false
    private lateinit var tc: ThreadController

    public class ThreadController {
        private val lock = ReentrantLock()
        private val stateChanged = lock.newCondition()

        public var maxFileSize: Long = 0L
        public var currentSlaveSize: Long = 0L
        public var currentMasterSize: Long = 2_000_000_000L

        public fun setCurrentMasterSize(size: Long): Boolean {
            lock.withLock {
                if (size <= currentSlaveSize) {
                    maxFileSize = Long.MAX_VALUE
                    stateChanged.signalAll()
                    return false
                }
                currentMasterSize = size
                if (maxFileSize == 0L) {
                    maxFileSize = size
                }
                stateChanged.signalAll()
                return true
            }
        }

        public fun setCurrentSlaveSize(size: Long): Boolean {
            lock.withLock {
                if (size >= currentMasterSize) {
                    return false
                }
                while (size + currentMasterSize + 50_000_000L > maxFileSize) {
                    println(
                        "****** slave thread waiting for permission to process file of size $size " +
                            "currentMaster=$currentMasterSize maxFileSize=$maxFileSize"
                    )
                    stateChanged.awaitNanos(10_000_000_000L)
                }
                currentSlaveSize = size
                return true
            }
        }
    }

    private fun reset() {
        minLon = -1
        minLat = -1
        nodesMap = MutableLongObjectMap()
        borderSet = MutableLongSet()
    }

    public fun process(
        nodeTilesIn: File,
        wayTilesIn: File,
        borderFileIn: File,
        restrictionsFileIn: File,
        lookupFile: File,
        profileFile: File,
        dataTilesOut: File,
        dataTilesSuffix: String,
    ) {
        val master = WayLinker()
        val slave = WayLinker()
        slave.isSlave = true
        master.isSlave = false
        val controller = ThreadController()
        slave.tc = controller
        master.tc = controller
        master.processInternal(nodeTilesIn, wayTilesIn, borderFileIn, restrictionsFileIn, lookupFile, profileFile, dataTilesOut, dataTilesSuffix)
        slave.processInternal(nodeTilesIn, wayTilesIn, borderFileIn, restrictionsFileIn, lookupFile, profileFile, dataTilesOut, dataTilesSuffix)
        val m = Thread(master)
        val s = Thread(slave)
        m.start()
        s.start()
        m.join()
        s.join()
    }

    private fun processInternal(
        nodeTilesIn: File,
        wayTilesIn: File,
        borderFileIn: File,
        restrictionsFileIn: File,
        lookupFile: File,
        profileFile: File,
        dataTilesOut: File,
        dataTilesSuffix: String,
    ) {
        this.nodeTilesIn = nodeTilesIn
        this.wayTilesIn = wayTilesIn
        this.dataTilesOut = dataTilesOut
        this.borderFileIn = borderFileIn
        this.dataTilesSuffix = dataTilesSuffix

        val meta = BExpressionMetaData()
        expctxWay = BExpressionContextWay(meta)
        meta.readMetaData(lookupFile.readText())
        lookupVersion = meta.lookupVersion
        lookupMinorVersion = meta.lookupMinorVersion
        expctxWay.parseProfile(profileFile.readText(), "global")
        creationTimeStamp = System.currentTimeMillis()
        abUnifier = ByteArrayUnifier(16384, false)
        skipEncodingCheck = java.lang.Boolean.getBoolean("skipEncodingCheck")
    }

    override fun run() {
        try {
            // Both worker threads read from the same directory with different size ordering.
            // Do not delete input tiles during iteration or one thread can race the other.
            WayIterator(this, false, !isSlave).processDir(wayTilesIn, ".wt5")
        } catch (e: Exception) {
            throw RuntimeException(e)
        } finally {
            if (!isSlave) {
                tc.setCurrentMasterSize(0L)
            }
        }
    }

    override fun wayFileStart(wayfile: File): Boolean {
        val filesize = wayfile.length()
        if (isSlave) {
            if (!tc.setCurrentSlaveSize(filesize)) return false
        } else {
            if (!tc.setCurrentMasterSize(filesize)) return false
        }

        elevationType = 3
        var nodeFile = fileFromTemplate(wayfile, nodeTilesIn, "u5d_1")
        if (nodeFile.exists()) {
            elevationType = 1
        } else {
            nodeFile = fileFromTemplate(wayfile, nodeTilesIn, "u5d_3")
            if (!nodeFile.exists()) {
                nodeFile = fileFromTemplate(wayfile, nodeTilesIn, "u5d")
            }
        }
        if (nodeFile.exists()) {
            reset()
            readingBorder = true
            NodeIterator(this, false).processFile(borderFileIn)
            readingBorder = false
            NodeIterator(this, true).processFile(nodeFile)
            val restrictionFile = fileFromTemplate(wayfile, File(requireNotNull(nodeTilesIn.parentFile), "restrictions55"), "rt5")
            if (restrictionFile.exists()) {
                val di = DataInputStream(BufferedInputStream(FileInputStream(restrictionFile)))
                try {
                    while (true) {
                        val res = RestrictionData(di)
                        var node = nodesMap.get(res.viaNid)
                        if (node != null) {
                            if (node !is OsmNodePT) {
                                node = OsmNodePT(node)
                                nodesMap[res.viaNid] = node
                            }
                            node.firstRestrictionData = node.firstRestrictionData?.also { res.next = it }
                            res.viaLon = node.ilon
                            res.viaLat = node.ilat
                            node.firstRestrictionData = res
                        }
                    }
                } catch (_: EOFException) {
                    di.close()
                }
            }
            val sortedNodeIds = LongArray(nodesMap.size)
            var nodeIndex = 0
            nodesMap.forEachKey { nid ->
                sortedNodeIds[nodeIndex++] = nid
            }
            sortedNodeIds.sort()
            nodesList = ArrayList<OsmNodeP>(sortedNodeIds.size).also { values ->
                for (nid in sortedNodeIds) {
                    values.add(requireNotNull(nodesMap.get(nid)) { "missing node $nid after sorting" })
                }
            }
            return true
        }
        return false
    }

    override fun nextNode(data: NodeData) {
        val node = if (data.description == null) {
            OsmNodeP()
        } else {
            OsmNodePT(data.description, data.retainWithoutLinks)
        }
        node.ilon = data.ilon
        node.ilat = data.ilat
        node.selev = data.selev
        if (readingBorder || !borderSet.contains(data.nid)) {
            nodesMap[data.nid] = node
        }
        if (readingBorder) {
            node.bits = (node.bits.toInt() or OsmNodeP.BORDER_BIT).toByte()
            borderSet.add(data.nid)
            return
        }
        val minLon = (node.ilon / 5_000_000) * 5_000_000
        val minLat = (node.ilat / 5_000_000) * 5_000_000
        if (this.minLon == -1) this.minLon = minLon
        if (this.minLat == -1) this.minLat = minLat
        if (this.minLon != minLon || this.minLat != minLat) {
            throw IllegalArgumentException("inconsistent node: ${node.ilon} ${node.ilat}")
        }
    }

    private fun checkRestriction(n1: OsmNodeP, n2: OsmNodeP, w: WayData) {
        checkRestriction(n1, n2, w, true)
        checkRestriction(n2, n1, w, false)
    }

    private fun checkRestriction(n1: OsmNodeP, n2: OsmNodeP, w: WayData, checkFrom: Boolean) {
        var restriction = n2.getFirstRestriction()
        while (restriction != null) {
            if (restriction.fromWid == w.wid && (restriction.fromLon == 0 || checkFrom)) {
                restriction.fromLon = n1.ilon
                restriction.fromLat = n1.ilat
                n1.bits = (n1.bits.toInt() or OsmNodeP.DP_SURVIVOR_BIT).toByte()
                if (!isEndNode(n2, w)) restriction.badWayMatch = true
            }
            if (restriction.toWid == w.wid && (restriction.toLon == 0 || !checkFrom)) {
                restriction.toLon = n1.ilon
                restriction.toLat = n1.ilat
                n1.bits = (n1.bits.toInt() or OsmNodeP.DP_SURVIVOR_BIT).toByte()
                if (!isEndNode(n2, w)) restriction.badWayMatch = true
            }
            restriction = restriction.next
        }
    }

    private fun isEndNode(node: OsmNodeP, way: WayData): Boolean =
        node === nodesMap.get(way.nodes.get(0)) || node === nodesMap.get(way.nodes.get(way.nodes.size - 1))

    override fun nextWay(data: WayData) {
        val description = abUnifier.unify(requireNotNull(data.description))
        expctxWay.evaluate(false, description)
        var ok = expctxWay.costfactor < 10000f
        expctxWay.evaluate(true, description)
        ok = ok || expctxWay.costfactor < 10000f
        if (!ok) return

        var wayBits: Byte = 0
        expctxWay.decode(description)
        if (!expctxWay.getBooleanLookupValue("bridge")) wayBits = (wayBits.toInt() or OsmNodeP.NO_BRIDGE_BIT).toByte()
        if (!expctxWay.getBooleanLookupValue("tunnel")) wayBits = (wayBits.toInt() or OsmNodeP.NO_TUNNEL_BIT).toByte()

        var n1: OsmNodeP? = null
        var n2: OsmNodeP? = null
        for (i in 0 until data.nodes.size) {
            val nid = data.nodes.get(i)
            n1 = n2
            n2 = nodesMap.get(nid)
            if (n1 != null && n2 != null && n1 !== n2) {
                checkRestriction(n1, n2, data)
                val link = n2.createLink(n1)
                link.descriptionBitmap = description
                if (n1.ilon / cellsize != n2.ilon / cellsize || n1.ilat / cellsize != n2.ilat / cellsize) {
                    n2.incWayCount()
                }
            }
            if (n2 != null) {
                n2.bits = (n2.bits.toInt() or wayBits.toInt()).toByte()
                n2.incWayCount()
            }
        }
    }

    override fun wayFileEnd(wayfile: File) {
        val ncaches = divisor * divisor
        val indexsize = ncaches * 4
        val localNodesList = nodesList ?: return

        nodesMap = MutableLongObjectMap()
        borderSet = MutableLongSet()

        val abBuf1 = ByteArray(10 * 1024 * 1024)
        val abBuf2 = ByteArray(10 * 1024 * 1024)
        val maxLon = minLon + 5_000_000
        val maxLat = minLat + 5_000_000

        for (node in localNodesList) {
            if ((node.getFirstLink() == null && !node.retainWithoutLinks()) || node.isTransferNode()) continue
            node.checkDuplicateTargets()
        }

        val nLonSegs = (maxLon - minLon) / 1_000_000
        val nLatSegs = (maxLat - minLat) / 1_000_000
        val seglists: Array<MutableList<OsmNodeP>?> = arrayOfNulls(nLonSegs * nLatSegs)
        for (node in localNodesList) {
            if ((node.getFirstLink() == null && !node.retainWithoutLinks()) || node.isTransferNode()) continue
            if (node.ilon < minLon || node.ilon >= maxLon || node.ilat < minLat || node.ilat >= maxLat) continue
            val lonIdx = (node.ilon - minLon) / 1_000_000
            val latIdx = (node.ilat - minLat) / 1_000_000
            val idx = lonIdx * nLatSegs + latIdx
            val list = seglists[idx] ?: ArrayList<OsmNodeP>().also { seglists[idx] = it }
            list.add(node)
        }

        val outfile = fileFromTemplate(wayfile, dataTilesOut, dataTilesSuffix)
        val os = createOutStream(outfile)
        val fileIndex = LongArray(25)
        val fileHeaderCrcs = IntArray(25)
        repeat(25) { os.writeLong(0L) }
        var filepos = 200L

        for (lonIdx in 0 until nLonSegs) {
            for (latIdx in 0 until nLatSegs) {
                val tileIndex = lonIdx * nLatSegs + latIdx
                val nlist = seglists[tileIndex]
                if (nlist != null && nlist.isNotEmpty()) {
                    val subs: Array<MutableList<OsmNodeP>?> = arrayOfNulls(ncaches)
                    val subByteArrays: Array<ByteArray?> = arrayOfNulls(ncaches)
                    for (node in nlist) {
                        val subLonIdx = (node.ilon - minLon) / cellsize - divisor * lonIdx
                        val subLatIdx = (node.ilat - minLat) / cellsize - divisor * latIdx
                        val si = subLatIdx * divisor + subLonIdx
                        val list = subs[si] ?: ArrayList<OsmNodeP>().also { subs[si] = it }
                        list.add(node)
                    }
                    val posIdx = IntArray(ncaches)
                    var pos = indexsize
                    for (si in 0 until ncaches) {
                        val subList = subs[si]
                        if (subList != null && subList.isNotEmpty()) {
                            val n0 = subList[0]
                            val lonIdxDiv = n0.ilon / cellsize
                            val latIdxDiv = n0.ilat / cellsize
                            val mc: MicroCache = MicroCache2(subList.size, abBuf2, lonIdxDiv, latIdxDiv, divisor)
                            val sortedList = TreeMap<Int, OsmNodeP>()
                            for (node in subList) {
                                val longId = node.idFromPos
                                val shrinkid = mc.shrinkId(longId)
                                require(mc.expandId(shrinkid) == longId) { "inconstistent shrinking: $longId" }
                                sortedList[shrinkid] = node
                            }
                            for (node in sortedList.values) {
                                node.writeNodeData(mc)
                            }
                            if (mc.size > 0) {
                                var subBytes: ByteArray
                                while (true) {
                                    val len = mc.encodeMicroCache(abBuf1)
                                    subBytes = abBuf1.copyOf(len)
                                    if (skipEncodingCheck) break
                                    val mc2 = MicroCache2(
                                        StatCoderContext(subBytes),
                                        DataBuffers(),
                                        lonIdxDiv,
                                        latIdxDiv,
                                        divisor,
                                        null,
                                        null,
                                    )
                                    val diffMessage = mc.compareWith(mc2)
                                    if (diffMessage == null) {
                                        break
                                    }
                                    throw RuntimeException("encoding crosscheck failed: $diffMessage")
                                }
                                pos += subBytes.size + 4
                                subByteArrays[si] = subBytes
                            }
                        }
                        posIdx[si] = pos
                    }
                    val abSubIndex = compileSubFileIndex(posIdx)
                    fileHeaderCrcs[tileIndex] = Crc32.crc(abSubIndex, 0, abSubIndex.size)
                    os.write(abSubIndex)
                    for (ab in subByteArrays) {
                        if (ab != null) {
                            os.write(ab)
                            os.writeInt(Crc32.crc(ab, 0, ab.size) xor microCacheEncoding)
                        }
                    }
                    filepos += pos.toLong()
                }
                fileIndex[tileIndex] = filepos
            }
        }

        val abFileIndex = compileFileIndex(fileIndex, lookupVersion, lookupMinorVersion)
        os.writeLong(creationTimeStamp)
        os.writeInt(Crc32.crc(abFileIndex, 0, abFileIndex.size) xor microCacheEncoding)
        for (i in 0 until 25) {
            os.writeInt(fileHeaderCrcs[i])
        }
        os.writeByte(elevationType.toInt())
        os.close()

        RandomAccessFile(outfile, "rw").use { ra ->
            ra.write(abFileIndex)
        }
        println("**** codec stats: *******\n${StatCoderContext.bitReport}")
    }

    private fun compileFileIndex(fileIndex: LongArray, lookupVersion: Short, lookupMinorVersion: Short): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { dos ->
            for (i55 in 0 until 25) {
                var versionPrefix = if (i55 == 1) lookupMinorVersion.toLong() else lookupVersion.toLong()
                versionPrefix = versionPrefix shl 48
                dos.writeLong(fileIndex[i55] or versionPrefix)
            }
        }
        return bos.toByteArray()
    }

    private fun compileSubFileIndex(posIdx: IntArray): ByteArray {
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { dos ->
            for (value in posIdx) {
                dos.writeInt(value)
            }
        }
        return bos.toByteArray()
    }
}

package dev.skynomads.beerouter.expressions

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.random.Random

public object ProfileComparator {
    public fun main(args: Array<String>) {
        if (args.size != 4) {
            println("usage: java ProfileComparator <lookup-file> <profile1> <profile2> <nsamples>")
            return
        }

        val nsamples = args[3].toInt()
        testContext(args[0], args[1], args[2], nsamples, false)
        testContext(args[0], args[1], args[2], nsamples, true)
    }

    private fun testContext(
        lookupPath: String,
        profile1Path: String,
        profile2Path: String,
        nsamples: Int,
        nodeContext: Boolean
    ) {
        val meta1 = BExpressionMetaData()
        val meta2 = BExpressionMetaData()
        val expctx1 =
            if (nodeContext) BExpressionContextNode(meta1) else BExpressionContextWay(meta1)
        val expctx2 =
            if (nodeContext) BExpressionContextNode(meta2) else BExpressionContextWay(meta2)

        if (fileName(profile1Path) == fileName(profile2Path)) {
            expctx2.skipConstantExpressionOptimizations = true
        }

        val lookupContent = readUtf8(lookupPath)
        val profile1Content = readUtf8(profile1Path)
        val profile2Content = readUtf8(profile2Path)

        meta1.readMetaData(lookupContent)
        meta2.readMetaData(lookupContent)
        expctx1.parseProfile(profile1Content, fileName(profile1Path), "global")
        println("usedTags1=" + expctx1.usedTagList())
        expctx2.parseProfile(profile2Content, fileName(profile2Path), "global")
        println("usedTags2=" + expctx2.usedTagList())

        println("nodeContext=$nodeContext nodeCount1=${expctx1.expressionNodeCount} nodeCount2=${expctx2.expressionNodeCount}")

        val rnd = Random.Default
        for (i in 0..<nsamples) {
            val data = expctx1.generateRandomValues(rnd)
            expctx1.evaluate(data)
            expctx2.evaluate(data)
            expctx1.assertAllVariablesEqual(expctx2)
        }
    }

    private fun readUtf8(path: String): String =
        SystemFileSystem.source(Path(path)).buffered().use { source ->
            val bytes = mutableListOf<Byte>()
            while (!source.exhausted()) {
                bytes += source.readByte()
            }
            bytes.toByteArray().decodeToString()
        }

    private fun fileName(path: String): String = path.substringAfterLast('/').substringAfterLast('\\')
}

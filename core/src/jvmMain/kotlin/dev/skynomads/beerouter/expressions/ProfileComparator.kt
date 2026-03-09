package dev.skynomads.beerouter.expressions

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
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

        val lookupContent = SystemFileSystem.source(Path(lookupPath)).buffered().use { it.readString() }
        val profile1Content = SystemFileSystem.source(Path(profile1Path)).buffered().use { it.readString() }
        val profile2Content = SystemFileSystem.source(Path(profile2Path)).buffered().use { it.readString() }

        meta1.readMetaData(lookupContent)
        meta2.readMetaData(lookupContent)
        expctx1.parseProfile(profile1Content, "global")
        println("usedTags1=" + expctx1.usedTagList())
        expctx2.parseProfile(profile2Content, "global")
        println("usedTags2=" + expctx2.usedTagList())

        println("nodeContext=$nodeContext nodeCount1=${expctx1.expressionNodeCount} nodeCount2=${expctx2.expressionNodeCount}")

        val rnd = Random.Default
        repeat(nsamples) {
            val data = expctx1.generateRandomValues(rnd)
            expctx1.evaluate(data)
            expctx2.evaluate(data)
            expctx1.assertAllVariablesEqual(expctx2)
        }
    }
    private fun fileName(path: String): String = Path(path).name
}

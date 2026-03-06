package dev.skynomads.beerouter.expressions

import java.io.File
import kotlin.random.Random

public object ProfileComparator {
    public fun main(args: Array<String>) {
        if (args.size != 4) {
            println("usage: java ProfileComparator <lookup-file> <profile1> <profile2> <nsamples>")
            return
        }

        val lookupFile = File(args[0])
        val profile1File = File(args[1])
        val profile2File = File(args[2])
        val nsamples = args[3].toInt()
        testContext(lookupFile, profile1File, profile2File, nsamples, false)
        testContext(lookupFile, profile1File, profile2File, nsamples, true)
    }

    private fun testContext(
        lookupFile: File,
        profile1File: File,
        profile2File: File,
        nsamples: Int,
        nodeContext: Boolean
    ) {
        val meta1 = BExpressionMetaData()
        val meta2 = BExpressionMetaData()
        val expctx1 =
            if (nodeContext) BExpressionContextNode(meta1) else BExpressionContextWay(meta1)
        val expctx2 =
            if (nodeContext) BExpressionContextNode(meta2) else BExpressionContextWay(meta2)

        if (profile1File.name == profile2File.name) {
            expctx2.skipConstantExpressionOptimizations = true
        }

        val lookupContent = lookupFile.readText()
        meta1.readMetaData(lookupContent)
        meta2.readMetaData(lookupContent)
        expctx1.parseProfile(profile1File.readText(), profile1File.name, "global")
        println("usedTags1=" + expctx1.usedTagList())
        expctx2.parseProfile(profile2File.readText(), profile2File.name, "global")
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
}

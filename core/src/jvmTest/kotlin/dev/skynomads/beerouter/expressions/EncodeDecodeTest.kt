package dev.skynomads.beerouter.expressions

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class EncodeDecodeTest {
    @Test
    fun encodeDecodeTest() {
        val root = File(System.getProperty("user.dir"))
        val profileDir = File(root, "../misc/profiles2")
        val lookupContent = javaClass.getResource("/lookups_test.dat")!!.readText()

        val meta = BExpressionMetaData()
        val expctxWay = BExpressionContextWay(meta)
        meta.readMetaData(lookupContent)
        expctxWay.parseProfile(File(profileDir, "trekking.brf").readText(), "trekking.brf", "global")

        val tags = arrayOf(
            "highway=residential",
            "oneway=yes",
            "depth=1'6\"",
            "maxheight=5.1m",
            "maxdraft=~3 m - 4 m",
            "reversedirection=yes"
        )

        val lookupData = expctxWay.createNewLookupData()!!
        for (arg in tags) {
            val idx = arg.indexOf('=')
            require(idx >= 0) { "bad argument (should be <tag>=<value>): $arg" }
            val key = arg.substring(0, idx)
            val value = arg.substring(idx + 1)
            expctxWay.addLookupValue(key, value, lookupData)
        }
        val description = expctxWay.encode(lookupData)!!

        expctxWay.evaluate(true, description)

        println("description: " + expctxWay.getKeyValueDescription(true, description))

        val costfactor = expctxWay.costfactor
        assertTrue(kotlin.math.abs(costfactor - 5.15f) < 0.00001f, "costfactor mismatch")
    }
}

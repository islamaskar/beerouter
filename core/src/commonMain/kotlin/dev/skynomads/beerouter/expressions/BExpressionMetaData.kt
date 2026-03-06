package dev.skynomads.beerouter.expressions

public class BExpressionMetaData {
    public var lookupVersion: Short = -1
    public var lookupMinorVersion: Short = -1
    public var minAppVersion: Short = -1

    private val listeners: MutableMap<String?, BExpressionContext> = HashMap()

    public fun registerListener(context: String?, ctx: BExpressionContext) {
        listeners[context] = ctx
    }

    public fun readMetaData(content: String) {
        var ctx: BExpressionContext? = null

        for (rawLine in content.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            if (line.startsWith(CONTEXT_TAG)) {
                ctx = listeners[line.substring(CONTEXT_TAG.length)]
                continue
            }
            if (line.startsWith(VERSION_TAG)) {
                lookupVersion = line.substring(VERSION_TAG.length).toShort()
                continue
            }
            if (line.startsWith(MINOR_VERSION_TAG)) {
                lookupMinorVersion = line.substring(MINOR_VERSION_TAG.length).toShort()
                continue
            }
            if (line.startsWith(MIN_APP_VERSION_TAG)) {
                minAppVersion = line.substring(MIN_APP_VERSION_TAG.length).toShort()
                continue
            }
            if (line.startsWith(VARLENGTH_TAG)) continue
            ctx?.parseMetaLine(line)
        }

        for (c in listeners.values) {
            c.finishMetaParsing()
        }
    }

    public companion object {
        private const val CONTEXT_TAG = "---context:"
        private const val VERSION_TAG = "---lookupversion:"
        private const val MINOR_VERSION_TAG = "---minorversion:"
        private const val VARLENGTH_TAG = "---readvarlength"
        private const val MIN_APP_VERSION_TAG = "---minappversion:"
    }
}

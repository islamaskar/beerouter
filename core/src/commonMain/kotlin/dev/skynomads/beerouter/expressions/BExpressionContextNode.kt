// context for simple expression
// context means:
// - the local variables
// - the local variable names
// - the lookup-input variables
package dev.skynomads.beerouter.expressions


public class BExpressionContextNode : BExpressionContext {
    override val buildInVariableNames: Array<String?>
        get() = buildInVariables

    public val initialcost: Float
        get() = getBuildInVariable(0)


    public constructor(meta: BExpressionMetaData) : super("node", meta)

    /**
     * Create an Expression-Context for way context
     *
     * @param hashSize size of hashmap for result caching
     */
    public constructor(hashSize: Int, meta: BExpressionMetaData) : super("node", hashSize, meta)

    public companion object {
        private val buildInVariables = arrayOf<String?>("initialcost")
    }
}

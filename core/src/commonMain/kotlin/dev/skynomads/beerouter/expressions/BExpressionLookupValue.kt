/**
 * A lookup value with optional aliases
 *
 *
 * toString just gives the primary value,
 * equals just compares against primary value
 * matches() also compares aliases
 *
 * @author ab
 */
package dev.skynomads.beerouter.expressions

public class BExpressionLookupValue(public var value: String) {
    public var aliases: MutableList<String>? = null

    override fun toString(): String {
        return value
    }

    public fun addAlias(alias: String) {
        if (aliases == null) aliases = mutableListOf()
        aliases!!.add(alias)
    }

    override fun equals(o: Any?): Boolean {
        return when (o) {
            is String -> value == o
            is BExpressionLookupValue -> value == o.value
            else -> false
        }
    }

    public fun matches(s: String?): Boolean {
        return value == s || aliases?.any { it == s } == true
    }
}

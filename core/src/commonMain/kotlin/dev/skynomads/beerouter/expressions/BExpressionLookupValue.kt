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

    public fun addAlias(alias: String?) {
        if (aliases == null) aliases = ArrayList()
        aliases!!.add(alias!!)
    }

    override fun equals(o: Any?): Boolean {
        if (o is String) {
            val v = o
            return value == v
        }
        if (o is BExpressionLookupValue) {
            val v = o

            return value == v.value
        }
        return false
    }

    public fun matches(s: String?): Boolean {
        if (value == s) return true
        if (aliases != null) {
            for (alias in aliases) {
                if (alias == s) return true
            }
        }
        return false
    }
}

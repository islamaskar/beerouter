package dev.skynomads.beerouter.codec


/**
 * TagValueWrapper wrapps a description bitmap
 * to add the access-type
 */
public data class TagValueWrapper(
    public val data: ByteArray?,
    public val accessType: Int
)

package dev.skynomads.beerouter.codec


/**
 * TagValueWrapper wrapps a description bitmap
 * to add the access-type
 */
public class TagValueWrapper {
    public var data: ByteArray? = null
    public var accessType: Int = 0
}

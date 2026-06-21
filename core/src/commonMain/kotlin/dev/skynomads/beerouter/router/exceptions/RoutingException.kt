package dev.skynomads.beerouter.router.exceptions

public open class RoutingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

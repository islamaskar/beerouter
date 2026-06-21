package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.router.exceptions.RoutingException

public class RoutingIslandException(
    message: String = "routing island detected",
    cause: Throwable? = null,
) : RoutingException(message, cause)

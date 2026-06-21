package dev.skynomads.beerouter.router.exceptions

public class ExpressionParseException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

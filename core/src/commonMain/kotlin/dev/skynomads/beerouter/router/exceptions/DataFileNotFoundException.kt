package dev.skynomads.beerouter.router.exceptions

public class DataFileNotFoundException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

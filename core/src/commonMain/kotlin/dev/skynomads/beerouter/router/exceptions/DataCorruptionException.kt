package dev.skynomads.beerouter.router.exceptions

public class DataCorruptionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

package dev.skynomads.beerouter.util

import kotlinx.io.Source
import kotlinx.io.readString

public fun readUtf8(source: Source): String = source.readString()

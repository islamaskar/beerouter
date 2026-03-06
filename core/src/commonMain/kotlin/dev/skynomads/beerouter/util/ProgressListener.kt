package dev.skynomads.beerouter.util


public interface ProgressListener {
    public fun updateProgress(task: String?, progress: Int)

    public val isCanceled: Boolean
}

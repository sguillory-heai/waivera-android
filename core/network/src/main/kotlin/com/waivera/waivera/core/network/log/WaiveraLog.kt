package com.waivera.waivera.core.network.log

/**
 * Minimal logging seam so pure-Kotlin code can report anomalies (an unknown hub event, an
 * unknown call status) without Android types. Messages must never contain tokens, message
 * bodies or personal data: log shapes and ids only.
 */
fun interface LogSink {
    fun log(priority: Priority, tag: String, message: String, error: Throwable?)

    enum class Priority { DEBUG, INFO, WARN, ERROR }
}

object WaiveraLog {
    @Volatile
    var sink: LogSink = LogSink { _, _, _, _ -> }

    fun d(tag: String, message: String) = sink.log(LogSink.Priority.DEBUG, tag, message, null)

    fun w(tag: String, message: String, error: Throwable? = null) = sink.log(LogSink.Priority.WARN, tag, message, error)

    fun e(tag: String, message: String, error: Throwable? = null) =
        sink.log(LogSink.Priority.ERROR, tag, message, error)
}

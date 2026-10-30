package com.waivera.waivera.debug.crash

import java.io.File

/** One report at a time in app-private storage; a newer crash replaces the older one. */
class CrashReportStore(filesDir: File) {
    private val file = File(File(filesDir, DIRECTORY), FILE_NAME)

    fun save(report: String) {
        file.parentFile?.mkdirs()
        val partial = File(file.parentFile, "$FILE_NAME.tmp")
        partial.writeText(report)
        if (!partial.renameTo(file)) {
            file.writeText(report)
            partial.delete()
        }
    }

    fun read(): String? = file.takeIf(File::isFile)?.readText()?.takeIf(String::isNotBlank)

    fun clear() {
        file.delete()
    }

    private companion object {
        const val DIRECTORY = "debug-crash"
        const val FILE_NAME = "last-crash.txt"
    }
}

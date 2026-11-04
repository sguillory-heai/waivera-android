package com.waivera.waivera.core.data.uploads

import com.waivera.waivera.core.data.model.UploadProblem
import com.waivera.waivera.core.data.model.UploadState
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.network.WaiveraJson
import java.io.File
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TaskUploadDirectory

/** One queued upload as it is kept on disk: the staged copy, where it goes and how far it got. */
@Serializable
data class UploadEntry(
    val id: String,
    val taskId: Int,
    /** The staged copy's path; null for a file refused before it was copied. */
    val localPath: String? = null,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val target: UploadTarget = UploadTarget(),
    val state: UploadState = UploadState.Queued,
    val problem: UploadProblem? = null,
    val savedName: String? = null,
    val createdAtMillis: Long = 0,
)

/**
 * The upload queue, kept as one small JSON file next to the staged copies in app storage so a
 * queued file survives the app being closed. Progress is held in memory only.
 */
@Singleton
@Suppress("TooManyFunctions") // Small queue operations, each under the one lock.
class TaskUploadStore @Inject constructor(@TaskUploadDirectory private val directory: File) {
    private val mutex = Mutex()
    private val entries = MutableStateFlow<List<UploadEntry>?>(null)
    private val progressFlow = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val serializer = ListSerializer(UploadEntry.serializer())

    /** Copies of files picked for Log activity, kept apart from the queue's own. */
    val activityDirectory: File get() = File(directory, ACTIVITY_DIRECTORY)

    val progress: StateFlow<Map<String, Float>> = progressFlow.asStateFlow()

    suspend fun all(): StateFlow<List<UploadEntry>?> {
        load()
        return entries.asStateFlow()
    }

    suspend fun snapshot(): List<UploadEntry> = load()

    suspend fun put(entry: UploadEntry) = edit { list -> list.filterNot { it.id == entry.id } + entry }

    suspend fun update(id: String, change: (UploadEntry) -> UploadEntry) =
        edit { list -> list.map { if (it.id == id) change(it) else it } }

    suspend fun remove(id: String) {
        val removed = mutex.withLock {
            val list = loadLocked()
            val gone = list.firstOrNull { it.id == id }
            saveLocked(list.filterNot { it.id == id })
            gone
        }
        removed?.localPath?.let { File(it).delete() }
        setProgress(id, null)
    }

    fun setProgress(id: String, fraction: Float?) = progressFlow.update {
        if (fraction == null) it - id else it + (id to fraction)
    }

    /** Sign-out: every queued file and its copy go. */
    suspend fun clear() {
        mutex.withLock {
            directory.deleteRecursively()
            entries.value = emptyList()
        }
        progressFlow.value = emptyMap()
    }

    private suspend fun edit(change: (List<UploadEntry>) -> List<UploadEntry>) = mutex.withLock {
        saveLocked(change(loadLocked()))
    }

    private suspend fun load(): List<UploadEntry> = mutex.withLock { loadLocked() }

    private fun loadLocked(): List<UploadEntry> = entries.value ?: run {
        val file = File(directory, QUEUE_FILE)
        val loaded = runCatching { WaiveraJson.decodeFromString(serializer, file.readText()) }.getOrNull().orEmpty()
        // A send cut off by the app closing goes back in line.
        loaded.map { if (it.state == UploadState.Uploading) it.copy(state = UploadState.Queued) else it }
            .also { entries.value = it }
    }

    private fun saveLocked(list: List<UploadEntry>) {
        directory.mkdirs()
        val file = File(directory, QUEUE_FILE)
        val temporary = File(directory, "$QUEUE_FILE.tmp")
        temporary.writeText(WaiveraJson.encodeToString(serializer, list))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
        entries.value = list
    }

    val stagingDirectory: File get() = File(directory, STAGED)

    private companion object {
        const val QUEUE_FILE = "queue.json"
        const val ACTIVITY_DIRECTORY = "activity"
        const val STAGED = "files"
    }
}

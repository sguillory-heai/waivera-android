package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.live.SignOutCleaner
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The last attachment shared with another app stays in the cache until the next share; it is
 * the previous person's file, so every session end empties the folder (AS-10).
 */
class ShareFolderCleaner(private val cacheDir: File) : SignOutCleaner {
    override suspend fun clear() {
        withContext(Dispatchers.IO) { File(cacheDir, DIRECTORY).deleteRecursively() }
    }

    companion object {
        /** Under `cacheDir`; must match `feature_chat_file_paths.xml`. */
        const val DIRECTORY = "feature_chat_share"
    }
}

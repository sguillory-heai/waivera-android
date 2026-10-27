package com.waivera.waivera.feature.chat.message

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ShareFolderCleanerTest {
    @Test
    fun aSessionEndEmptiesTheSharedAttachmentFolderOnly() = runTest {
        val cacheDir = Files.createTempDirectory("cache").toFile()
        val shared = cacheDir.resolve(ShareFolderCleaner.DIRECTORY).apply { mkdirs() }.resolve("payslip.pdf")
        shared.writeText("private")
        val other = cacheDir.resolve("waivera_images").apply { mkdirs() }.resolve("keep.png").apply { writeText("x") }

        ShareFolderCleaner(cacheDir).clear()

        assertFalse(shared.exists(), "the previous person's shared file is gone")
        assertTrue(other.exists(), "other caches have their own cleaners")
        cacheDir.deleteRecursively()
    }
}

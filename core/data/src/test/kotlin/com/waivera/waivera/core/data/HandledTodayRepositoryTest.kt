package com.waivera.waivera.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.waivera.waivera.core.data.model.HandledObservation
import com.waivera.waivera.core.data.today.DataStoreHandledTodayRepository
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

/** The tally's DataStore: it persists, advances atomically, survives bad data and clears on sign-out. */
class HandledTodayRepositoryTest {
    private val directory: File = Files.createTempDirectory("handled-today").toFile()
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val store = PreferenceDataStoreFactory.create(scope = scope) {
        File(directory, "handled_today.preferences_pb")
    }
    private val repository = DataStoreHandledTodayRepository(store)
    private val day = LocalDate.of(2026, 10, 28)

    private fun seen(vararg open: String) = HandledObservation(userId = "me", day = day, open = open.toSet())

    @After
    fun tearDown() {
        scope.cancel()
        directory.deleteRecursively()
    }

    @Test
    fun recordsAndReadsBack() = runTest {
        assertNull(repository.tally.first())
        repository.record(seen("dm-1", "dm-2"))
        val tally = repository.record(seen("dm-2"))
        assertEquals(1, tally.handled)
        assertEquals(tally, repository.tally.first())
    }

    @Test
    fun observationsRacingNeverLoseACount() = runTest {
        repository.record(seen("dm-1", "dm-2", "dm-3"))
        listOf(seen("dm-2", "dm-3"), seen("dm-3")).map { async { repository.record(it) } }.awaitAll()
        // Whichever lands first, the two that left are counted once each and none is lost.
        assertEquals(2, checkNotNull(repository.tally.first()).handled)
    }

    @Test
    fun anUnreadableValueReadsAsNoTally() = runTest {
        store.edit { it[stringPreferencesKey("tally")] = "{not json" }
        assertNull(repository.tally.first())
        assertEquals(0, repository.record(seen("dm-1")).handled)
    }

    @Test
    fun signOutForgetsTheTally() = runTest {
        repository.record(seen("dm-1"))
        repository.clear()
        assertNull(repository.tally.first())
    }
}

package com.waivera.waivera.core.data.today

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.model.HandledObservation
import com.waivera.waivera.core.data.model.HandledTally
import com.waivera.waivera.core.data.model.HandledToday
import com.waivera.waivera.core.data.repository.HandledTodayRepository
import com.waivera.waivera.core.network.WaiveraJson
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The plain preferences file that holds the handled-today tally (nothing in it is sensitive). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class HandledTodayStore

/**
 * The tally in its own DataStore file, one JSON value. [record] advances and saves in one
 * transaction, so two observations racing never lose a count. Keys are opaque item ids, never
 * names or message text. Sign-out clears it.
 */
@Singleton
class DataStoreHandledTodayRepository @Inject constructor(
    @HandledTodayStore private val store: DataStore<Preferences>,
) : HandledTodayRepository,
    SignOutCleaner {
    override val tally: Flow<HandledTally?> = store.data.map { read(it) }.distinctUntilChanged()

    override suspend fun record(observation: HandledObservation): HandledTally {
        var next: HandledTally? = null
        store.edit { prefs ->
            val advanced = HandledToday.advance(read(prefs), observation)
            prefs[TALLY] = WaiveraJson.encodeToString(HandledTally.serializer(), advanced)
            next = advanced
        }
        return checkNotNull(next)
    }

    override suspend fun clear() {
        store.edit { it.clear() }
    }

    /** A value an older build wrote in another shape reads as no tally yet, never as a crash. */
    private fun read(prefs: Preferences): HandledTally? = prefs[TALLY]?.let { raw ->
        runCatching { WaiveraJson.decodeFromString(HandledTally.serializer(), raw) }.getOrNull()
    }

    private companion object {
        val TALLY = stringPreferencesKey("tally")
    }
}

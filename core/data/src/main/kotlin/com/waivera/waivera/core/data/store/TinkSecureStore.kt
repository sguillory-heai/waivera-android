package com.waivera.waivera.core.data.store

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.waivera.waivera.core.network.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

private val Context.secureDataStore: DataStore<Preferences> by preferencesDataStore(name = "waivera_secure")

/**
 * [SecureStore] over Preferences DataStore with every value sealed by a Tink AES-256-GCM AEAD
 * whose keyset is wrapped by an Android Keystore master key. Keys are the plain names; the key
 * name is bound as associated data so a value cannot be moved to another key.
 */
@Singleton
class TinkSecureStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : SecureStore {
    private val aead: Aead by lazy {
        AeadConfig.register()
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
            .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    override suspend fun get(key: String): String? = withContext(io) {
        val sealed = context.secureDataStore.data.first()[stringPreferencesKey(key)] ?: return@withContext null
        try {
            String(aead.decrypt(Base64.getDecoder().decode(sealed), key.toByteArray()), Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            // A restored backup or a reset keystore makes old values unreadable; treat as absent.
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override suspend fun put(key: String, value: String?) {
        withContext(io) {
            val sealed = value?.let {
                Base64.getEncoder().encodeToString(aead.encrypt(it.toByteArray(Charsets.UTF_8), key.toByteArray()))
            }
            context.secureDataStore.edit { prefs ->
                if (sealed ==
                    null
                ) {
                    prefs.remove(stringPreferencesKey(key))
                } else {
                    prefs[stringPreferencesKey(key)] = sealed
                }
            }
        }
    }

    override suspend fun clear(keep: Set<String>) {
        withContext(io) {
            context.secureDataStore.edit { prefs ->
                prefs.asMap().keys.filter { it.name !in keep }.forEach { prefs -= it }
            }
        }
    }

    private companion object {
        // Holds only the Keystore-wrapped Tink keyset, never app data (AGENTS.md: tokens never in plain prefs).
        const val KEYSET_PREFS = "waivera_secure_keyset"
        const val KEYSET_NAME = "waivera_secure_keyset"
        const val MASTER_KEY_URI = "android-keystore://waivera_secure_master_key"
    }
}

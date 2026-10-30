package com.waivera.waivera.debug.crash

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Installs [CrashCatcher] before `Application.onCreate`, so start-up crashes are caught too.
 * Declared only in the debug manifest; release builds have neither this class nor the provider.
 */
class CrashCatcherInstaller : ContentProvider() {
    override fun onCreate(): Boolean {
        context?.let(CrashCatcher::install)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        0
}

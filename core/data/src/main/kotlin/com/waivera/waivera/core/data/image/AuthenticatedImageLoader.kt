package com.waivera.waivera.core.data.image

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.waivera.waivera.core.data.live.SignOutCleaner
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.network.di.AuthenticatedHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * The app's Coil 3 [ImageLoader]. It fetches through the authenticated OkHttp client, whose
 * interceptor adds the Firebase token for the API host only, so relative attachment URLs
 * resolved against the API base load (G-15) while CDN avatars never see the token.
 * It also loads [AvatarModel]s through `PeopleRepository.avatar`.
 * The app should install it with `SingletonImageLoader.setSafe { loaderProvider.get().imageLoader }`.
 */
@Singleton
class AuthenticatedImageLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @AuthenticatedHttpClient private val client: OkHttpClient,
    private val people: Provider<PeopleRepository>,
) : SignOutCleaner {
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { client }))
                // Avatars by user id ([AvatarModel]), for the design system's avatar.
                add(AvatarFetcher.Key)
                add(AvatarFetcher.Factory(people))
            }
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_PERCENT).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(DISK_DIR))
                    .maxSizeBytes(DISK_BYTES)
                    .build()
            }
            .build()
    }

    override suspend fun clear() {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
    }

    private companion object {
        const val MEMORY_PERCENT = 0.20
        const val DISK_DIR = "waivera_images"
        const val DISK_BYTES = 64L * 1024 * 1024
    }
}

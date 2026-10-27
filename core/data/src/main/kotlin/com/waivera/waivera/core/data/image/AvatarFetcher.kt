package com.waivera.waivera.core.data.image

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import com.waivera.waivera.core.data.repository.PeopleRepository
import javax.inject.Provider
import okio.Buffer

/** Loads [AvatarModel]s through [PeopleRepository.avatar]; a person without a photo is an error result. */
internal class AvatarFetcher(
    private val model: AvatarModel,
    private val options: Options,
    private val people: PeopleRepository,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = people.avatar(model.userId) ?: throw NoAvatarException()
        return SourceFetchResult(
            source = ImageSource(source = Buffer().write(bytes), fileSystem = options.fileSystem),
            mimeType = null,
            dataSource = DataSource.MEMORY,
        )
    }

    /** [people] is a provider: the loader is built at startup, before the repositories are needed. */
    class Factory(private val people: Provider<PeopleRepository>) : Fetcher.Factory<AvatarModel> {
        override fun create(data: AvatarModel, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.isLoadable) AvatarFetcher(data, options, people.get()) else null
    }

    /** Memory-cache key; the repository's own cache decides when bytes are fetched again. */
    object Key : Keyer<AvatarModel> {
        override fun key(data: AvatarModel, options: Options): String = "avatar:${data.userId}"
    }

    /** No message: it never reaches the UI, which shows initials instead. */
    class NoAvatarException : IllegalStateException()
}

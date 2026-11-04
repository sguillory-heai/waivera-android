package com.waivera.waivera.core.data.di

import com.waivera.waivera.core.data.auth.GatewayTokenProvider
import com.waivera.waivera.core.data.live.LiveActivityRepository
import com.waivera.waivera.core.data.live.LiveAttachmentRepository
import com.waivera.waivera.core.data.live.LiveBookmarkRepository
import com.waivera.waivera.core.data.live.LiveCalendarRepository
import com.waivera.waivera.core.data.live.LiveConversationRepository
import com.waivera.waivera.core.data.live.LiveLinkLookupRepository
import com.waivera.waivera.core.data.live.LiveMessageRepository
import com.waivera.waivera.core.data.live.LiveNoticeRepository
import com.waivera.waivera.core.data.live.LivePeopleRepository
import com.waivera.waivera.core.data.live.LiveProfileRepository
import com.waivera.waivera.core.data.live.LivePushRegistrationRepository
import com.waivera.waivera.core.data.live.LiveRealtimeStatusRepository
import com.waivera.waivera.core.data.live.LiveRequestRepository
import com.waivera.waivera.core.data.live.LiveSessionRepository
import com.waivera.waivera.core.data.live.LiveSettingsRepository
import com.waivera.waivera.core.data.live.LiveTaskFileRepository
import com.waivera.waivera.core.data.live.LiveTaskRepository
import com.waivera.waivera.core.data.live.LiveTaskReviewRepository
import com.waivera.waivera.core.data.live.LiveThreadRepository
import com.waivera.waivera.core.data.repository.ActivityRepository
import com.waivera.waivera.core.data.repository.AttachmentRepository
import com.waivera.waivera.core.data.repository.BookmarkRepository
import com.waivera.waivera.core.data.repository.CalendarRepository
import com.waivera.waivera.core.data.repository.ConversationRepository
import com.waivera.waivera.core.data.repository.DataLayerStarter
import com.waivera.waivera.core.data.repository.DurableMessageSender
import com.waivera.waivera.core.data.repository.LinkLookupRepository
import com.waivera.waivera.core.data.repository.MessageRepository
import com.waivera.waivera.core.data.repository.NoticeRepository
import com.waivera.waivera.core.data.repository.PeopleRepository
import com.waivera.waivera.core.data.repository.ProfileRepository
import com.waivera.waivera.core.data.repository.PushRegistrationRepository
import com.waivera.waivera.core.data.repository.RealtimeStatusRepository
import com.waivera.waivera.core.data.repository.RequestRepository
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.data.repository.TaskFileRepository
import com.waivera.waivera.core.data.repository.TaskRepository
import com.waivera.waivera.core.data.repository.TaskReviewRepository
import com.waivera.waivera.core.data.repository.TaskUploadRepository
import com.waivera.waivera.core.data.repository.ThreadRepository
import com.waivera.waivera.core.data.sync.LiveDataLayerStarter
import com.waivera.waivera.core.data.uploads.LiveTaskUploadRepository
import com.waivera.waivera.core.network.AuthTokenProvider
import com.waivera.waivera.core.realtime.DefaultRealtimeClient
import com.waivera.waivera.core.realtime.RealtimeClient
import dagger.Binds
import dagger.Module
import dagger.hilt.migration.DisableInstallInCheck

/**
 * Interface-to-live bindings. Not installed on its own, so exactly one module decides between
 * live and demo:
 *  - release builds: the app's release source set has a module
 *    `@Module(includes = [LiveRepositoriesModule::class]) @InstallIn(SingletonComponent::class)`;
 *  - debug builds: core:demo's `DemoSwitchModule` picks live or demo per launch.
 */
@Module
@DisableInstallInCheck
@Suppress("TooManyFunctions")
interface LiveRepositoriesModule {
    @Binds
    fun session(impl: LiveSessionRepository): SessionRepository

    @Binds
    fun conversations(impl: LiveConversationRepository): ConversationRepository

    @Binds
    fun messages(impl: LiveMessageRepository): MessageRepository

    @Binds
    fun durableSends(impl: LiveMessageRepository): DurableMessageSender

    @Binds
    fun threads(impl: LiveThreadRepository): ThreadRepository

    @Binds
    fun attachments(impl: LiveAttachmentRepository): AttachmentRepository

    @Binds
    fun bookmarks(impl: LiveBookmarkRepository): BookmarkRepository

    @Binds
    fun activity(impl: LiveActivityRepository): ActivityRepository

    @Binds
    fun notices(impl: LiveNoticeRepository): NoticeRepository

    @Binds
    fun tasks(impl: LiveTaskRepository): TaskRepository

    @Binds
    fun taskFiles(impl: LiveTaskFileRepository): TaskFileRepository

    @Binds
    fun taskUploads(impl: LiveTaskUploadRepository): TaskUploadRepository

    @Binds
    fun taskReview(impl: LiveTaskReviewRepository): TaskReviewRepository

    @Binds
    fun links(impl: LiveLinkLookupRepository): LinkLookupRepository

    @Binds
    fun requests(impl: LiveRequestRepository): RequestRepository

    @Binds
    fun people(impl: LivePeopleRepository): PeopleRepository

    @Binds
    fun calendar(impl: LiveCalendarRepository): CalendarRepository

    @Binds
    fun profile(impl: LiveProfileRepository): ProfileRepository

    @Binds
    fun settings(impl: LiveSettingsRepository): SettingsRepository

    @Binds
    fun push(impl: LivePushRegistrationRepository): PushRegistrationRepository

    @Binds
    fun realtimeStatus(impl: LiveRealtimeStatusRepository): RealtimeStatusRepository

    @Binds
    fun realtime(impl: DefaultRealtimeClient): RealtimeClient

    @Binds
    fun tokens(impl: GatewayTokenProvider): AuthTokenProvider

    @Binds
    fun starter(impl: LiveDataLayerStarter): DataLayerStarter
}

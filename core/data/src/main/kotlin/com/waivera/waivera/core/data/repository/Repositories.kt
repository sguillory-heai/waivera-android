package com.waivera.waivera.core.data.repository

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.AttachmentSource
import com.waivera.waivera.core.data.model.Bookmark
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.EmployeeFilePage
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.MessageThread
import com.waivera.waivera.core.data.model.MessageTimeline
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.Organization
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.PersonDetails
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.data.model.currentUserId
import java.io.File
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

/*
 * Every suspend function returns Result, failing with a WaiveraError (never a raw exception or
 * server text). Flows never throw: errors arrive inside Synced.status.
 */

interface SessionRepository {
    val state: StateFlow<SessionState>

    suspend fun signIn(email: String, password: String): Result<Unit>

    /**
     * Stops realtime, unregisters push, clears caches and signs out of Firebase, in that order.
     * Any other end of the session (rejected bootstrap, revoked Firebase user) runs the same cleanup.
     */
    suspend fun signOut(): Result<Unit>

    /**
     * The backend user id once the session has settled (null when signed out). Waits at most
     * [timeoutMillis] for a cold start's bootstrap; a live session then answers from its cache.
     */
    suspend fun settledUserId(timeoutMillis: Long): String? =
        withTimeoutOrNull(timeoutMillis) { state.first { it !is SessionState.Initializing } }?.currentUserId

    /**
     * Whether a push may be shown (AS-02): only for a signed-in session. A cold start from a push
     * is still bootstrapping ([SessionState.Initializing] only happens with a Firebase user), so
     * a session that has not settled after [timeoutMillis] counts as signed in.
     */
    suspend fun acceptsPushes(timeoutMillis: Long): Boolean {
        val settled = withTimeoutOrNull(timeoutMillis) { state.first { it !is SessionState.Initializing } }
        return settled == null || settled.currentUserId != null
    }

    suspend fun sendPasswordReset(email: String): Result<Unit>

    /** Re-runs the backend bootstrap (after [SessionState.BootstrapFailed] or a profile edit). */
    suspend fun refresh(): Result<Unit>

    /** Calls `confirm-password-change` after the user set a new password in Firebase. */
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
}

@Suppress("TooManyFunctions")
interface ConversationRepository {
    /** Inbox, newest activity first. */
    fun observeConversations(): Flow<Synced<List<Conversation>>>

    fun observeConversation(conversationId: Long): Flow<Conversation?>

    /** Total unread across conversations, for the tab badge. */
    fun observeUnreadTotal(): Flow<Int>

    suspend fun refresh(): Result<Unit>

    /** Fetches one conversation (deep link, push) without trusting its unreadCount (G-14). */
    suspend fun refreshConversation(conversationId: Long): Result<Conversation>

    /** Reuses the exact-participant DM or group when one exists, else creates it. Returns its id. */
    suspend fun openConversation(participantIds: List<String>): Result<Long>

    suspend fun conversationForEvent(calendarEventId: String): Result<Long>

    suspend fun leave(conversationId: Long): Result<Unit>

    /** Marks the whole conversation read up to [lastVisibleMessageId]. */
    suspend fun markRead(conversationId: Long, lastVisibleMessageId: Long): Result<Unit>

    /** People typing in [conversationId] right now (expires after 5 s without a refresh). */
    fun observeTyping(conversationId: Long): Flow<Set<String>>

    /** [observeTyping] for every conversation at once (the inbox); empty sets are left out. */
    fun observeAllTyping(): Flow<Map<Long, Set<String>>>

    /**
     * The web lobby URL for the conversation's call (03 §19): joins the live room, or creates
     * one when none is live. Fails with [com.waivera.waivera.core.network.error.WaiveraError.Configuration]
     * when the build has no web base URL.
     */
    suspend fun callLobbyUrl(conversationId: Long): Result<String>

    /** Call on each composer change; throttled to one hub event per 3 s. */
    fun onComposerChanged(conversationId: Long, isEmpty: Boolean)
}

interface MessageRepository {
    /**
     * Server messages merged with unsent outbox rows, oldest first. Collecting also joins the
     * conversation's hub group and resyncs its tail after every reconnect.
     */
    fun observeTimeline(conversationId: Long): Flow<Synced<MessageTimeline>>

    suspend fun refreshLatest(conversationId: Long): Result<Unit>

    /** Loads the next older page; false when there is nothing older. */
    suspend fun loadOlder(conversationId: Long): Result<Boolean>

    /** Loads messages around [messageId] (search, activity, bookmark jumps). */
    suspend fun loadAround(conversationId: Long, messageId: Long): Result<Unit>

    /**
     * Queues a message and returns its client id at once (optimistic). Sends over the hub when
     * connected and there is no attachment, otherwise over REST, with the same id either way.
     */
    suspend fun send(
        conversationId: Long,
        text: String,
        doc: JsonElement? = null,
        attachment: AttachmentSource? = null,
        parentMessageId: Long? = null,
    ): String

    /** Re-queues a failed message. */
    suspend fun retry(clientMessageId: String): Result<Unit>

    suspend fun discard(clientMessageId: String)

    suspend fun edit(messageId: Long, text: String, doc: JsonElement? = null): Result<Message>

    suspend fun delete(messageId: Long): Result<Unit>

    /** Toggles [emoji]: same emoji removes, a different one replaces. Hub only (G-01). */
    suspend fun react(messageId: Long, emoji: String): Result<Unit>
}

interface ThreadRepository {
    /** Collecting joins the parent conversation's group, which carries thread replies. */
    fun observeThread(conversationId: Long, rootMessageId: Long): Flow<Synced<MessageThread>>

    suspend fun refresh(rootMessageId: Long): Result<Unit>

    suspend fun markRead(conversationId: Long, rootMessageId: Long, lastVisibleMessageId: Long): Result<Unit>

    /**
     * The thread root of a cached message, or null when it is a main-timeline message or not
     * cached. Lets push suppression tell a thread reply from a main message.
     */
    suspend fun cachedRootOf(messageId: Long): Long? = null
}

interface AttachmentRepository {
    /** Downloads into a bounded cache; 423 fails with AttachmentRejected (G-15). */
    suspend fun download(messageId: Long, fileName: String?): Result<File>

    /** The absolute URL for a relative attachment path, for the authenticated image loader. */
    fun resolve(attachmentUrl: String): String?
}

interface BookmarkRepository {
    fun observeBookmarks(): Flow<Synced<List<Bookmark>>>

    fun observeBookmarkedIds(): Flow<Set<Long>>

    suspend fun refresh(): Result<Unit>

    /** Returns the new state. */
    suspend fun toggle(messageId: Long): Result<Boolean>
}

interface ActivityRepository {
    fun observeActivity(): Flow<Synced<ActivityFeed>>

    suspend fun refresh(): Result<Unit>

    suspend fun loadMore(): Result<Boolean>

    suspend fun markRead(activityId: Long): Result<Unit>

    suspend fun markAllRead(): Result<Unit>
}

interface PeopleRepository {
    /** Staff who can be messaged, sorted by name. */
    fun observeDirectory(): Flow<Synced<List<Person>>>

    fun observePerson(userId: String): Flow<Person?>

    suspend fun refresh(): Result<Unit>

    /** Avatar image bytes (decoded from the API's data URI), cached; null when the user has none. */
    suspend fun avatar(userId: String): ByteArray?

    /** Another person's account details (roles, organization) from `GetUserDetailsByUserID`. */
    suspend fun details(userId: String): Result<PersonDetails>
}

interface CalendarRepository {
    /** The employee's saved zone that every schedule value is shown in. */
    val zone: Flow<ZoneId>

    /** Feed for [range]; refreshes on first collection when stale. */
    fun observeRange(range: DateRange): Flow<Synced<ScheduleFeed>>

    suspend fun refresh(range: DateRange): Result<Unit>

    fun observeMilestones(): Flow<Synced<List<Milestone>>>

    suspend fun refreshMilestones(): Result<Unit>

    /**
     * One event by id (push, deep link, the event screen), from
     * `GET api/CalendarEvent/GetEventbyId/{id}`; refreshes on first collection when stale or
     * missing. `data == null` after a sync means the event was not found.
     */
    fun observeEvent(eventId: String): Flow<Synced<ScheduleEvent?>>

    suspend fun refreshEvent(eventId: String): Result<Unit>

    /** On success every cached copy of the event (single event and feeds) shows the new answer. */
    suspend fun respondToInvitation(eventId: String, accept: Boolean): Result<Unit>

    /** Creates or reuses the meeting room and returns the web lobby URL to open. */
    suspend fun meetingLobbyUrl(eventId: String): Result<String>

    companion object {
        /** The 42-cell month grid range, starting the Sunday on or before the 1st. */
        fun monthGrid(month: YearMonth): DateRange =
            com.waivera.waivera.core.data.calendar.CalendarMath.monthGrid(month)
    }
}

interface ProfileRepository {
    fun observeMyProfile(): Flow<Synced<EmployeeProfile?>>

    suspend fun refresh(): Result<Unit>

    suspend fun profile(employeeId: String): Result<EmployeeProfile>

    suspend fun timeOff(userId: String): Result<TimeOffSummary>

    suspend fun files(userId: String, page: Int): Result<EmployeeFilePage>

    suspend fun updateContact(phoneNumber: String?, timeZoneId: String?): Result<Unit>

    /** The session's organization from `OrganizationDetails`, matched by id; null when absent. */
    suspend fun organization(): Result<Organization?>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setTheme(theme: ThemePreference)

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setNotificationsEnabled(enabled: Boolean)

    suspend fun setCategoryEnabled(category: NotificationCategory, enabled: Boolean)

    suspend fun setSound(enabled: Boolean)

    suspend fun setVibration(enabled: Boolean)

    suspend fun setPreviewContent(enabled: Boolean)
}

interface PushRegistrationRepository {
    /** Registers [token] with the full preference set (G-08); skipped when nothing changed. */
    suspend fun register(token: String, preferences: NotificationPreferences): Result<Unit>

    suspend fun unregister(): Result<Unit>

    /** From `FirebaseMessagingService.onNewToken`: stores and re-registers without touching the hub. */
    suspend fun onNewToken(token: String)
}

interface RealtimeStatusRepository {
    val status: StateFlow<RealtimeStatus>

    fun retryNow()

    /**
     * The conversation on screen, or null, with [threadRootMessageId] set when the screen is one
     * of its threads rather than its timeline. The app shell is the single owner: it sets this
     * from navigation state (top route is a conversation or thread and the activity is resumed)
     * and clears it otherwise. Feature screens never call it.
     */
    fun setVisibleConversation(conversationId: Long?, threadRootMessageId: Long? = null)

    /**
     * Foreground push suppression: only when the hub is connected and that conversation's
     * timeline or one of its threads is on screen. The notification layer narrows it to the
     * exact surface, so a main-timeline message still alerts while only a thread is open.
     */
    fun shouldSuppressNotification(conversationId: Long): Boolean
}

/** Starts the background pieces (realtime lifecycle, event sync, push sync). Call once from Application. */
fun interface DataLayerStarter {
    fun start()
}

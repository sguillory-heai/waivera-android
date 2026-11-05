package com.waivera.waivera.core.data.repository

import com.waivera.waivera.core.data.model.ActivityFeed
import com.waivera.waivera.core.data.model.ActivityType
import com.waivera.waivera.core.data.model.AppSettings
import com.waivera.waivera.core.data.model.ApprovalMode
import com.waivera.waivera.core.data.model.ApprovalOutcome
import com.waivera.waivera.core.data.model.ApprovalPlan
import com.waivera.waivera.core.data.model.ApprovalSend
import com.waivera.waivera.core.data.model.AttachmentSource
import com.waivera.waivera.core.data.model.AttendeeOption
import com.waivera.waivera.core.data.model.Bookmark
import com.waivera.waivera.core.data.model.ChangeOptions
import com.waivera.waivera.core.data.model.ChangeReason
import com.waivera.waivera.core.data.model.ChangeRequest
import com.waivera.waivera.core.data.model.Conversation
import com.waivera.waivera.core.data.model.DateRange
import com.waivera.waivera.core.data.model.DelayFilter
import com.waivera.waivera.core.data.model.DelayPeriod
import com.waivera.waivera.core.data.model.DocumentCategory
import com.waivera.waivera.core.data.model.EmployeeClosure
import com.waivera.waivera.core.data.model.EmployeeFilePage
import com.waivera.waivera.core.data.model.EmployeeProfile
import com.waivera.waivera.core.data.model.EventDraft
import com.waivera.waivera.core.data.model.FileTemplate
import com.waivera.waivera.core.data.model.FormDraft
import com.waivera.waivera.core.data.model.LateContext
import com.waivera.waivera.core.data.model.LateEdit
import com.waivera.waivera.core.data.model.LeaveBalance
import com.waivera.waivera.core.data.model.LeaveDraft
import com.waivera.waivera.core.data.model.LeavePreview
import com.waivera.waivera.core.data.model.LeaveType
import com.waivera.waivera.core.data.model.LinkedFaq
import com.waivera.waivera.core.data.model.LinkedResource
import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.data.model.MessageThread
import com.waivera.waivera.core.data.model.MessageTimeline
import com.waivera.waivera.core.data.model.Milestone
import com.waivera.waivera.core.data.model.NewActivity
import com.waivera.waivera.core.data.model.Notice
import com.waivera.waivera.core.data.model.NoticeFeed
import com.waivera.waivera.core.data.model.NotificationCategory
import com.waivera.waivera.core.data.model.NotificationPreferences
import com.waivera.waivera.core.data.model.Organization
import com.waivera.waivera.core.data.model.ParticipantProfile
import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.PersonDetails
import com.waivera.waivera.core.data.model.PreparedFile
import com.waivera.waivera.core.data.model.RealtimeStatus
import com.waivera.waivera.core.data.model.RequestAccess
import com.waivera.waivera.core.data.model.RequestOutcome
import com.waivera.waivera.core.data.model.RequiredFile
import com.waivera.waivera.core.data.model.ScheduleEvent
import com.waivera.waivera.core.data.model.ScheduleFeed
import com.waivera.waivera.core.data.model.SendBackOption
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.data.model.TaskActivity
import com.waivera.waivera.core.data.model.TaskAttachment
import com.waivera.waivera.core.data.model.TaskCounts
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskFile
import com.waivera.waivera.core.data.model.TaskPage
import com.waivera.waivera.core.data.model.TaskQuery
import com.waivera.waivera.core.data.model.TaskScope
import com.waivera.waivera.core.data.model.TaskSummary
import com.waivera.waivera.core.data.model.TaskUpload
import com.waivera.waivera.core.data.model.ThemePreference
import com.waivera.waivera.core.data.model.TimeOffSummary
import com.waivera.waivera.core.data.model.TrainingCredit
import com.waivera.waivera.core.data.model.TrainingDecision
import com.waivera.waivera.core.data.model.UploadTarget
import com.waivera.waivera.core.data.model.currentUserId
import com.waivera.waivera.core.network.api.TaskApi
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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

    /**
     * Conversations holding a message of mine that the outbox gave up on, so the inbox and Today
     * can say "Not sent" without opening the chat (UI2-09).
     */
    fun observeFailedSends(): Flow<Set<Long>> = flowOf(emptySet())

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

/**
 * Sending for a caller whose process may end right after it returns: an inline reply from a
 * notification, where the receiver finishes and the system may stop the app (IF-03).
 */
fun interface DurableMessageSender {
    /**
     * Queues like [MessageRepository.send], then returns only once a durable retry is scheduled
     * and the first attempt has finished, so the message is never left queued with nothing to
     * send it.
     */
    suspend fun sendDurably(conversationId: Long, text: String, parentMessageId: Long?): String
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

/** The web bell's notifications (task, approval, signature, survey, meeting, time off). */
interface NoticeRepository {
    fun observeNotices(): Flow<Synced<NoticeFeed>>

    suspend fun refresh(): Result<Unit>

    /** A notification that arrived live (hub) or by push; shown at once, then confirmed. */
    suspend fun onArrived(notice: Notice)

    /** Optimistic: the row reads as read at once and rolls back if the server refuses. */
    suspend fun markRead(id: String): Result<Unit>

    suspend fun markAllRead(): Result<Unit>

    /**
     * Read on another device (the web or another phone): the row, or with a null id every row,
     * reads as read here. Nothing is sent; the server already has it.
     */
    suspend fun onReadElsewhere(notificationId: String?) = Unit
}

/** The read-only participant profile and the participant's open tasks. */
interface ParticipantRepository {
    /** Fails with `Forbidden` when the viewer may not see this participant. */
    suspend fun profile(id: Int): Result<ParticipantProfile>

    /** Open tasks about the participant that the viewer can see, earliest due first (undated last). */
    suspend fun openTasks(id: Int): Result<List<TaskSummary>>
}

/**
 * The person's own tasks, read straight from the server each time: the list is small, changes
 * on the web, and the server is authoritative. Logging activity is the only write.
 */
interface TaskRepository {
    suspend fun tasks(query: TaskQuery, page: Int, pageSize: Int = TaskApi.PAGE_SIZE): Result<TaskPage>

    /** How many open tasks a list holds, and how many of them are overdue. */
    suspend fun counts(scope: TaskScope, assignedToMe: Boolean = false): Result<TaskCounts>

    suspend fun task(id: Int): Result<TaskDetail>

    /** Oldest first. */
    suspend fun activity(taskId: Int): Result<List<TaskActivity>>

    suspend fun activityTypes(): Result<List<ActivityType>>

    suspend fun logActivity(activity: NewActivity): Result<Unit>

    /** The task's files, newest first. */
    suspend fun attachments(taskId: Int): Result<List<TaskAttachment>>
}

/**
 * What Waivera links in chat messages point at, for their previews. Each read carries the
 * reader's own access: a task in another agency, a draft resource or an FAQ the reader can't see
 * fails with [com.waivera.waivera.core.network.error.WaiveraError.NotFound] or `Forbidden`.
 */
interface LinkLookupRepository {
    /** The task people call by [number] in the reader's agency. */
    suspend fun task(number: Int): Result<TaskDetail>

    suspend fun resource(id: String): Result<LinkedResource>

    /** Every published FAQ article the reader may open. */
    suspend fun faqs(): Result<List<LinkedFaq>>
}

/**
 * Reads task files for the viewer. A file lives in the app's private cache only while it is
 * open: [close] deletes it, and sign-out empties the cache.
 */
interface TaskFileRepository {
    suspend fun open(taskId: Int, file: TaskAttachment): Result<java.io.File>

    /**
     * The whole file, of any type, for saving to the phone or sharing; up to
     * [TaskAttachment.MAX_EXPORT_BYTES]. Closed with [close] like a viewed one.
     */
    suspend fun export(taskId: Int, file: TaskAttachment): Result<java.io.File>

    /** An external training certificate (a PDF) under review; closed with [close]. */
    suspend fun certificate(requestId: Int): Result<java.io.File>

    fun close(file: java.io.File)
}

/**
 * Files going onto tasks. Picked files are checked and copied into app storage at once, then
 * sent one at a time by a background job that outlives the screen and the app, and is retried
 * when the connection comes back. Sign-out drops everything still queued.
 */
/** Who a prepared pick is for: each keeps its own copy so one pick never removes the other's. */
enum class PickPurpose { Activity, Evidence }

interface TaskUploadRepository {
    /** This task's uploads that the server does not list yet, oldest first. */
    fun uploads(taskId: Int): Flow<List<TaskUpload>>

    /** The task's required files still missing. */
    suspend fun requiredFiles(taskId: Int): Result<List<RequiredFile>>

    /** Employee file categories, for an extra file on an employee task. */
    suspend fun categories(): Result<List<DocumentCategory>>

    /**
     * Checks and queues [uris] (`content://` or `file://`). A file that fails the web's checks
     * (type, size, empty) is listed as failed with its reason and is not sent.
     */
    suspend fun add(taskId: Int, uris: List<String>, target: UploadTarget)

    /** Sends a failed upload again. */
    fun retry(id: String)

    /** Forgets an upload: a failed one the person dismissed, or a finished one now on the server's list. */
    fun dismiss(id: String)

    /**
     * Checks and copies a file picked for Log activity. Fails with `UploadRefused` and the
     * app's reason when the file can't be sent. Only the latest prepared file is kept.
     */
    suspend fun prepare(uri: String, purpose: PickPurpose = PickPurpose.Activity): Result<PreparedFile>

    /**
     * Sends [file] now, under [template]'s name and type when there is one (sprout_web logs the
     * activity only after its file is up). Returns the name the server saved it as. Fails with
     * `UploadRefused` carrying the app's reason.
     */
    suspend fun sendNow(taskId: Int, file: PreparedFile, template: FileTemplate?): Result<String?>

    /**
     * Sends [file] as evidence for a late reason (sprout_web `uploadComplianceEvidence`) and
     * returns its attachment id. Fails with `UploadRefused` carrying the app's reason.
     */
    suspend fun sendEvidence(taskId: Int, file: PreparedFile): Result<Int>

    /** Drops a prepared file the person removed or never sent. */
    suspend fun discard(file: PreparedFile)
}

/**
 * Submitting, approving and rejecting a task, and every step the web runs on the way
 * (sprout_web `TaskPage.HandleApprove`). The server re-checks each call against the viewer's
 * own workflow role, so a stale screen fails safely.
 */
@Suppress("TooManyFunctions") // One function per step of the web's review chain.
interface TaskReviewRepository {
    suspend fun plan(taskId: Int, mode: ApprovalMode): Result<ApprovalPlan>

    /** A refusal the person can act on is an [ApprovalOutcome]; anything else fails. */
    suspend fun approve(taskId: Int, send: ApprovalSend): Result<ApprovalOutcome>

    suspend fun drafts(taskId: Int): Result<List<FormDraft>>

    suspend fun employeeClosure(taskId: Int): Result<EmployeeClosure>

    /** [merge] lists the PDFs to combine, in order; null closes and finalizes each file. */
    suspend fun chooseClosure(taskId: Int, merge: List<String>?, nextExpiration: LocalDate?): Result<Unit>

    /** Empty when there is no earlier step. */
    suspend fun sendBackOptions(taskId: Int): Result<List<SendBackOption>>

    /** [lateReasonId] when it returns a late reason for correction. */
    suspend fun sendBack(
        taskId: Int,
        option: SendBackOption,
        feedback: String,
        minutes: Int,
        lateReasonId: Long? = null,
    ): Result<Unit>

    suspend fun lateContext(taskId: Int): Result<LateContext>

    /** Reasons that may explain a late task. */
    suspend fun lateReasons(): Result<List<ChangeReason>>

    suspend fun justify(
        taskId: Int,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
        delayId: Long?,
    ): Result<Unit>

    /** Agrees with a late reason, optionally correcting it first. */
    suspend fun agreeLate(taskId: Int, justificationId: Long, note: String?, edit: LateEdit?): Result<Unit>
}

/**
 * Change requests on tasks: asking for more time, reporting a delay or asking to close, and
 * reviewing what others asked. [waiting] is shared by every screen that shows it, so a decision
 * leaves all of them at once and comes back to all of them if the server refuses it.
 */
@Suppress("TooManyFunctions")
interface RequestRepository {
    /** What to offer, re-checked when the person signs in, returns to the app, or refreshes. */
    val access: StateFlow<RequestAccess>

    /** Requests waiting on this person, newest first; null until first loaded. */
    val waiting: StateFlow<List<ChangeRequest>?>

    suspend fun refreshAccess(): Result<Unit>

    /** Loads access only when it has not been loaded for this session yet. */
    suspend fun ensureAccess(): Result<Unit> = if (access.value.loaded) Result.success(Unit) else refreshAccess()

    suspend fun refreshWaiting(): Result<Unit>

    /** The person's own requests, newest first. */
    suspend fun mine(): Result<List<ChangeRequest>>

    /**
     * Removes the request from [waiting] at once and puts it back if the server says no. Each kind
     * goes to the route the web uses for it; a training credit also needs [training].
     */
    suspend fun decide(request: ChangeRequest, approve: Boolean, training: TrainingDecision? = null): Result<Unit>

    /** The requester's balance for one leave type; null when the reviewer may not see balances. */
    suspend fun leaveBalance(employeeId: String, leaveTypeId: Int): Result<LeaveBalance?>

    /** The agency's leave zone, which partial days are told in; null when it can't be read. */
    suspend fun leaveZone(): ZoneId?

    /** The agency's active leave types with the signed-in person's balance for each. */
    suspend fun leaveTypes(): Result<List<LeaveType>>

    /** The hours [draft] asks for, the balance after it, and anything that blocks it. */
    suspend fun previewLeave(draft: LeaveDraft): Result<LeavePreview>

    /** Sends [draft] for approval; a refusal fails with [LeaveSendException] when the app can word it. */
    suspend fun requestTimeOff(draft: LeaveDraft): Result<Unit>

    suspend fun trainingCredit(requestId: Int): Result<TrainingCredit>

    suspend fun options(taskId: Int): Result<ChangeOptions>

    suspend fun moreTimeReasons(): Result<List<ChangeReason>>

    suspend fun delayReasons(): Result<List<ChangeReason>>

    suspend fun closureReasons(): Result<List<ChangeReason>>

    suspend fun files(taskId: Int): Result<List<TaskFile>>

    suspend fun askMoreTime(
        taskId: Int,
        newDue: LocalDate,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome>

    suspend fun reportDelay(
        taskId: Int,
        period: DelayPeriod,
        reasonId: Long,
        explanation: String,
        evidenceId: Int?,
    ): Result<RequestOutcome>

    suspend fun askClosure(taskId: Int, reasonId: Long, comment: String): Result<Unit>
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

// Reads, invitation answers and the quick-event writes belong to one calendar cache, so they stay together.
@Suppress("TooManyFunctions")
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

    /** Coworkers who can be invited; [eventId] asks for an edit's options (it may include people already invited). */
    suspend fun attendeeOptions(eventId: String? = null): Result<List<AttendeeOption>>

    /** Creates a one-off event owned by the person; open schedules refresh after. */
    suspend fun createEvent(draft: EventDraft): Result<Unit>

    /** Saves the person's own one-off event; the event and open schedules refresh after. */
    suspend fun updateEvent(event: ScheduleEvent, draft: EventDraft): Result<Unit>

    /** Cancels the person's own one-off event; it leaves every cached schedule at once. */
    suspend fun cancelEvent(eventId: String): Result<Unit>

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

    suspend fun setReduceMotion(enabled: Boolean)

    suspend fun setTaskDelays(filter: DelayFilter)

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

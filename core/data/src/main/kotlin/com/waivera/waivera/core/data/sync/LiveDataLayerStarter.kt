package com.waivera.waivera.core.data.sync

import com.waivera.waivera.core.data.live.LivePushRegistrationRepository
import com.waivera.waivera.core.data.live.SyncTracker
import com.waivera.waivera.core.data.live.TypingTracker
import com.waivera.waivera.core.data.model.SessionState
import com.waivera.waivera.core.data.outbox.OutboxProcessor
import com.waivera.waivera.core.data.outbox.OutboxScheduler
import com.waivera.waivera.core.data.realtime.AppForegroundMonitor
import com.waivera.waivera.core.data.realtime.ConnectivityMonitor
import com.waivera.waivera.core.data.repository.DataLayerStarter
import com.waivera.waivera.core.data.repository.SessionRepository
import com.waivera.waivera.core.data.repository.SettingsRepository
import com.waivera.waivera.core.network.di.ApplicationScope
import com.waivera.waivera.core.realtime.RealtimeClient
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Wires the long-lived pieces together: the hub runs only in the foreground, hub events update
 * the cache, every reconnect resyncs, connectivity coming back retries the hub and the outbox,
 * and notification settings re-register push (debounced, G-08).
 */
@Singleton
@OptIn(FlowPreview::class)
@Suppress("LongParameterList")
class LiveDataLayerStarter @Inject constructor(
    private val realtime: RealtimeClient,
    private val sync: RealtimeSync,
    private val session: SessionRepository,
    private val settings: SettingsRepository,
    private val push: LivePushRegistrationRepository,
    private val foreground: AppForegroundMonitor,
    private val connectivity: ConnectivityMonitor,
    private val outbox: OutboxProcessor,
    private val outboxScheduler: OutboxScheduler,
    private val typing: TypingTracker,
    private val syncTracker: SyncTracker,
    @ApplicationScope private val scope: CoroutineScope,
) : DataLayerStarter {
    private val started = AtomicBoolean(false)

    override fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch { realtime.events.collect { sync.apply(it) } }
        scope.launch { realtime.resyncRequests.collect { sync.resync() } }
        scope.launch { foreground.isForeground.collect(realtime::setForeground) }
        scope.launch {
            connectivity.isOnline.drop(1).filter { it }.collect {
                realtime.retryNow()
                if (runCatching { outbox.process() }.getOrDefault(true)) outboxScheduler.schedule()
                if ((session.state.value as? SessionState.SignedIn)?.isOffline == true) session.refresh()
            }
        }
        scope.launch {
            session.state.map { it is SessionState.SignedIn }.distinctUntilChanged().collect { signedIn ->
                if (signedIn) {
                    // Messages queued before a restart are sent as soon as the session is back.
                    if (runCatching { outbox.process() }.getOrDefault(true)) outboxScheduler.schedule()
                } else {
                    typing.clear()
                    syncTracker.reset()
                    // Covers Firebase ending the session on its own (revoked or deleted account).
                    if (session.state.value is SessionState.SignedOut) realtime.stop()
                }
            }
        }
        scope.launch {
            combine(
                session.state.map {
                    it is SessionState.SignedIn
                },
                settings.settings.map { it.notifications },
            ) { signedIn, prefs ->
                prefs.takeIf { signedIn }
            }.distinctUntilChanged().debounce(PUSH_DEBOUNCE_MILLIS).collectLatest { prefs ->
                val token = push.storedToken()
                if (prefs != null && token != null) push.register(token, prefs)
            }
        }
    }

    private companion object {
        const val PUSH_DEBOUNCE_MILLIS = 500L
    }
}

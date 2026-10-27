package com.waivera.waivera.core.demo

import kotlinx.coroutines.CoroutineScope

/**
 * Every demo repository built by hand, for screenshot and Compose tests that do not use Hilt.
 * [backend] is shared, so actions on one screen show up on the others.
 */
class DemoGraph(scope: CoroutineScope, val backend: DemoBackend = DemoBackend()) {
    val realtime = DemoRealtimeClient()
    val hub = DemoHub(backend, realtime, scope)
    val session = DemoSessionRepository(backend)
    val conversations = DemoConversationRepository(backend)
    val messages = DemoMessageRepository(backend)
    val threads = DemoThreadRepository(backend)
    val attachments = DemoAttachmentRepository()
    val bookmarks = DemoBookmarkRepository(backend)
    val activity = DemoActivityRepository(backend)
    val people = DemoPeopleRepository(backend)
    val calendar = DemoCalendarRepository(backend)
    val profile = DemoProfileRepository(backend)
    val settings = DemoSettingsRepository(backend)
    val push = DemoPushRegistrationRepository()
    val realtimeStatus = DemoRealtimeStatusRepository(backend, realtime, scope)
}

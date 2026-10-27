package com.waivera.waivera.feature.chat.newchat

import com.waivera.waivera.core.data.model.Person
import com.waivera.waivera.core.data.model.SyncStatus
import com.waivera.waivera.core.data.model.Synced
import com.waivera.waivera.core.network.error.WaiveraError
import com.waivera.waivera.feature.chat.R
import com.waivera.waivera.feature.chat.list.AvatarCache
import com.waivera.waivera.feature.chat.list.FakeConversationRepository
import com.waivera.waivera.feature.chat.list.FakePeopleRepository
import com.waivera.waivera.feature.chat.list.FakeSessionRepository
import com.waivera.waivera.feature.chat.list.JORDAN
import com.waivera.waivera.feature.chat.list.ME
import com.waivera.waivera.feature.chat.list.NOW
import com.waivera.waivera.feature.chat.list.PRIYA
import com.waivera.waivera.feature.chat.list.jordan
import com.waivera.waivera.feature.chat.list.me
import com.waivera.waivera.feature.chat.list.priya
import java.time.Clock
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewChatStateHolderTest {
    private val clock = Clock.fixed(NOW, ZoneOffset.UTC)
    private val mei = Person("usr_mei_chen", "Mei", "Chen", email = "mei.chen@example.com")
    private val people = FakePeopleRepository(Synced(listOf(me, priya, jordan, mei, Person(" ")), lastSyncedAt = NOW))
    private val conversations = FakeConversationRepository(Synced(emptyList(), lastSyncedAt = NOW))
    private val session = FakeSessionRepository()

    private fun TestScope.holder() = NewChatStateHolder(backgroundScope, people, conversations, session, clock)

    private fun run(block: suspend TestScope.() -> Unit) = runTest(UnconfinedTestDispatcher(), testBody = block)

    @Test
    fun directoryDropsMeAndPeopleWithoutAnId() = run {
        val ids = holder().state.value.people.map { it.userId }
        assertEquals(listOf(PRIYA, JORDAN, "usr_mei_chen"), ids)
    }

    @Test
    fun searchMatchesDisplayNamesOnlyNotEmail() = run {
        val h = holder()
        h.onQueryChange("  CHEN ")
        assertEquals(listOf("usr_mei_chen"), h.state.value.people.map { it.userId })
        h.onQueryChange("example.com")
        assertTrue(h.state.value.people.isEmpty())
    }

    @Test
    fun buttonOpensAChatForOneAndCreatesAGroupForSeveral() = run {
        val h = holder()
        assertFalse(h.state.value.canSubmit)
        h.onToggle(PRIYA)
        assertEquals(NewChatAction.OpenChat, h.state.value.action)
        assertTrue(h.state.value.canSubmit)
        h.onToggle(JORDAN)
        assertEquals(NewChatAction.CreateGroup, h.state.value.action)
        assertEquals(2, h.state.value.selectedCount)
        h.onToggle(JORDAN)
        assertEquals(1, h.state.value.selectedCount)
    }

    @Test
    fun selectionSurvivesASearchThatHidesThePerson() = run {
        val h = holder()
        h.onToggle(PRIYA)
        h.onQueryChange("jordan")
        assertEquals(1, h.state.value.selectedCount)
        assertEquals(listOf(JORDAN), h.state.value.people.map { it.userId })
    }

    @Test
    fun submitOpensTheReturnedConversationAndResets() = run {
        conversations.openResult = Result.success(7L)
        val h = holder()
        val effects = mutableListOf<NewChatEffect>()
        backgroundScope.launch { h.effects.toList(effects) }
        h.onToggle(PRIYA)
        h.onSubmit()
        assertEquals(listOf(listOf(PRIYA)), conversations.openCalls)
        assertEquals(listOf<NewChatEffect>(NewChatEffect.OpenConversation(7)), effects)
        assertEquals(0, h.state.value.selectedCount)
        assertFalse(h.state.value.isCreating)
    }

    @Test
    fun failureShowsTheTypedErrorAndKeepsTheSelection() = run {
        conversations.openResult = Result.failure(WaiveraError.Server(WaiveraError.HTTP_SERVER_ERROR))
        val h = holder()
        h.onToggle(PRIYA)
        h.onSubmit()
        assertEquals(WaiveraError.Server(WaiveraError.HTTP_SERVER_ERROR).messageRes, h.state.value.errorRes)
        assertEquals(1, h.state.value.selectedCount)
        h.onToggle(JORDAN)
        assertEquals(null, h.state.value.errorRes, "changing the selection clears the error")
    }

    @Test
    fun conflictOnCreateShowsCreateCopyNotTheEditConflictCopy() = run {
        conversations.openResult = Result.failure(WaiveraError.Conflict())
        val h = holder()
        h.onToggle(PRIYA)
        h.onSubmit()
        assertEquals(R.string.feature_chat_newchat_create_conflict, h.state.value.errorRes)
        assertEquals(1, h.state.value.selectedCount)
    }

    @Test
    fun loadingThenEmptyThenErrorStates() = run {
        people.directory.value = Synced(emptyList(), SyncStatus.Refreshing, lastSyncedAt = null)
        val h = holder()
        assertTrue(h.state.value.isLoading)
        people.directory.value = Synced(emptyList(), lastSyncedAt = NOW)
        assertFalse(h.state.value.isLoading)
        assertTrue(h.state.value.people.isEmpty())
        people.directory.value = Synced(emptyList(), SyncStatus.Failed(WaiveraError.Network()), lastSyncedAt = null)
        assertEquals(WaiveraError.Network().messageRes, h.state.value.loadErrorRes)
        h.onRetryLoad()
        assertEquals(1, people.refreshCalls)
    }

    @Test
    fun openingRefetchesOnlyAStaleDirectory() = run {
        val h = holder()
        h.onOpened()
        assertEquals(0, people.refreshCalls)
        people.directory.value = Synced(listOf(priya), lastSyncedAt = NOW.minusSeconds(120))
        h.onOpened()
        assertEquals(1, people.refreshCalls)
    }

    @Test
    fun dismissClearsSearchAndSelection() = run {
        val h = holder()
        h.onQueryChange("pri")
        h.onToggle(PRIYA)
        h.onDismissed()
        assertEquals("", h.state.value.query)
        assertEquals(0, h.state.value.selectedCount)
    }

    @Test
    fun avatarsAreRequestedOncePerIdAndNeverForBotsOrBlankIds() = run {
        val cache = AvatarCache(
            people,
            decode = { it.size },
            scope = backgroundScope,
            decodeDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        cache.prefetch(listOf(PRIYA, PRIYA, JORDAN, "bot:3", "", "null", ME))
        cache.prefetch(listOf(PRIYA, JORDAN))
        assertEquals(listOf(PRIYA, JORDAN, ME), people.avatarRequests)
        assertEquals(mapOf(PRIYA to 3), cache.images.value)
    }
}

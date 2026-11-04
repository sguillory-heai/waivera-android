package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.LinkedFaq
import com.waivera.waivera.core.data.model.LinkedResource
import com.waivera.waivera.core.data.model.TaskDetail
import com.waivera.waivera.core.data.model.TaskState
import com.waivera.waivera.core.testing.MainDispatcherRule
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Rule
import org.junit.Test

/** Which links get previews, how the text reads once they resolve, and what opening one does. */
class ChatLinksTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val web = FakeLinkLookups.WEB
    private val links = ChatLinks(web.toHttpUrl())

    @Test
    fun only_links_on_the_web_app_are_read() {
        assertEquals(LinkTarget.Task(1042, "HEA"), links.target("$web/task/HEA-1042"))
        assertEquals(LinkTarget.Task(7, null), links.target("/task/7/"))
        assertEquals(LinkTarget.Resource(ID), links.target("/resources/${ID.uppercase()}"))
        assertEquals(LinkTarget.Faq(ID, null), links.target("$web/faqs/article/$ID"))
        assertEquals(LinkTarget.Faq(null, "submitting-mileage"), links.target("/faqs/submitting-mileage"))

        assertNull(links.target("https://evil.example/task/1042"))
        assertNull(links.target("//evil.example/task/1042"))
        assertNull(links.target("https://user:pw@app.waivera.test/task/1042"))
        assertNull(links.target("http://app.waivera.test/task/1042"), "another scheme is another site")
        assertNull(links.target("/task/0"))
        assertNull(links.target("/task/99999999999"))
        assertNull(links.target("/task/1042‮"))
        assertNull(links.target("/resources/$ID?share=1"), "resource links never carry a query")
        assertNull(links.target("/resources/00000000-0000-0000-0000-000000000000"))
        assertNull(links.target("/faqs/article/not-a-uuid"))
        assertNull(links.target("/schedule"))
    }

    @Test
    fun without_a_web_base_no_link_is_read() {
        assertNull(ChatLinks(null).target("/task/7"))
    }

    @Test
    fun every_address_takes_its_short_name_and_a_closing_link_stays_in_the_text() {
        val doc = paragraph(
            text("Start with "),
            text("$web/task/HEA-1077", "$web/task/HEA-1077"),
            text(" and call "),
            text("$web/resources/$ID", "$web/resources/$ID"),
            text(" "),
            RichInline.HardBreak,
            text("Lakeview", "/resources/$OTHER"),
        )
        val previews = mapOf(
            LinkTarget.Task(1077, "HEA") to task("HEA-1077"),
            LinkTarget.Resource(ID) to LinkPreview.Resource("Accessible Rides", null, null),
            LinkTarget.Resource(OTHER) to LinkPreview.Resource("Lakeview", null, null),
        )

        val shown = links.present(doc, previews)

        assertEquals(
            paragraph(
                text("Start with "),
                text("HEA-1077", "$web/task/HEA-1077"),
                text(" and call "),
                text("Accessible Rides", "$web/resources/$ID"),
                text(" "),
                RichInline.HardBreak,
                text("Lakeview", "/resources/$OTHER"),
            ),
            shown,
        )
    }

    @Test
    fun a_message_that_is_only_a_link_reads_as_its_short_name() {
        val doc = paragraph(text("$web/task/HEA-1077", "$web/task/HEA-1077"))
        val previews = mapOf<LinkTarget, LinkPreview>(LinkTarget.Task(1077, "HEA") to task("HEA-1077"))

        assertEquals(paragraph(text("HEA-1077", "$web/task/HEA-1077")), links.present(doc, previews))
    }

    @Test
    fun links_still_loading_or_unavailable_stay_as_written() {
        val doc = paragraph(text("See "), text("$web/task/5", "$web/task/5"))
        val loading = mapOf<LinkTarget, LinkPreview>(LinkTarget.Task(5, null) to LinkPreview.Loading(LinkKind.Task))
        val unavailable = mapOf<LinkTarget, LinkPreview>(
            LinkTarget.Task(5, null) to LinkPreview.Unavailable(LinkKind.Task),
        )

        assertEquals(doc, links.present(doc, loading))
        assertEquals(doc, links.present(doc, unavailable))
    }

    @Test
    fun a_task_from_another_agency_with_the_same_number_is_unavailable() = runTest {
        val lookups = FakeLinkLookups(tasks = mapOf(1042 to detail(1, "HEA-1042"), 7 to detail(2, "7")))
        val resolver = FakeLinkLookups.resolver(lookups)

        resolver.request(
            listOf(LinkTarget.Task(1042, "OTH"), LinkTarget.Task(1042, "hea"), LinkTarget.Task(7, null)),
            backgroundScope,
            ::today,
        )
        runCurrent()

        assertIs<LinkPreview.Unavailable>(resolver.previews.value[LinkTarget.Task(1042, "OTH")])
        assertEquals(1, assertIs<LinkPreview.Task>(resolver.previews.value[LinkTarget.Task(1042, "hea")]).id)
        assertEquals(2, assertIs<LinkPreview.Task>(resolver.previews.value[LinkTarget.Task(7, null)]).id)
    }

    @Test
    fun articles_share_one_read_and_each_target_is_read_once() = runTest {
        val lookups = FakeLinkLookups(
            faqs = Result.success(listOf(LinkedFaq(ID, "mileage", "Submitting mileage", null))),
            resources = mapOf(OTHER to LinkedResource(OTHER, "Lakeview", null, "Baton Rouge")),
        )
        val resolver = FakeLinkLookups.resolver(lookups)
        val targets = listOf(LinkTarget.Faq(ID, null), LinkTarget.Faq(null, "mileage"), LinkTarget.Resource(OTHER))

        resolver.request(targets, backgroundScope, ::today)
        runCurrent()
        resolver.request(targets, backgroundScope, ::today)
        runCurrent()

        assertEquals(listOf("faqs", "resource:$OTHER"), lookups.reads.sorted())
        assertEquals("Submitting mileage", assertIs<LinkPreview.Faq>(resolver.previews.value[targets[1]]).title)
        assertEquals("Baton Rouge", assertIs<LinkPreview.Resource>(resolver.previews.value[targets[2]]).place)
    }

    @Test
    fun a_loaded_task_opens_in_the_app_and_other_links_open_on_the_web() = runTest {
        val resolver = FakeLinkLookups.resolver(FakeLinkLookups(tasks = mapOf(12 to detail(340, "HEA-12"))))
        resolver.request(listOf(LinkTarget.Task(12, null)), backgroundScope, ::today)
        runCurrent()

        assertEquals(ChatEffect.OpenTask(340), resolver.effectFor("/task/12"))
        assertEquals(ChatEffect.OpenUrl("$web/task/13"), resolver.effectFor("/task/13"))
        assertEquals(ChatEffect.OpenUrl("$web/resources/$ID"), resolver.effectFor("/resources/$ID"))
        assertNull(resolver.effectFor("https://elsewhere.example/page"), "outside links keep the usual handling")
    }

    @Test
    fun a_due_date_in_another_year_shows_its_year() = runTest {
        val next = detail(1, "9").copy(due = LocalDate.of(2027, 1, 4))
        val resolver = FakeLinkLookups.resolver(FakeLinkLookups(tasks = mapOf(9 to next)))
        resolver.request(listOf(LinkTarget.Task(9, null)), backgroundScope, ::today)
        runCurrent()

        assertEquals(true, assertIs<LinkPreview.Task>(resolver.previews.value[LinkTarget.Task(9, null)]).dueShowsYear)
    }

    private fun today() = LocalDate.of(2026, 12, 31)

    private fun task(key: String) = LinkPreview.Task(1, key, "Plan review", TaskState.InProgress, null)

    private fun detail(id: Int, key: String) = TaskDetail(
        id = id,
        key = key,
        title = "Plan review",
        description = null,
        state = TaskState.InProgress,
        type = null,
        start = null,
        due = null,
        assignedBy = null,
        about = null,
        webPath = "/task/$key",
    )

    private fun text(value: String, href: String? = null) = RichInline.Text(value, href = href)

    private fun paragraph(vararg inlines: RichInline) = RichDocument(listOf(RichBlock.Paragraph(inlines.toList())))

    private companion object {
        const val ID = "6a1d3e52-8f07-4c2b-a9d4-17e0b6c35f81"
        const val OTHER = "0c94f7a2-3b58-4e1d-8a6f-d2b5e9c71034"
    }
}

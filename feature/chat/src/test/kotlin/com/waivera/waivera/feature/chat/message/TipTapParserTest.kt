package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Message
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/** One test per rich-text rule in 03 §7. */
class TipTapParserTest {
    private fun doc(vararg nodes: String): JsonObject =
        Json.parseToJsonElement("""{"type":"doc","content":[${nodes.joinToString(",")}]}""").jsonObject

    private fun text(value: String, marks: String = "") =
        """{"type":"text","text":"$value"${if (marks.isEmpty()) "" else ""","marks":[$marks]"""}}"""

    private fun paragraph(vararg inlines: String) = """{"type":"paragraph","content":[${inlines.joinToString(",")}]}"""

    private fun blocks(json: JsonObject) = TipTapParser.parse(json).blocks

    @Test
    fun paragraphWithPlainText() {
        val result = blocks(doc(paragraph(text("Hello"))))
        assertEquals(listOf(RichBlock.Paragraph(listOf(RichInline.Text("Hello")))), result)
    }

    @Test
    fun everyMarkIsRead() {
        val marks = """{"type":"bold"},{"type":"italic"},{"type":"strike"},{"type":"underline"},{"type":"code"}"""
        val inline = (blocks(doc(paragraph(text("x", marks)))).single() as RichBlock.Paragraph).inlines.single()
        assertEquals(RichMark.entries.toSet(), (inline as RichInline.Text).marks)
    }

    @Test
    fun unknownMarksAreIgnored() {
        val inline = (
            blocks(
                doc(paragraph(text("x", """{"type":"highlight"}"""))),
            ).single() as RichBlock.Paragraph
            ).inlines.single()
        assertEquals(emptySet(), (inline as RichInline.Text).marks)
    }

    @Test
    fun safeLinkKeepsItsHref() {
        val link = """{"type":"link","attrs":{"href":"https://waivera.example/plan"}}"""
        val inline = (blocks(doc(paragraph(text("plan", link)))).single() as RichBlock.Paragraph).inlines.single()
        assertEquals("https://waivera.example/plan", (inline as RichInline.Text).href)
    }

    @Test
    fun unsafeLinkRendersAsPlainText() {
        val link = """{"type":"link","attrs":{"href":"javascript:alert(1)"}}"""
        val inline = (blocks(doc(paragraph(text("click", link)))).single() as RichBlock.Paragraph).inlines.single()
        assertNull((inline as RichInline.Text).href)
    }

    @Test
    fun mentionUsesLabelThenIdThenFallback() {
        val labelled = """{"type":"mention","attrs":{"id":"usr_1","label":"Priya Raman"}}"""
        val idOnly = """{"type":"mention","attrs":{"id":"usr_2"}}"""
        val bare = """{"type":"mention"}"""
        val inlines = (blocks(doc(paragraph(labelled, idOnly, bare))).single() as RichBlock.Paragraph).inlines
        assertEquals(
            listOf(RichInline.Mention("Priya Raman"), RichInline.Mention("usr_2"), RichInline.Mention("mention")),
            inlines,
        )
    }

    @Test
    fun hardBreakIsKept() {
        val inlines = (
            blocks(
                doc(paragraph(text("a"), """{"type":"hardBreak"}""", text("b"))),
            ).single() as RichBlock.Paragraph
            ).inlines
        assertEquals(listOf(RichInline.Text("a"), RichInline.HardBreak, RichInline.Text("b")), inlines)
    }

    @Test
    fun headingLevelDefaultsToTwo() {
        val withLevel = blocks(doc("""{"type":"heading","attrs":{"level":1},"content":[${text("A")}]}""")).single()
        val withoutLevel = blocks(doc("""{"type":"heading","content":[${text("B")}]}""")).single()
        assertEquals(1, (withLevel as RichBlock.Heading).level)
        assertEquals(2, (withoutLevel as RichBlock.Heading).level)
    }

    @Test
    fun blockquoteHoldsBlocks() {
        val quote = blocks(doc("""{"type":"blockquote","content":[${paragraph(text("q"))}]}""")).single()
        assertIs<RichBlock.Blockquote>(quote)
        assertEquals(1, quote.blocks.size)
    }

    @Test
    fun bulletAndOrderedLists() {
        val item = """{"type":"listItem","content":[${paragraph(text("one"))}]}"""
        val bullet = blocks(doc("""{"type":"bulletList","content":[$item,$item]}""")).single() as RichBlock.ListBlock
        val ordered = blocks(doc("""{"type":"orderedList","content":[$item]}""")).single() as RichBlock.ListBlock
        assertEquals(false, bullet.ordered)
        assertEquals(2, bullet.items.size)
        assertEquals(true, ordered.ordered)
    }

    @Test
    fun codeBlockJoinsItsText() {
        val code = blocks(doc("""{"type":"codeBlock","content":[${text("val a = 1")}]}""")).single()
        assertEquals(RichBlock.CodeBlock("val a = 1"), code)
    }

    @Test
    fun horizontalRule() {
        assertEquals(listOf(RichBlock.HorizontalRule), blocks(doc("""{"type":"horizontalRule"}""")))
    }

    @Test
    fun tableRowsAndCells() {
        val cell = """{"type":"tableCell","content":[${paragraph(text("c"))}]}"""
        val row = """{"type":"tableRow","content":[$cell,$cell]}"""
        val table = blocks(doc("""{"type":"table","content":[$row,$row,$row]}""")).single() as RichBlock.Table
        assertEquals(3, table.rows.size)
        assertEquals(2, table.rows.first().size)
    }

    @Test
    fun unknownNodeWithContentRendersItsChildren() {
        val group = blocks(doc("""{"type":"callout","content":[${paragraph(text("inside"))}]}""")).single()
        assertIs<RichBlock.Group>(group)
    }

    @Test
    fun unknownLeafNodeIsDropped() {
        assertEquals(emptyList(), blocks(doc("""{"type":"image","attrs":{"src":"x"}}""")))
    }

    @Test
    fun looseInlineNodesAtTheTopBecomeAParagraph() {
        val result = blocks(doc(text("loose")))
        assertEquals(listOf(RichBlock.Paragraph(listOf(RichInline.Text("loose")))), result)
    }

    @Test
    fun emptyTextNodesAreSkipped() {
        val inlines = (blocks(doc(paragraph(text("")))).single() as RichBlock.Paragraph).inlines
        assertEquals(emptyList(), inlines)
    }

    @Test
    fun extractFindsADocWrappedInAnEnvelope() {
        val wrapped = Json.parseToJsonElement("""{"messageDoc":{"doc":${doc(paragraph(text("hi")))}}}""")
        assertEquals("doc", TipTapParser.extractDoc(wrapped, null)?.get("type")?.let { (it as JsonPrimitive).content })
    }

    @Test
    fun extractFindsAStringifiedDoc() {
        val once = JsonPrimitive(doc(paragraph(text("hi"))).toString())
        val twice = JsonPrimitive(once.toString())
        assertEquals("hi", TipTapParser.plainText(requireNotNull(TipTapParser.extractDoc(once, null))))
        assertEquals("hi", TipTapParser.plainText(requireNotNull(TipTapParser.extractDoc(twice, null))))
    }

    @Test
    fun textIsReadAsADocOnlyWhenItMentionsAnEditor() {
        val editorPayload = """{"editor":"tiptap","doc":${doc(paragraph(text("from text")))}}"""
        val plainJson = doc(paragraph(text("not rich"))).toString()
        assertEquals("from text", TipTapParser.plainText(requireNotNull(TipTapParser.extractDoc(null, editorPayload))))
        assertNull(TipTapParser.extractDoc(null, plainJson))
    }

    @Test
    fun deepNestingStopsAtTheDepthLimit() {
        var payload: kotlinx.serialization.json.JsonElement = doc(paragraph(text("deep")))
        repeat(12) { payload = buildJsonObject { put("data", payload) } }
        assertNull(TipTapParser.extractDoc(payload, null))
    }

    @Test
    fun plainTextProjection() {
        val json = doc(
            paragraph(text("Hi "), """{"type":"mention","attrs":{"label":"Priya"}}"""),
            paragraph(),
            paragraph(),
            paragraph(text("line"), """{"type":"hardBreak"}""", text("two")),
        )
        assertEquals("Hi @Priya\n\nline\ntwo", TipTapParser.plainText(json))
    }

    @Test
    fun messagePlainTextPrefersTheDocAndIsEmptyWhenDeleted() {
        val message = Message(id = 1, conversationId = 1, text = "stored", doc = doc(paragraph(text("rich"))))
        assertEquals("rich", message.plainText())
        assertEquals("stored", message.copy(doc = null).plainText())
        assertEquals("", message.copy(isDeleted = true).plainText())
    }

    @Test
    fun documentForAMessageWithoutDocIsNull() {
        assertNull(TipTapParser.documentFor(Message(id = 1, conversationId = 1, text = "just text")))
    }
}

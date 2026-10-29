package com.waivera.waivera.feature.chat.message

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** How inline marks, links and mentions become styled text (03 §7). */
@RunWith(RobolectricTestRunner::class)
class RichInlineAnnotationTest {
    private val colors = RichInlineColors(accent = Color.Magenta, mentionBackground = Color.Yellow)

    @Test
    fun boldItalicCodeAndDecorationsCompose() {
        val text = annotateInlines(
            listOf(
                RichInline.Text(
                    "x",
                    setOf(RichMark.Bold, RichMark.Italic, RichMark.Code, RichMark.Strike, RichMark.Underline),
                ),
            ),
            colors,
        ) {}
        val style = text.spanStyles.single().item
        assertEquals(FontWeight.ExtraBold, style.fontWeight)
        assertEquals(FontStyle.Italic, style.fontStyle)
        assertEquals(FontFamily.Monospace, style.fontFamily)
        val decoration = requireNotNull(style.textDecoration)
        assertTrue(decoration.contains(TextDecoration.LineThrough) && decoration.contains(TextDecoration.Underline))
    }

    @Test
    fun linkKeepsItsMarksAndTakesTheAccent() {
        var opened: String? = null
        val text = annotateInlines(
            listOf(RichInline.Text("plan", setOf(RichMark.Bold), href = "https://waivera.example/plan")),
            colors,
        ) { opened = it }
        val link = text.getLinkAnnotations(0, text.length).single().item as LinkAnnotation.Clickable
        val style = requireNotNull(link.styles?.style)
        assertEquals(FontWeight.ExtraBold, style.fontWeight)
        assertEquals(Color.Magenta, style.color)
        link.linkInteractionListener?.onClick(link)
        assertEquals("https://waivera.example/plan", opened)
    }

    @Test
    fun textWithoutHrefHasNoLink() {
        val text = annotateInlines(listOf(RichInline.Text("plain")), colors) {}
        assertEquals(0, text.getLinkAnnotations(0, text.length).size)
    }

    @Test
    fun mentionIsAtPrefixedBoldAndAccented() {
        val text = annotateInlines(listOf(RichInline.Mention("Priya Raman")), colors) {}
        assertEquals("@Priya Raman", text.text)
        val style = text.spanStyles.single().item
        assertEquals(FontWeight.ExtraBold, style.fontWeight)
        assertEquals(Color.Magenta, style.color)
        assertEquals(Color.Yellow, style.background)
        assertEquals(null, style.textDecoration)
    }

    @Test
    fun mentionKeepsItsHyphenatedNameOnOneLine() {
        val text = annotateInlines(listOf(RichInline.Mention("Jean-Baptiste Moreau")), colors) {}
        assertEquals("@Jean\u2011Baptiste Moreau", text.text, "a mention wraps at its own hyphen")
    }

    @Test
    fun mentionInMyTintedBubbleIsUnderlinedWithoutABlock() {
        val inBubble = RichInlineColors(accent = Color.Magenta, mentionBackground = null)
        val style = annotateInlines(listOf(RichInline.Mention("Priya Raman")), inBubble) {}.spanStyles.single().item
        assertEquals(FontWeight.ExtraBold, style.fontWeight)
        assertEquals(Color.Magenta, style.color)
        assertEquals(Color.Unspecified, style.background, "a square block on the tint reads as a selection")
        assertEquals(TextDecoration.Underline, style.textDecoration)
    }

    @Test
    fun hardBreakIsANewline() {
        val text = annotateInlines(listOf(RichInline.Text("a"), RichInline.HardBreak, RichInline.Text("b")), colors) {}
        assertEquals("a\nb", text.text)
    }
}

package com.waivera.waivera.feature.chat.message

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.waivera.waivera.core.designsystem.preview.WaiveraPreviews
import com.waivera.waivera.core.designsystem.text.withNonBreakingHyphens
import com.waivera.waivera.core.designsystem.theme.WaiveraShapes
import com.waivera.waivera.core.designsystem.theme.WaiveraSpacing
import com.waivera.waivera.core.designsystem.theme.WaiveraTheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Colours the inline builder needs; resolved from the theme by [RichText]. A null
 * [mentionBackground] sets mentions underlined instead of on a wash: in my tinted bubble a wash
 * block barely shows on the tint and a square block reads like a text selection (UI10-14), so the
 * mention is the bold violet with an underline, a cue that does not rest on colour alone.
 */
@Immutable
data class RichInlineColors(val accent: Color, val mentionBackground: Color?)

/**
 * Inline content to an [AnnotatedString]. Marks compose in order; a link is applied last, so a
 * link inside bold text stays bold but takes the accent colour (03 §7). Only links that passed
 * [SafeLinks] carry a link annotation, and taps go through [onLink], never straight to the system.
 */
fun annotateInlines(inlines: List<RichInline>, colors: RichInlineColors, onLink: (String) -> Unit): AnnotatedString =
    buildAnnotatedString {
        inlines.forEach { inline ->
            when (inline) {
                is RichInline.Text -> {
                    val style = spanFor(inline.marks)
                    val href = inline.href
                    if (href != null) {
                        val linkStyle = style.merge(
                            SpanStyle(
                                color = colors.accent,
                                textDecoration = combine(style.textDecoration, TextDecoration.Underline),
                            ),
                        )
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = href,
                                styles = TextLinkStyles(style = linkStyle),
                                linkInteractionListener = { onLink(href) },
                            ),
                        ) { append(inline.text) }
                    } else {
                        withStyle(style) { append(inline.text) }
                    }
                }
                // A mention is one name: it never wraps at its own hyphens ("@Maria" / "-Fernanda").
                is RichInline.Mention -> withStyle(mentionSpan(colors)) {
                    append("@${inline.label.withNonBreakingHyphens()}")
                }
                RichInline.HardBreak -> append('\n')
            }
        }
    }

private fun mentionSpan(colors: RichInlineColors): SpanStyle {
    val background = colors.mentionBackground
    return SpanStyle(
        fontWeight = FontWeight.ExtraBold,
        color = colors.accent,
        background = background ?: Color.Unspecified,
        textDecoration = if (background == null) TextDecoration.Underline else null,
    )
}

private fun spanFor(marks: Set<RichMark>): SpanStyle {
    var decoration: TextDecoration? = null
    if (RichMark.Strike in marks) decoration = combine(decoration, TextDecoration.LineThrough)
    if (RichMark.Underline in marks) decoration = combine(decoration, TextDecoration.Underline)
    return SpanStyle(
        fontWeight = if (RichMark.Bold in marks) FontWeight.ExtraBold else null,
        fontStyle = if (RichMark.Italic in marks) FontStyle.Italic else null,
        fontFamily = if (RichMark.Code in marks) FontFamily.Monospace else null,
        textDecoration = decoration,
    )
}

private fun combine(a: TextDecoration?, b: TextDecoration): TextDecoration =
    if (a == null) b else TextDecoration.combine(listOf(a, b))

/** Renders a TipTap document. Block layout follows 03 §7; text sizes come from the type scale. */
@Composable
fun RichText(
    document: RichDocument,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = WaiveraTheme.colors.text,
    inMyBubble: Boolean = false,
) {
    CompositionLocalProvider(LocalInMyBubble provides inMyBubble) {
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
            document.blocks.forEach { RichBlockView(it, onLink, color) }
        }
    }
}

/** True inside my own tinted bubble, where mentions are underlined rather than washed. */
private val LocalInMyBubble = staticCompositionLocalOf { false }

@Composable
private fun RichBlockView(block: RichBlock, onLink: (String) -> Unit, color: Color) {
    val colors = WaiveraTheme.colors
    val inlineColors = RichInlineColors(
        accent = colors.accent,
        mentionBackground = if (LocalInMyBubble.current) null else colors.accentWash,
    )
    when (block) {
        is RichBlock.Paragraph -> InlineText(
            block.inlines,
            MaterialTheme.typography.bodyMedium,
            color,
            inlineColors,
            onLink,
        )
        is RichBlock.Heading -> InlineText(
            block.inlines,
            when {
                block.level <= 2 -> MaterialTheme.typography.titleLarge
                block.level == HEADING_MEDIUM_LEVEL -> MaterialTheme.typography.titleMedium
                else -> MaterialTheme.typography.titleSmall
            }.copy(fontWeight = FontWeight.ExtraBold),
            color,
            inlineColors,
            onLink,
            Modifier.padding(top = WaiveraSpacing.XxSmall),
        )
        is RichBlock.Blockquote -> Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(vertical = WaiveraSpacing.XxSmall),
        ) {
            Box(
                Modifier
                    .width(ChatDimens.QuoteBar)
                    .fillMaxHeight()
                    .background(colors.accent),
            )
            Column(
                modifier = Modifier.padding(start = WaiveraSpacing.MediumSmall - WaiveraSpacing.XxSmall),
                verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall),
            ) {
                block.blocks.forEach { RichBlockView(it, onLink, colors.textSecondary) }
            }
        }
        is RichBlock.ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
            block.items.forEachIndexed { index, item ->
                Row {
                    Text(
                        text = if (block.ordered) "${index + 1}." else "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.End,
                        modifier = Modifier
                            .widthIn(min = ChatDimens.ListMarkerGutter)
                            .padding(end = WaiveraSpacing.ExtraSmall),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall)) {
                        item.forEach { RichBlockView(it, onLink, color) }
                    }
                }
            }
        }
        is RichBlock.CodeBlock -> Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .border(WaiveraSpacing.Hairline, colors.borderStrong, WaiveraShapes.Chip)
                .padding(WaiveraSpacing.Small),
        )
        RichBlock.HorizontalRule -> Box(
            Modifier
                .padding(vertical = WaiveraSpacing.Small)
                .fillMaxWidth()
                .height(WaiveraSpacing.Hairline)
                .background(colors.borderStrong),
        )
        is RichBlock.Table -> RichTable(block, onLink, color)
        is RichBlock.Group -> Column(verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.ExtraSmall)) {
            block.blocks.forEach { RichBlockView(it, onLink, color) }
        }
    }
}

@Composable
private fun RichTable(table: RichBlock.Table, onLink: (String) -> Unit, color: Color) {
    val colors = WaiveraTheme.colors
    Column(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .widthIn(min = ChatDimens.TableMinWidth),
    ) {
        table.rows.forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                row.forEach { cell ->
                    Column(
                        modifier = Modifier
                            .width(ChatDimens.TableCellWidth)
                            .fillMaxHeight()
                            .heightIn(min = ChatDimens.TableCellMinHeight)
                            .border(WaiveraSpacing.Hairline, colors.borderStrong)
                            .padding(WaiveraSpacing.Small),
                        verticalArrangement = Arrangement.spacedBy(WaiveraSpacing.XxSmall, Alignment.CenterVertically),
                    ) {
                        cell.forEach { RichBlockView(it, onLink, color) }
                    }
                }
            }
        }
    }
}

@Composable
private fun InlineText(
    inlines: List<RichInline>,
    style: TextStyle,
    color: Color,
    colors: RichInlineColors,
    onLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = remember(inlines, colors, onLink) { annotateInlines(inlines, colors, onLink) }
    if (text.isEmpty()) return
    Text(text = text, style = style, color = color, modifier = modifier)
}

private val PreviewDoc = """
{"type":"doc","content":[
 {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Visit notes"}]},
 {"type":"paragraph","content":[{"type":"text","text":"Ask "},{"type":"mention","attrs":{"id":"usr_priya_raman","label":"Priya Raman"}},
  {"type":"text","text":" about the "},{"type":"text","marks":[{"type":"bold"},{"type":"link","attrs":{"href":"https://example.com/plan"}}],"text":"care plan"}]},
 {"type":"orderedList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Medication"}]},
   {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Morning dose"}]}]}]}]}]},
 {"type":"blockquote","content":[{"type":"paragraph","content":[{"type":"text","marks":[{"type":"italic"}],"text":"Keep the door locked."}]}]},
 {"type":"codeBlock","content":[{"type":"text","text":"WAIV-2527"}]},
 {"type":"horizontalRule"},
 {"type":"table","content":[{"type":"tableRow","content":[
   {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"Time"}]}]},
   {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"Task"}]}]},
   {"type":"tableCell","content":[{"type":"paragraph","content":[{"type":"text","text":"Owner"}]}]}]}]}
]}
""".trimIndent()

/** A document using every node and mark the renderer supports, for previews and screenshots. */
val SampleRichDocument: RichDocument by lazy {
    TipTapParser.parse(Json.parseToJsonElement(PreviewDoc) as JsonObject)
}

@WaiveraPreviews
@Composable
private fun RichTextPreview() {
    WaiveraTheme {
        RichText(document = SampleRichDocument, onLink = {}, modifier = Modifier.padding(WaiveraSpacing.Medium))
    }
}

private const val HEADING_MEDIUM_LEVEL = 3

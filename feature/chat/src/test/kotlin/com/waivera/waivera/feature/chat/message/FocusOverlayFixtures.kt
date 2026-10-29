package com.waivera.waivera.feature.chat.message

import com.waivera.waivera.core.data.model.Message
import com.waivera.waivera.core.demo.DemoData

/**
 * The wave 28 long-content fixtures the focus overlay is checked against (copied from
 * `android-results/wave28/harness/LongFixtures.kt`, kept here so this module's tests own them):
 * a 3,000-character message in paragraphs, a 60-line message and an unbroken 400-character link.
 */
object FocusOverlayFixtures {
    private val SENTENCES = listOf(
        "Quick update on the Hughes household before the weekend handover.",
        "Mrs. Hughes had a restless night and asked twice about the new medication schedule, so please go over " +
            "it again with her at breakfast.",
        "The pharmacy delivery is now Tuesdays and Fridays between 2 and 4 pm, and someone needs to sign for it " +
            "in person.",
        "Her daughter will visit Saturday morning and would like ten minutes with whoever is on shift to talk " +
            "about the care plan goals.",
        "The bathroom grab rail by the shower is loose again; I logged it with maintenance but please do not " +
            "let her use it until it is fixed.",
        "Mileage for the hospital run on Wednesday still needs to be entered, and the receipt is in the blue " +
            "folder on the kitchen counter.",
    )

    /** About 3,000 characters in paragraphs separated by blank lines. */
    val PARAGRAPHS_3000: String = buildString {
        var i = 0
        while (length < 3_000) {
            if (i > 0) append(if (i % 3 == 0) "\n\n" else " ")
            append(SENTENCES[i % SENTENCES.size])
            i++
        }
    }.take(3_000)

    /** 60 short lines. */
    val LINES_60: String = (1..60).joinToString("\n") { "$it. Check the visit log entry for room $it" }

    private const val ALNUM = "a7Kq2Zr9Xm4Tb8Wn3Lp6Yc1Vd5Hs0Gf"

    /** A 400-character URL with no break opportunities after the host. */
    val URL_400: String = "https://app.example.test/r?k=".let { prefix ->
        prefix + (0 until 400 - prefix.length).map { ALNUM[(it * 7) % ALNUM.length] }.joinToString("")
    }

    const val SHORT: String = "@Priya Raman can you confirm?"

    /** My own plain message with [text], so every action shows: the most crowded case. */
    fun mine(text: String, id: Long = 80_500): Message = Message(
        id = id,
        conversationId = DemoData.DM_PRIYA,
        authorId = DemoData.ME,
        text = text,
        contentFormat = Message.CONTENT_FORMAT_PLAIN,
        createdAt = DemoData.NOW.minusSeconds(60),
    )

    /** Someone else's plain message: no Edit or Delete. */
    fun theirs(text: String, id: Long = 80_501): Message = mine(text, id).copy(authorId = DemoData.PRIYA)
}

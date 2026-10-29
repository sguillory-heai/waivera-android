package com.waivera.waivera.core.testing

/**
 * Long-content stress fixtures from the wave 28 pass: the longest and least breakable content a
 * chat can carry. Layout tests use them to keep long names, labels and mentions from breaking
 * cards at small widths and large font.
 */
object LongFixtures {
    /** Exactly 60 characters. */
    const val NAME_60: String = "Maria-Fernanda Alejandra Castellanos-Whitaker de la Vega Rui"

    const val CHANNEL_LONG: String = "north-region-overnight-care-coordination-and-weekend-scheduling-team"

    private val SENTENCES = listOf(
        "Quick update on the Hughes household before the weekend handover.",
        "Mrs. Hughes had a restless night and asked twice about the new medication schedule, " +
            "so please go over it again with her at breakfast.",
        "The pharmacy delivery is now Tuesdays and Fridays between 2 and 4 pm, " +
            "and someone needs to sign for it in person.",
        "Her daughter will visit Saturday morning and would like ten minutes with whoever is on shift " +
            "to talk about the care plan goals.",
        "The bathroom grab rail by the shower is loose again; I logged it with maintenance " +
            "but please do not let her use it until it is fixed.",
        "Mileage for the hospital run on Wednesday still needs to be entered, " +
            "and the receipt is in the blue folder on the kitchen counter.",
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

    // Coprime to the 32 letters, so a token walks all of them before one repeats.
    private const val STRIDE = 7
    private const val ALNUM = "a7Kq2Zr9Xm4Tb8Wn3Lp6Yc1Vd5Hs0Gf"

    private fun unbroken(count: Int, offset: Int = 0): String =
        (0 until count).map { ALNUM[(it * STRIDE + offset) % ALNUM.length] }.joinToString("")

    /** A 400-character URL with no break opportunities after the host. */
    val URL_400: String = "https://app.example.test/r?k=".let { it + unbroken(400 - it.length) }

    /** A 200-character token with no break opportunities. */
    val TOKEN_200: String = unbroken(200, offset = 3)

    val MENTION_NAMES: List<String> = listOf(
        "Priya Raman", "Jordan Lee", "Sam Okafor", "Dana Ortiz", "Alex Rivera", "Maria-Fernanda Castellanos",
        "Chen Wei", "Fatima Al-Sayed", "Olusegun Adeyemi", "Kateryna Shevchenko", "Bartholomew Fitzgerald",
        "Nguyen Thi Minh Khai", "Sofia Rodriguez-Hernandez", "Jean-Baptiste Moreau", "Aiyana Whitehorse",
    )

    /** The plain-text form of 15 mentions. */
    val MENTIONS_PLAIN: String = MENTION_NAMES.joinToString(" ") { "@$it" } + " please read the handover notes."

    /** A TipTap document with 15 mention nodes, as the web composer sends it. */
    val MENTIONS_DOC: String = buildString {
        append("""{"type":"doc","content":[{"type":"paragraph","content":[""")
        MENTION_NAMES.forEachIndexed { i, name ->
            if (i > 0) append(""",{"type":"text","text":" "},""")
            append("""{"type":"mention","attrs":{"id":"usr_$i","label":"$name"}}""")
        }
        append(""",{"type":"text","text":" please read the handover notes."}]}]}""")
    }

    /** 120 emoji, no spaces. */
    val EMOJI_WALL: String = List(20) { "🎉👍❤️😂🙏🔥" }.joinToString("")

    /** Arabic, Hebrew and English in one message. */
    const val RTL_MIXED: String = "مرحبا، هل يمكنك تغطية زيارة يوم الجمعة بعد الظهر؟ The Hughes visit is at 3 pm. " +
        "שלום לכולם, המשמרת מתחילה בשעה שבע. شكرا جزيلا"

    /** A code-like string with very few break opportunities. */
    const val CODE_LIKE: String = "config.waivera.mobile.push.retryPolicy.maxBackoffMillisecondsBeforeGivingUp=30000;" +
        "config.waivera.mobile.realtime.reconnect.initialDelayMillis=250;" +
        "sprout_api.Services.ChatHub.OnDisconnectedAsync(System.Exception)+0x1f4"

    /** A long quoted reply as a user would paste it: "> " lines then an answer. */
    val QUOTE_REPLY: String = PARAGRAPHS_3000.take(600).lines().joinToString("\n") { "> $it" } +
        "\n\nYes, I can take Saturday."
}

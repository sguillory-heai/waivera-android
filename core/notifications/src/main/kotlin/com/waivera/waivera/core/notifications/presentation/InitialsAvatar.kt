package com.waivera.waivera.core.notifications.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.waivera.waivera.core.notifications.R
import java.util.Locale

/**
 * The sender or group picture on a chat notification and its conversation shortcut. Pushes carry
 * no photo, so this draws the person's initials on the brand accent, in the app's soft rounded
 * square; the system may mask it to its own shape in the conversation layout.
 */
internal object InitialsAvatar {
    /** Corner radius as a share of the side, matching the in-app avatar shape. */
    private const val CORNER_FRACTION = 0.28f
    private const val TEXT_FRACTION = 0.4f
    private const val MAX_INITIALS = 2

    fun icon(context: Context, name: String): IconCompat = IconCompat.createWithBitmap(bitmap(context, name))

    fun initials(name: String): String = name.trim()
        .split(WHITESPACE)
        .filter(String::isNotEmpty)
        .let { words -> if (words.size > MAX_INITIALS) listOf(words.first(), words.last()) else words }
        .joinToString("") { word -> word.substring(0, word.offsetByCodePoints(0, 1)) }
        .uppercase(Locale.getDefault())

    private fun bitmap(context: Context, name: String): Bitmap {
        val side = context.resources.getDimensionPixelSize(R.dimen.core_notifications_avatar_size)
        val bitmap = createBitmap(side, side)
        val canvas = Canvas(bitmap)
        val radius = side * CORNER_FRACTION
        val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, R.color.core_notifications_accent)
        }
        canvas.drawRoundRect(RectF(0f, 0f, side.toFloat(), side.toFloat()), radius, radius, background)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = side * TEXT_FRACTION
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val baseline = side / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(initials(name), side / 2f, baseline, text)
        return bitmap
    }

    private val WHITESPACE = Regex("\\s+")
}

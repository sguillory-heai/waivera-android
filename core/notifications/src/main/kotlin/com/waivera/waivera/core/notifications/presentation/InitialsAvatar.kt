package com.waivera.waivera.core.notifications.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import com.waivera.waivera.core.designsystem.theme.AvatarTiles
import com.waivera.waivera.core.notifications.R
import java.util.Locale

/**
 * The sender or group picture on a chat notification and its conversation shortcut: the
 * sender's photo when the push named them and the photo arrived in time, else the same
 * per-person tile the app shows for someone without a photo ([AvatarTiles]), in the app's soft
 * rounded square. The system may mask it to its own shape in the conversation layout.
 */
internal object InitialsAvatar {
    /** Corner radius as a share of the side, matching the in-app avatar shape. */
    private const val CORNER_FRACTION = 0.28f
    private const val TEXT_FRACTION = 0.4f
    private const val MAX_INITIALS = 2
    private const val SHEEN_CENTER = 0.15f
    private const val SHEEN_RADIUS = 0.95f

    fun icon(context: Context, name: String, photo: ByteArray? = null): IconCompat {
        val side = context.resources.getDimensionPixelSize(R.dimen.core_notifications_avatar_size)
        val picture = photo?.let { runCatching { photoBitmap(it, side) }.getOrNull() }
        return IconCompat.createWithBitmap(picture ?: tileBitmap(side, name))
    }

    fun initials(name: String): String = name.trim()
        .split(WHITESPACE)
        .filter(String::isNotEmpty)
        .let { words -> if (words.size > MAX_INITIALS) listOf(words.first(), words.last()) else words }
        .joinToString("") { word -> word.substring(0, word.offsetByCodePoints(0, 1)) }
        .uppercase(Locale.getDefault())

    /** The photo cropped to the centre square, in the rounded square; null if it can't be read. */
    private fun photoBitmap(bytes: ByteArray, side: Int): Bitmap? {
        val source = decode(bytes, side) ?: return null
        val scale = side.toFloat() / minOf(source.width, source.height)
        val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(
                Matrix().apply {
                    setScale(scale, scale)
                    postTranslate((side - source.width * scale) / 2f, (side - source.height * scale) / 2f)
                },
            )
        }
        val bitmap = createBitmap(side, side)
        val radius = side * CORNER_FRACTION
        Canvas(bitmap).drawRoundRect(
            RectF(0f, 0f, side.toFloat(), side.toFloat()),
            radius,
            radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader },
        )
        return bitmap
    }

    /** Decoded no larger than needed for [side]; null when the bytes aren't an image. */
    private fun decode(bytes: ByteArray, side: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= side) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } else {
            null
        }
    }

    private fun tileBitmap(side: Int, name: String): Bitmap {
        val bitmap = createBitmap(side, side)
        val canvas = Canvas(bitmap)
        val size = side.toFloat()
        val radius = side * CORNER_FRACTION
        val bounds = RectF(0f, 0f, size, size)
        val (start, end) = AvatarTiles.pairFor(name)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, size, size, start, end, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(bounds, radius, radius, fill)
        val sheen = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                size / 2f,
                -size * SHEEN_CENTER,
                size * SHEEN_RADIUS,
                white(AvatarTiles.SHEEN_ALPHA),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(bounds, radius, radius, sheen)
        val edgeWidth = size * AvatarTiles.EDGE_FRACTION
        val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = edgeWidth
            color = white(AvatarTiles.EDGE_ALPHA)
        }
        val inset = edgeWidth / 2f
        canvas.drawRoundRect(RectF(inset, inset, size - inset, size - inset), radius - inset, radius - inset, edge)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * TEXT_FRACTION
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
            setShadowLayer(size * AvatarTiles.SHADOW_BLUR, 0f, size * AvatarTiles.SHADOW_DROP, AvatarTiles.SHADOW_COLOR)
        }
        val baseline = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(initials(name), size / 2f, baseline, text)
        return bitmap
    }

    private fun white(alpha: Float): Int = Color.argb(alpha, 1f, 1f, 1f)

    private val WHITESPACE = Regex("\\s+")
}

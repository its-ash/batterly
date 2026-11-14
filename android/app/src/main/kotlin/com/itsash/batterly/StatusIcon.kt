package com.itsash.batterly

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon

/**
 * Draws [NotificationText.IconText] as a notification small icon: the number on top, the unit below,
 * white on transparent (the status bar tints it). Cached by text, since the service asks every 5 s.
 */
object StatusIcon {
    private const val SIZE = 96
    private var cacheKey: NotificationText.IconText? = null
    private var cacheIcon: Icon? = null

    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        textSize = SIZE * 0.34f
    }

    fun icon(text: NotificationText.IconText): Icon {
        if (text == cacheKey) cacheIcon?.let { return it }
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        // Largest number size that fits the width, at most 64% of the height.
        var size = SIZE * 0.64f
        numberPaint.textSize = size
        val width = numberPaint.measureText(text.value)
        if (width > SIZE * 0.98f) {
            size *= SIZE * 0.98f / width
            numberPaint.textSize = size
        }
        val numberBaseline = SIZE * 0.60f
        canvas.drawText(text.value, SIZE / 2f, numberBaseline, numberPaint)
        canvas.drawText(text.unit, SIZE / 2f, SIZE * 0.95f, unitPaint)
        val icon = Icon.createWithBitmap(bmp)
        cacheKey = text
        cacheIcon = icon
        return icon
    }
}

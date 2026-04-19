package com.sa.posprinter.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlin.math.min

class ReceiptBitmapGenerator(
    private val myanmarTypeface: Typeface?,
    private val myanmarTypefaceBold: Typeface?
){
    // Creates a single row bitmap with receiptItems (left, Myanmar font) and amount (right-English/Default font),
    fun createItemRowBitmap(itemName: String, amount: String, maxWidth: Int, textSize: Float = 18f): Bitmap? {
        return try {
            val leftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                this.textSize = textSize
                typeface = myanmarTypeface
            }
            val rightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                this.textSize = textSize
                typeface = Typeface.DEFAULT
            }

            val height = (textSize * 1.5f).toInt().coerceAtLeast(30)
            val bitmap = createBitmap(maxWidth, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            canvas.drawText(itemName, 0f, height - 10f, leftPaint)
            val amountWidth = rightPaint.measureText(amount)
            canvas.drawText(amount, maxWidth - amountWidth - 4f, height - 10f, rightPaint)

            convertToMonochrome(bitmap)
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error creating item row bitmap: ${e.message}")
            null
        }
    }

    // Draws leftText and rightText on a single bitmap (left with padding - Myantypeface, right-aligned),
    // then converts to monochrome for printer output.
    fun createLeftRightTextBitmap(leftText: String, rightText: String, maxWidth: Int, textSize: Float = 18f, leftPadding: Float = 10f): Bitmap? {
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.BLACK
            paint.textSize = textSize
            paint.typeface = myanmarTypeface

            val height = (textSize * 1.5f).toInt().coerceAtLeast(30)
            val bitmap = createBitmap(maxWidth, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            canvas.drawText(leftText, leftPadding, height - 10f, paint)
            val rightWidth = paint.measureText(rightText)
            canvas.drawText(rightText, maxWidth - rightWidth - 10f, height - 10f, paint)

            convertToMonochrome(bitmap)
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error creating left-right text bitmap: ${e.message}")
            null
        }
    }

    // Create bitmap from text with custom font
    fun createMyanmarTextBitmap(
        text: String,
        textSize: Float,
        maxWidth: Int,
        isBold: Boolean = false
    ): Bitmap? {
        return try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.color = Color.BLACK
            paint.textSize = textSize
            paint.typeface = if (isBold) myanmarTypefaceBold else myanmarTypeface

            // Measure text properly
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            val textWidth = paint.measureText(text).toInt()
            val textHeight = bounds.height()

            val width = min(textWidth, maxWidth).coerceAtLeast(1)
            val height = (textHeight + 20).coerceAtLeast(1)

            val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            // Draw text at baseline
            val x = if (textWidth > maxWidth) 0f else (width - textWidth) / 2f
            val y = height - 10f
            canvas.drawText(text, x, y, paint)

            convertToMonochrome(bitmap)
        } catch (e: Exception) {
            Log.e("PreviewActivity", "Error creating Myanmar text bitmap: ${e.message}")
            null
        }
    }

    // Converts a bitmap to monochrome (black & white) using grayscale threshold,
    // suitable for ESC/POS thermal printer compatibility.
    private fun convertToMonochrome(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val gray = (Color.red(pixel) * 0.299 + Color.green(pixel) * 0.587 + Color.blue(pixel) * 0.114).toInt()
            pixels[i] = if (gray < 128) Color.BLACK else Color.WHITE
        }

        val result = createBitmap(width, height)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
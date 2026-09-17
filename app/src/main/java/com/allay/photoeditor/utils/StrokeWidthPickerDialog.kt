package com.allay.photoeditor.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

/**
 * Dialog used to select annotation stroke width.
 *
 * The slider is presented in a dialog, matching the ColorPickerDialog flow:
 * change the value, preview it, then Apply or Cancel.
 */
class StrokeWidthPickerDialog(
    context: Context,
    initialStrokeWidth: Float,
    private val minStrokeWidth: Float = 4f,
    private val maxStrokeWidth: Float = 96f,
    private val onStrokeWidthSelected: (Float) -> Unit
) {

    private val safeMin = minStrokeWidth.coerceAtLeast(1f)
    private val safeMax = maxStrokeWidth.coerceAtLeast(safeMin)
    private val initial = initialStrokeWidth.coerceIn(safeMin, safeMax)

    private val dialog = AlertDialog.Builder(context)
        .setTitle("Stroke Size")
        .create()

    fun show() {
        val context = dialog.context
        val padding = dp(context, 20)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, dp(context, 4), padding, dp(context, 4))
        }

        val valueText = TextView(context).apply {
            text = "Size: ${initial.roundToInt()} px"
            textSize = 16f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, dp(context, 10))
        }

        val preview = StrokePreviewView(context).apply {
            strokeWidth = initial
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 54)
            ).apply {
                bottomMargin = dp(context, 8)
            }
        }

        val seekBar = SeekBar(context).apply {
            max = (safeMax - safeMin).roundToInt().coerceAtLeast(1)
            progress = (initial - safeMin).roundToInt().coerceIn(0, max)
        }

        val rangeText = TextView(context).apply {
            text = "${safeMin.roundToInt()} px     —     ${safeMax.roundToInt()} px"
            textSize = 12f
            setTextColor(Color.DKGRAY)
            setPadding(0, dp(context, 4), 0, 0)
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                val size = safeMin + progress
                valueText.text = "Size: ${size.roundToInt()} px"
                preview.strokeWidth = size
                preview.invalidate()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        container.addView(valueText)
        container.addView(preview)
        container.addView(seekBar)
        container.addView(rangeText)

        dialog.setView(container)
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { _, _ ->
            dialog.dismiss()
        }
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Apply") { _, _ ->
            val selected = safeMin + seekBar.progress
            onStrokeWidthSelected(selected.coerceIn(safeMin, safeMax))
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = safeMin + seekBar.progress
                onStrokeWidthSelected(selected.coerceIn(safeMin, safeMax))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).roundToInt()

    private class StrokePreviewView(context: Context) : View(context) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        var strokeWidth: Float = 24f
            set(value) {
                field = value
                paint.strokeWidth = value
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            paint.strokeWidth = strokeWidth
            val y = height / 2f
            canvas.drawLine(
                paddingLeft.toFloat() + 8f,
                y,
                width - paddingRight.toFloat() - 8f,
                y,
                paint
            )
        }
    }
}

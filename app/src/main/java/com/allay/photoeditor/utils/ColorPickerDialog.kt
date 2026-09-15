package com.allay.photoeditor.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.toColorInt
import kotlin.math.roundToInt

class ColorPickerDialog(
    context: Context,
    initialColor: Int = Color.WHITE,
    private val onColorSelected: (Int) -> Unit
) {

    private val dialog: AlertDialog

    private var selectedColor: Int = initialColor

    private var hue: Float
    private var saturation: Float
    private var brightness: Float

    private lateinit var colorPreview: View
    private lateinit var hexText: TextView

    init {

        val hsv = FloatArray(3)

        Color.colorToHSV(
            initialColor,
            hsv
        )

        hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]

        // -------------------------------------------------------------
        // ROOT
        // -------------------------------------------------------------

        val root = LinearLayout(context).apply {

            orientation = LinearLayout.VERTICAL

            setPadding(
                dp(context, 20),
                dp(context, 10),
                dp(context, 20),
                dp(context, 10)
            )
        }

        // -------------------------------------------------------------
        // SATURATION / BRIGHTNESS PICKER
        // -------------------------------------------------------------

        val saturationBrightnessView =
            SaturationBrightnessView(context).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(context, 240)
                    )
            }

        root.addView(
            saturationBrightnessView
        )

        // -------------------------------------------------------------
        // HUE PICKER
        // -------------------------------------------------------------

        val hueView =
            HuePickerView(context).apply {

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(context, 40)
                    ).apply {

                        topMargin =
                            dp(context, 20)
                    }
            }

        root.addView(
            hueView
        )

        // -------------------------------------------------------------
        // COLOR PREVIEW
        // -------------------------------------------------------------

        colorPreview = View(context).apply {

            setBackgroundColor(
                selectedColor
            )

            layoutParams =
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(context, 50)
                ).apply {

                    topMargin =
                        dp(context, 20)
                }
        }

        root.addView(
            colorPreview
        )

        // -------------------------------------------------------------
        // HEX VALUE
        // -------------------------------------------------------------

        hexText =
            TextView(context).apply {

                text =
                    colorToHex(selectedColor)

                textSize = 16f

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(context, 10),
                    0,
                    dp(context, 10)
                )
            }

        root.addView(
            hexText
        )

        // -------------------------------------------------------------
        // HEX INPUT
        // -------------------------------------------------------------

        val hexInput =
            EditText(context).apply {

                hint = "#FFFFFF"

                setSingleLine(true)

                setText(
                    colorToHex(selectedColor)
                )

                gravity =
                    Gravity.CENTER

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dp(context, 50)
                    )
            }

        root.addView(
            hexInput
        )

        // -------------------------------------------------------------
        // DIALOG
        // -------------------------------------------------------------

        dialog =
            AlertDialog.Builder(context)
                .setTitle("Choose Color")
                .setView(root)
                .setNegativeButton(
                    "Cancel",
                    null
                )
                .setPositiveButton(
                    "Apply",
                    null
                )
                .create()

        // -------------------------------------------------------------
        // HUE CALLBACK
        // -------------------------------------------------------------

        hueView.onHueChanged = { newHue ->

            hue = newHue

            saturationBrightnessView.updateHue(
                hue
            )

            updateColor()

            hexInput.setText(
                colorToHex(selectedColor)
            )
        }

        // -------------------------------------------------------------
        // SATURATION / BRIGHTNESS CALLBACK
        // -------------------------------------------------------------

        saturationBrightnessView.onColorChanged =
            { newSaturation, newBrightness ->

                saturation =
                    newSaturation

                brightness =
                    newBrightness

                updateColor()

                hexInput.setText(
                    colorToHex(selectedColor)
                )
            }

        // -------------------------------------------------------------
        // HEX INPUT
        // -------------------------------------------------------------

        hexInput.setOnFocusChangeListener { _, hasFocus ->

            if (!hasFocus) {

                val color =
                    parseHexColor(
                        hexInput.text.toString()
                    )

                if (color != null) {

                    setColor(
                        color
                    )

                    saturationBrightnessView
                        .updateColor(
                            saturation,
                            brightness
                        )

                    hueView.updateHue(
                        hue
                    )
                } else {

                    hexInput.setText(
                        colorToHex(selectedColor)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // SHOW LISTENER
        // -------------------------------------------------------------

        dialog.setOnShowListener {

            dialog.getButton(
                AlertDialog.BUTTON_POSITIVE
            ).setOnClickListener {

                val typedHex =
                    hexInput.text
                        .toString()
                        .trim()

                val color =
                    parseHexColor(
                        typedHex
                    )

                if (color != null) {

                    selectedColor =
                        color
                }

                onColorSelected(
                    selectedColor
                )

                dialog.dismiss()
            }
        }

        // -------------------------------------------------------------
        // INITIAL STATE
        // -------------------------------------------------------------

        saturationBrightnessView.updateHue(
            hue
        )

        saturationBrightnessView.updateColor(
            saturation,
            brightness
        )
    }

    fun show() {
        dialog.show()
    }

    // =================================================================
    // COLOR UPDATE
    // =================================================================

    private fun updateColor() {

        selectedColor =
            Color.HSVToColor(
                floatArrayOf(
                    hue,
                    saturation,
                    brightness
                )
            )

        colorPreview.setBackgroundColor(
            selectedColor
        )

        hexText.text =
            colorToHex(selectedColor)
    }

    private fun setColor(
        color: Int
    ) {

        selectedColor =
            color

        val hsv =
            FloatArray(3)

        Color.colorToHSV(
            color,
            hsv
        )

        hue =
            hsv[0]

        saturation =
            hsv[1]

        brightness =
            hsv[2]

        updateColor()
    }

    // =================================================================
    // SATURATION / BRIGHTNESS VIEW
    // =================================================================

    private inner class SaturationBrightnessView(
        context: Context
    ) : View(context) {

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val selectorPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(context, 2).toFloat()

                color =
                    Color.WHITE
            }

        private var bitmap: Bitmap? = null

        private var currentSaturation =
            saturation

        private var currentBrightness =
            brightness

        var onColorChanged:
                ((Float, Float) -> Unit)? = null

        fun updateHue(
            newHue: Float
        ) {

            hue =
                newHue

            bitmap =
                null

            invalidate()
        }

        fun updateColor(
            newSaturation: Float,
            newBrightness: Float
        ) {

            currentSaturation =
                newSaturation

            currentBrightness =
                newBrightness

            invalidate()
        }

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(canvas)

            if (width <= 0 || height <= 0) {
                return
            }

            drawColorArea(
                canvas
            )

            drawSelector(
                canvas
            )
        }

        private fun drawColorArea(
            canvas: Canvas
        ) {

            val baseColor =
                Color.HSVToColor(
                    floatArrayOf(
                        hue,
                        1f,
                        1f
                    )
                )

            // ---------------------------------------------------------
            // SATURATION GRADIENT
            // ---------------------------------------------------------

            val saturationShader =
                LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    0f,
                    Color.WHITE,
                    baseColor,
                    Shader.TileMode.CLAMP
                )

            // ---------------------------------------------------------
            // BRIGHTNESS GRADIENT
            // ---------------------------------------------------------

            val brightnessShader =
                LinearGradient(
                    0f,
                    0f,
                    0f,
                    height.toFloat(),
                    Color.TRANSPARENT,
                    Color.BLACK,
                    Shader.TileMode.CLAMP
                )

            val saturationPaint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {

                    shader =
                        saturationShader
                }

            val brightnessPaint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {

                    shader =
                        brightnessShader
                }

            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                saturationPaint
            )

            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                brightnessPaint
            )
        }

        private fun drawSelector(
            canvas: Canvas
        ) {

            val x =
                currentSaturation *
                        width

            val y =
                (1f - currentBrightness) *
                        height

            canvas.drawCircle(
                x,
                y,
                dp(context, 9).toFloat(),
                selectorPaint
            )
        }

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {

            when (event.action) {

                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {

                    val x =
                        event.x.coerceIn(
                            0f,
                            width.toFloat()
                        )

                    val y =
                        event.y.coerceIn(
                            0f,
                            height.toFloat()
                        )

                    currentSaturation =
                        if (width == 0) {
                            0f
                        } else {
                            x / width
                        }

                    currentBrightness =
                        if (height == 0) {
                            1f
                        } else {
                            1f - (y / height)
                        }

                    onColorChanged?.invoke(
                        currentSaturation,
                        currentBrightness
                    )

                    invalidate()

                    return true
                }
            }

            return true
        }
    }

    // =================================================================
    // HUE VIEW
    // =================================================================

    private inner class HuePickerView(
        context: Context
    ) : View(context) {

        private val paint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val selectorPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    dp(context, 2).toFloat()

                color =
                    Color.WHITE
            }

        var onHueChanged:
                ((Float) -> Unit)? = null

        override fun onDraw(
            canvas: Canvas
        ) {

            super.onDraw(canvas)

            if (width <= 0 || height <= 0) {
                return
            }

            val colors =
                intArrayOf(
                    Color.RED,
                    Color.MAGENTA,
                    Color.BLUE,
                    Color.CYAN,
                    Color.GREEN,
                    Color.YELLOW,
                    Color.RED
                )

            val shader =
                LinearGradient(
                    0f,
                    0f,
                    width.toFloat(),
                    0f,
                    colors,
                    null,
                    Shader.TileMode.CLAMP
                )

            paint.shader =
                shader

            canvas.drawRect(
                0f,
                0f,
                width.toFloat(),
                height.toFloat(),
                paint
            )

            paint.shader =
                null

            val x =
                (hue / 360f) *
                        width

            canvas.drawRect(
                x - dp(context, 3),
                0f,
                x + dp(context, 3),
                height.toFloat(),
                selectorPaint
            )
        }

        fun updateHue(
            newHue: Float
        ) {

            hue =
                newHue.coerceIn(
                    0f,
                    360f
                )

            invalidate()
        }

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {

            when (event.action) {

                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE -> {

                    val x =
                        event.x.coerceIn(
                            0f,
                            width.toFloat()
                        )

                    hue =
                        if (width == 0) {
                            0f
                        } else {
                            (x / width) * 360f
                        }

                    onHueChanged?.invoke(
                        hue
                    )

                    invalidate()

                    return true
                }
            }

            return true
        }
    }

    // =================================================================
    // HELPERS
    // =================================================================

    private fun colorToHex(
        color: Int
    ): String {

        return String.format(
            "#%06X",
            0xFFFFFF and color
        )
    }

    private fun parseHexColor(
        value: String
    ): Int? {

        return try {

            var hex =
                value.trim()

            if (!hex.startsWith("#")) {

                hex =
                    "#$hex"
            }

            if (
                hex.length == 7 &&
                hex.matches(
                    Regex("#[0-9A-Fa-f]{6}")
                )
            ) {

                hex.toColorInt()

            } else {

                null
            }

        } catch (
            exception: Exception
        ) {

            null
        }
    }

    private fun dp(
        context: Context,
        value: Int
    ): Int {

        return (
                value *
                        context.resources
                            .displayMetrics
                            .density
                ).roundToInt()
    }
}

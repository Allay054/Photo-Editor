package com.allay.photoeditor.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.allay.photoeditor.model.AdjustmentState
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Centralized image-adjustment processing pipeline.
 *
 * Phase 7.7.2:
 * - Avoids processing when the adjustment state is neutral.
 * - Avoids unnecessary intermediate bitmap allocations.
 * - Reuses a single pixel buffer for pixel-based adjustments.
 * - Keeps all adjustment processing in one place.
 *
 * Processing order:
 *
 * Brightness
 *      ↓
 * Contrast
 *      ↓
 * Saturation
 *      ↓
 * Exposure
 *      ↓
 * Temperature
 *      ↓
 * Highlights
 *      ↓
 * Shadows
 */
object AdjustmentProcessingPipeline {

    private const val MAX_COLOR = 255

    /**
     * Process [source] using [state].
     *
     * The source bitmap is never modified.
     *
     * If the adjustment state is completely neutral, the original bitmap
     * is returned directly to avoid creating an unnecessary bitmap.
     */
    fun process(
        source: Bitmap,
        state: AdjustmentState
    ): Bitmap {

        if (state.isNeutral()) {
            return source
        }

        var result = source

        /*
         * Global ColorMatrix adjustments.
         *
         * We combine the adjustments into one matrix where possible so
         * that we don't repeatedly create intermediate bitmaps.
         */
        val colorMatrix = ColorMatrix()

        var hasColorMatrixChanges = false

        if (state.brightness != 0f) {
            applyBrightness(
                colorMatrix = colorMatrix,
                brightness = state.brightness
            )

            hasColorMatrixChanges = true
        }

        if (state.contrast != 0f) {
            applyContrast(
                colorMatrix = colorMatrix,
                contrast = state.contrast
            )

            hasColorMatrixChanges = true
        }

        if (state.saturation != 0f) {
            applySaturation(
                colorMatrix = colorMatrix,
                saturation = state.saturation
            )

            hasColorMatrixChanges = true
        }

        if (state.exposure != 0f) {
            applyExposure(
                colorMatrix = colorMatrix,
                exposure = state.exposure
            )

            hasColorMatrixChanges = true
        }

        if (state.temperature != 0f) {
            applyTemperature(
                colorMatrix = colorMatrix,
                temperature = state.temperature
            )

            hasColorMatrixChanges = true
        }

        if (hasColorMatrixChanges) {
            result = applyColorMatrix(
                source = result,
                colorMatrix = colorMatrix
            )
        }

        /*
         * Pixel-based adjustments.
         *
         * One IntArray is allocated and reused for both Highlights
         * and Shadows instead of creating a separate array for each stage.
         */
        if (
            state.highlights != 0f ||
            state.shadows != 0f
        ) {
            result = applyPixelAdjustments(
                source = result,
                highlights = state.highlights,
                shadows = state.shadows
            )
        }

        return result
    }

    private fun applyColorMatrix(
        source: Bitmap,
        colorMatrix: ColorMatrix
    ): Bitmap {

        val output = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }

        canvas.drawBitmap(
            source,
            0f,
            0f,
            paint
        )

        return output
    }

    private fun applyBrightness(
        colorMatrix: ColorMatrix,
        brightness: Float
    ) {
        /*
         * Brightness is represented as 0..100.
         *
         * Map it to a useful RGB offset.
         */
        val offset = brightness * 2.55f

        val matrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f
            )
        )

        colorMatrix.postConcat(matrix)
    }

    private fun applyContrast(
        colorMatrix: ColorMatrix,
        contrast: Float
    ) {
        /*
         * 0 = neutral
         * 100 = strong contrast
         */
        val factor = 1f + (contrast / 100f)

        val translate =
            128f * (1f - factor)

        val matrix = ColorMatrix(
            floatArrayOf(
                factor, 0f, 0f, 0f, translate,
                0f, factor, 0f, 0f, translate,
                0f, 0f, factor, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )

        colorMatrix.postConcat(matrix)
    }

    private fun applySaturation(
        colorMatrix: ColorMatrix,
        saturation: Float
    ) {
        /*
         * 0 = neutral
         *
         * Positive values increase saturation.
         */
        val factor = 1f + (saturation / 100f)

        val matrix = ColorMatrix().apply {
            setSaturation(factor)
        }

        colorMatrix.postConcat(matrix)
    }

    private fun applyExposure(
        colorMatrix: ColorMatrix,
        exposure: Float
    ) {
        /*
         * 0..100 maps approximately to 0..+1.5 EV.
         */
        val exposureEv = (exposure / 100f) * 1.5f

        val exposureMultiplier =
            2f.pow(exposureEv)

        val matrix = ColorMatrix(
            floatArrayOf(
                exposureMultiplier, 0f, 0f, 0f, 0f,
                0f, exposureMultiplier, 0f, 0f, 0f,
                0f, 0f, exposureMultiplier, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        colorMatrix.postConcat(matrix)
    }

    private fun applyTemperature(
        colorMatrix: ColorMatrix,
        temperature: Float
    ) {
        /*
         * Positive temperature produces a warmer image:
         * - increase red
         * - slightly increase green
         * - reduce blue
         */
        val normalized =
            temperature / 100f

        val red =
            1f + (0.08f * normalized)

        val green =
            1f + (0.02f * normalized)

        val blue =
            1f - (0.08f * normalized)

        val matrix = ColorMatrix(
            floatArrayOf(
                red, 0f, 0f, 0f, 0f,
                0f, green, 0f, 0f, 0f,
                0f, 0f, blue, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )

        colorMatrix.postConcat(matrix)
    }

    private fun applyPixelAdjustments(
        source: Bitmap,
        highlights: Float,
        shadows: Float
    ): Bitmap {

        val width = source.width
        val height = source.height

        /*
         * One output bitmap is created for the entire pixel-based stage.
         */
        val output = Bitmap.createBitmap(
            width,
            height,
            Bitmap.Config.ARGB_8888
        )

        /*
         * One reusable pixel buffer.
         *
         * This buffer is used for:
         * - reading source pixels
         * - processing highlights
         * - processing shadows
         * - writing the final pixels
         */
        val pixels = IntArray(width * height)

        source.getPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        val highlightAmount =
            highlights / 100f

        val shadowAmount =
            shadows / 100f

        for (index in pixels.indices) {

            val color = pixels[index]

            val alpha =
                (color ushr 24) and 0xFF

            var red =
                (color ushr 16) and 0xFF

            var green =
                (color ushr 8) and 0xFF

            var blue =
                color and 0xFF

            /*
             * Calculate perceived luminance.
             *
             * This lets us target bright and dark pixels differently.
             */
            val luminance =
                (
                        0.2126f * red +
                                0.7152f * green +
                                0.0722f * blue
                        ) / 255f

            /*
             * Highlights:
             *
             * Brighter pixels receive more adjustment.
             * Dark pixels remain mostly protected.
             */
            if (highlightAmount != 0f) {

                val highlightWeight =
                    luminance * luminance

                val highlightLift =
                    highlightAmount *
                            highlightWeight *
                            45f

                red += highlightLift.roundToInt()
                green += highlightLift.roundToInt()
                blue += highlightLift.roundToInt()
            }

            /*
             * Shadows:
             *
             * Darker pixels receive more adjustment.
             * Bright pixels remain mostly protected.
             */
            if (shadowAmount != 0f) {

                val shadowWeight =
                    (1f - luminance) *
                            (1f - luminance)

                val shadowLift =
                    shadowAmount *
                            shadowWeight *
                            55f

                red += shadowLift.roundToInt()
                green += shadowLift.roundToInt()
                blue += shadowLift.roundToInt()
            }

            red = clamp(red)
            green = clamp(green)
            blue = clamp(blue)

            pixels[index] =
                (alpha shl 24) or
                        (red shl 16) or
                        (green shl 8) or
                        blue
        }

        output.setPixels(
            pixels,
            0,
            width,
            0,
            0,
            width,
            height
        )

        return output
    }

    private fun clamp(
        value: Int
    ): Int {
        return min(
            MAX_COLOR,
            max(0, value)
        )
    }

    private fun AdjustmentState.isNeutral(): Boolean {
        return brightness == 0f &&
                contrast == 0f &&
                saturation == 0f &&
                exposure == 0f &&
                temperature == 0f &&
                highlights == 0f &&
                shadows == 0f
    }
}
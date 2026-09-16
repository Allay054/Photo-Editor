package com.allay.photoeditor.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.allay.photoeditor.model.FilterType

/**
 * Pixel-processing boundary for the Photo Editor filter system.
 *
 * Filters are always generated from the original bitmap of the active filter
 * session. This keeps filter selection non-destructive and prevents quality
 * loss from repeatedly filtering an already filtered preview.
 */
object ImageFilterProcessor {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun process(
        source: Bitmap,
        filter: FilterType
    ): Bitmap {
        if (filter == FilterType.ORIGINAL) {
            return source
        }

        val output = Bitmap.createBitmap(
            source.width,
            source.height,
            Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(output)
        val colorMatrix = createColorMatrix(filter)

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(source, 0f, 0f, paint)
        paint.colorFilter = null

        return output
    }

    private fun createColorMatrix(filter: FilterType): ColorMatrix {
        return when (filter) {
            FilterType.ORIGINAL -> ColorMatrix()

            FilterType.GRAYSCALE -> ColorMatrix().apply {
                setSaturation(0f)
            }

            FilterType.BLACK_WHITE -> ColorMatrix(
                floatArrayOf(
                    1.4f, 1.4f, 1.4f, 0f, -180f,
                    1.4f, 1.4f, 1.4f, 0f, -180f,
                    1.4f, 1.4f, 1.4f, 0f, -180f,
                    0f,   0f,   0f,   1f,    0f
                )
            )

            FilterType.SEPIA -> ColorMatrix(
                floatArrayOf(
                    0.393f, 0.769f, 0.189f, 0f, 0f,
                    0.349f, 0.686f, 0.168f, 0f, 0f,
                    0.272f, 0.534f, 0.131f, 0f, 0f,
                    0f,     0f,     0f,     1f, 0f
                )
            )

            FilterType.VINTAGE -> {
                val matrix = ColorMatrix()
                matrix.setSaturation(0.65f)
                matrix.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.08f, 0f,    0f,    0f, -8f,
                            0f,    1.02f, 0f,    0f,  2f,
                            0f,    0f,    0.88f, 0f, 12f,
                            0f,    0f,    0f,    1f,  0f
                        )
                    )
                )
                matrix
            }

            FilterType.WARM -> ColorMatrix(
                floatArrayOf(
                    1.08f, 0f,    0f,    0f,  8f,
                    0f,    1.02f, 0f,    0f,  3f,
                    0f,    0f,    0.92f, 0f, -4f,
                    0f,    0f,    0f,    1f,  0f
                )
            )

            FilterType.COOL -> ColorMatrix(
                floatArrayOf(
                    0.92f, 0f,    0f,    0f, -3f,
                    0f,    1.01f, 0f,    0f,  1f,
                    0f,    0f,    1.08f, 0f,  8f,
                    0f,    0f,    0f,    1f,  0f
                )
            )
        }
    }
}

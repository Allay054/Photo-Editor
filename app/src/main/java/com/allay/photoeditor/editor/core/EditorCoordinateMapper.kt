package com.allay.photoeditor.editor.core

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import kotlin.math.min

/**
 * Owns conversion between the editor's image coordinate space and View
 * screen coordinate space.
 *
 * The editor stores element positions and annotation paths in image pixels.
 * This class keeps the existing viewport math (fit scale, user zoom and pan)
 * in one place while allowing PhotoEditorView to remain the public coordinator.
 */
class EditorCoordinateMapper(
    private val getBitmap: () -> Bitmap?,
    private val getViewWidth: () -> Int,
    private val getViewHeight: () -> Int,
    private val getScaleFactor: () -> Float,
    private val getTranslationX: () -> Float,
    private val getTranslationY: () -> Float
) {

    val imageToScreenMatrix = Matrix()
    val screenToImageMatrix = Matrix()

    /**
     * Returns the scale required to fit the current bitmap inside the View.
     */
    fun getFitScale(): Float {
        val bitmap = getBitmap() ?: return 1f
        val viewWidth = getViewWidth()
        val viewHeight = getViewHeight()

        if (viewWidth <= 0 || viewHeight <= 0) {
            return 1f
        }

        return min(
            viewWidth.toFloat() / bitmap.width,
            viewHeight.toFloat() / bitmap.height
        )
    }

    /**
     * Rebuilds both image -> screen and screen -> image matrices using the
     * current bitmap, View size, zoom and pan.
     *
     * This intentionally preserves the existing PhotoEditorView transform
     * calculation exactly.
     */
    fun updateMatrices() {
        val bitmap = getBitmap() ?: return
        val viewWidth = getViewWidth()
        val viewHeight = getViewHeight()

        if (viewWidth <= 0 || viewHeight <= 0) {
            return
        }

        val fitScale = getFitScale()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        val scale = fitScale * getScaleFactor()

        imageToScreenMatrix.reset()
        imageToScreenMatrix.postTranslate(
            -bitmap.width / 2f,
            -bitmap.height / 2f
        )
        imageToScreenMatrix.postScale(
            scale,
            scale
        )
        imageToScreenMatrix.postTranslate(
            centerX + getTranslationX(),
            centerY + getTranslationY()
        )

        imageToScreenMatrix.invert(screenToImageMatrix)
    }

    /**
     * Converts an image-space point to View screen coordinates.
     */
    fun imageToScreen(imageX: Float, imageY: Float): PointF? {
        if (getBitmap() == null) {
            return null
        }

        updateMatrices()

        val points = floatArrayOf(imageX, imageY)
        imageToScreenMatrix.mapPoints(points)

        return PointF(points[0], points[1])
    }

    /**
     * Converts a View screen-space point to image coordinates.
     */
    fun screenToImage(screenX: Float, screenY: Float): PointF? {
        if (getBitmap() == null) {
            return null
        }

        updateMatrices()

        val points = floatArrayOf(screenX, screenY)
        screenToImageMatrix.mapPoints(points)

        return PointF(points[0], points[1])
    }

    /**
     * Returns the current effective image-to-screen scale.
     */
    fun currentScreenScale(): Float {
        updateMatrices()

        val values = FloatArray(9)
        imageToScreenMatrix.getValues(values)

        val scaleX = values[Matrix.MSCALE_X].toDouble()
        val skewX = values[Matrix.MSKEW_X].toDouble()

        return kotlin.math.hypot(
            scaleX,
            skewX
        ).toFloat().coerceAtLeast(0.001f)
    }
}

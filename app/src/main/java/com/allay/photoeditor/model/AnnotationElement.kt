package com.allay.photoeditor.model

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PathMeasure
import kotlin.math.hypot

/**
 * Represents a drawing/annotation element on the editor canvas.
 *
 * Annotation paths are stored in image coordinates, consistent with the
 * existing EditorElement architecture. The visual paint is kept in sync
 * with the element's color and stroke width so the selected annotation
 * settings are actually reflected when the element is rendered.
 */
class AnnotationElement(
    val annotationType: AnnotationType,
    val path: Path,
    color: Int,
    strokeWidth: Float
) : EditorElement() {

    companion object {
        /** Fixed opacity used by the highlighter tool. */
        private const val HIGHLIGHTER_ALPHA = 96
    }

    var color: Int = color
        private set

    var strokeWidth: Float = strokeWidth
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = color
        this.strokeWidth = strokeWidth
    }

    /**
     * Updates the visual color of this annotation.
     * Existing annotations can therefore be restyled later without
     * rebuilding the path.
     */
    fun setColor(color: Int) {
        this.color = color
        paint.color = color
    }

    /**
     * Updates the visual stroke width of this annotation.
     */
    fun setStrokeWidth(strokeWidth: Float) {
        require(strokeWidth > 0f) {
            "Stroke width must be greater than zero."
        }

        this.strokeWidth = strokeWidth
        paint.strokeWidth = strokeWidth
    }

    override fun draw(
        canvas: Canvas,
        matrix: Matrix
    ) {
        val transformedPath = Path(path)
        transformedPath.transform(matrix)

        // Re-apply the current values immediately before drawing. This keeps
        // the renderer correct even if the annotation has been restyled.
        paint.color = color
        paint.strokeWidth = strokeWidth
        paint.alpha = if (annotationType == AnnotationType.HIGHLIGHTER) {
            HIGHLIGHTER_ALPHA
        } else {
            255
        }

        canvas.drawPath(
            transformedPath,
            paint
        )
    }

    /**
     * Erases the portion of this annotation that falls inside the supplied
     * image-space eraser circle. Returns false when the entire annotation
     * has been erased.
     *
     * Annotation paths in this project are created from line segments, so the
     * path is safely rebuilt from sampled points after removing the points
     * covered by the eraser. Multiple remaining sections are preserved using
     * moveTo(), so an eraser stroke can create gaps without affecting the
     * original bitmap or other editor elements.
     */
    fun eraseAt(
        x: Float,
        y: Float,
        radius: Float
    ): Boolean {
        if (radius <= 0f) return true

        val measure = PathMeasure(path, false)
        val rebuiltPath = Path()
        val position = FloatArray(2)
        val step = (strokeWidth / 4f).coerceIn(1f, 12f)
        var hasRemainingPoint = false
        var contourFound = false

        do {
            val contourLength = measure.length
            if (contourLength > 0f) {
                contourFound = true
                var lastRemaining = false
                var distance = 0f

                while (distance < contourLength) {
                    if (!measure.getPosTan(distance, position, null)) break

                    val dx = position[0] - x
                    val dy = position[1] - y
                    val inside = hypot(dx.toDouble(), dy.toDouble()) <= radius

                    if (!inside) {
                        if (!lastRemaining) {
                            rebuiltPath.moveTo(position[0], position[1])
                        } else {
                            rebuiltPath.lineTo(position[0], position[1])
                        }
                        hasRemainingPoint = true
                    }

                    lastRemaining = !inside
                    distance += step
                }

                // Always evaluate the exact end point of this contour.
                if (measure.getPosTan(contourLength, position, null)) {
                    val dx = position[0] - x
                    val dy = position[1] - y
                    val inside = hypot(dx.toDouble(), dy.toDouble()) <= radius

                    if (!inside) {
                        if (!lastRemaining) {
                            rebuiltPath.moveTo(position[0], position[1])
                        } else {
                            rebuiltPath.lineTo(position[0], position[1])
                        }
                        hasRemainingPoint = true
                    }
                }
            }
        } while (measure.nextContour())

        if (!contourFound) {
            path.reset()
            return false
        }

        if (!hasRemainingPoint) {
            path.reset()
            return false
        }

        path.reset()
        path.addPath(rebuiltPath)
        return true
    }

    override fun getBounds(): RectF {
        val bounds = RectF()

        path.computeBounds(
            bounds,
            true
        )

        val padding = strokeWidth / 2f

        bounds.inset(
            -padding,
            -padding
        )

        return bounds
    }

    override fun contains(
        x: Float,
        y: Float
    ): Boolean {
        return getBounds().contains(x, y)
    }

    override fun moveBy(
        dx: Float,
        dy: Float
    ) {
        path.offset(dx, dy)
    }

    // =========================================================================
    // LAYER DUPLICATION - PHASE 10.4
    // =========================================================================

    /** Creates an independent copy for layer duplication. */
    override fun duplicate(): AnnotationElement {
        return AnnotationElement(
            annotationType = annotationType,
            path = Path(path),
            color = color,
            strokeWidth = strokeWidth
        ).also { copy ->
            copy.isSelected = false
            copy.isVisible = true
        }
    }

}

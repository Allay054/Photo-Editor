package com.allay.photoeditor.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Generic shape element.
 *
 * A single ShapeElement supports multiple ShapeType values.
 *
 * Supported shapes:
 * - Rectangle
 * - Circle / Ellipse
 * - Rounded Rectangle
 * - Triangle
 * - Line
 * - Arrow
 * - Pointer
 *
 * ShapeElement also supports:
 * - Selection
 * - Movement
 * - Scaling
 * - Rotation
 * - Color
 * - Stroke width
 * - Fill / outline
 */
class ShapeElement(
    var shapeType: ShapeType,
    var position: PointF,
    var width: Float = DEFAULT_WIDTH,
    var height: Float = DEFAULT_HEIGHT,
    var color: Int = Color.WHITE,
    var strokeWidth: Float = DEFAULT_STROKE_WIDTH,
    var isFilled: Boolean = false,
    var rotation: Float = 0f,
    var scale: Float = 1f
) : EditorElement() {

    companion object {

        // =====================================================================
        // DEFAULTS
        // =====================================================================

        const val DEFAULT_WIDTH = 300f
        const val DEFAULT_HEIGHT = 200f

        const val DEFAULT_STROKE_WIDTH = 8f

        // =====================================================================
        // LIMITS
        // =====================================================================

        const val MIN_WIDTH = 20f
        const val MIN_HEIGHT = 20f

        const val MIN_SCALE = 0.1f
        const val MAX_SCALE = 10f

        // =====================================================================
        // ARROW
        // =====================================================================

        private const val ARROW_HEAD_LENGTH_RATIO = 0.22f

        private const val ARROW_HEAD_WIDTH_RATIO = 0.55f

        private const val MIN_ARROW_HEAD_LENGTH = 30f

        private const val MAX_ARROW_HEAD_LENGTH = 90f

        private const val POINTER_HEAD_WIDTH_RATIO = 0.75f

        // =====================================================================
        // SELECTION
        // =====================================================================

        /**
         * Extra space around a selected shape.
         *
         * This is intentionally small because the actual
         * resize / rotation handles will be implemented
         * in Phase 8.5.
         */
        private const val SELECTION_PADDING = 14f

        private const val SELECTION_STROKE_WIDTH = 3f
    }

    // =========================================================================
    // SHAPE PAINT
    // =========================================================================

    private val shapePaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = this@ShapeElement.color
            style = Paint.Style.STROKE
            strokeWidth = this@ShapeElement.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    // =========================================================================
    // SELECTION PAINT
    // =========================================================================

    /**
     * Paint used to indicate that this shape is selected.
     *
     * This is deliberately independent from shapePaint so
     * selecting a shape never changes its actual color,
     * fill state or stroke width.
     */
    private val selectionPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = SELECTION_STROKE_WIDTH
            color = Color.WHITE
            strokeCap = Paint.Cap.SQUARE
            strokeJoin = Paint.Join.MITER
        }

    // =========================================================================
    // DRAW
    // =========================================================================

    override fun draw(
        canvas: Canvas,
        matrix: Matrix
    ) {

        val safeScale =
            scale.coerceIn(
                MIN_SCALE,
                MAX_SCALE
            )

        val safeWidth =
            width.coerceAtLeast(
                MIN_WIDTH
            )

        val safeHeight =
            height.coerceAtLeast(
                MIN_HEIGHT
            )

        canvas.save()

        /*
         * Convert image coordinates into screen coordinates.
         */
        canvas.concat(matrix)

        /*
         * Shape position is the center of the element.
         */
        canvas.translate(
            position.x,
            position.y
        )

        /*
         * Apply element rotation.
         */
        canvas.rotate(
            rotation
        )

        /*
         * Apply element scale.
         */
        canvas.scale(
            safeScale,
            safeScale
        )

        updatePaint()

        // =====================================================================
        // DRAW SHAPE
        // =====================================================================

        when (shapeType) {

            ShapeType.RECTANGLE -> {

                drawRectangle(
                    canvas = canvas,
                    width = safeWidth,
                    height = safeHeight
                )
            }

            ShapeType.CIRCLE -> {

                drawCircle(
                    canvas = canvas,
                    width = safeWidth,
                    height = safeHeight
                )
            }

            ShapeType.ROUNDED_RECTANGLE -> {

                drawRoundedRectangle(
                    canvas = canvas,
                    width = safeWidth,
                    height = safeHeight
                )
            }

            ShapeType.TRIANGLE -> {

                drawTriangle(
                    canvas = canvas,
                    width = safeWidth,
                    height = safeHeight
                )
            }

            ShapeType.LINE -> {

                drawLine(
                    canvas = canvas,
                    width = safeWidth
                )
            }

            ShapeType.ARROW -> {

                drawArrow(
                    canvas = canvas,
                    width = safeWidth
                )
            }

            ShapeType.POINTER -> {

                drawPointer(
                    canvas = canvas,
                    width = safeWidth
                )
            }
        }

        // =====================================================================
        // DRAW SELECTION
        // =====================================================================

        if (isSelected) {

            drawSelection(
                canvas = canvas,
                width = safeWidth,
                height = safeHeight
            )
        }

        canvas.restore()
    }

    // =========================================================================
    // UPDATE PAINT
    // =========================================================================

    private fun updatePaint() {

        shapePaint.color =
            color

        shapePaint.strokeWidth =
            strokeWidth.coerceAtLeast(
                1f
            )

        /*
         * Lines, arrows and pointers are always
         * rendered as strokes.
         */
        shapePaint.style =
            when {

                shapeType == ShapeType.LINE ||
                        shapeType == ShapeType.ARROW ||
                        shapeType == ShapeType.POINTER -> {

                    Paint.Style.STROKE
                }

                isFilled -> {

                    Paint.Style.FILL
                }

                else -> {

                    Paint.Style.STROKE
                }
            }
    }

    // =========================================================================
    // SELECTION
    // =========================================================================

    /**
     * Draws a lightweight selection border around
     * the shape.
     *
     * Resize and rotation handles are intentionally
     * NOT implemented here.
     *
     * Those belong to Phase 8.5.
     */
    private fun drawSelection(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val halfWidth =
            width / 2f

        val halfHeight =
            height / 2f

        val selectionRect =
            RectF(
                -halfWidth - SELECTION_PADDING,
                -halfHeight - SELECTION_PADDING,
                halfWidth + SELECTION_PADDING,
                halfHeight + SELECTION_PADDING
            )

        canvas.drawRect(
            selectionRect,
            selectionPaint
        )
    }

    // =========================================================================
    // RECTANGLE
    // =========================================================================

    private fun drawRectangle(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val rect =
            RectF(
                -width / 2f,
                -height / 2f,
                width / 2f,
                height / 2f
            )

        canvas.drawRect(
            rect,
            shapePaint
        )
    }

    // =========================================================================
    // CIRCLE / ELLIPSE
    // =========================================================================

    private fun drawCircle(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val rect =
            RectF(
                -width / 2f,
                -height / 2f,
                width / 2f,
                height / 2f
            )

        canvas.drawOval(
            rect,
            shapePaint
        )
    }

    // =========================================================================
    // ROUNDED RECTANGLE
    // =========================================================================

    private fun drawRoundedRectangle(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val rect =
            RectF(
                -width / 2f,
                -height / 2f,
                width / 2f,
                height / 2f
            )

        val cornerRadius =
            minOf(
                width,
                height
            ) * 0.18f

        canvas.drawRoundRect(
            rect,
            cornerRadius,
            cornerRadius,
            shapePaint
        )
    }

    // =========================================================================
    // TRIANGLE
    // =========================================================================

    private fun drawTriangle(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {

        val path =
            Path()

        path.moveTo(
            0f,
            -height / 2f
        )

        path.lineTo(
            width / 2f,
            height / 2f
        )

        path.lineTo(
            -width / 2f,
            height / 2f
        )

        path.close()

        canvas.drawPath(
            path,
            shapePaint
        )
    }

    // =========================================================================
    // LINE
    // =========================================================================

    private fun drawLine(
        canvas: Canvas,
        width: Float
    ) {

        val startX =
            -width / 2f

        val endX =
            width / 2f

        canvas.drawLine(
            startX,
            0f,
            endX,
            0f,
            shapePaint
        )
    }

    // =========================================================================
    // ARROW
    // =========================================================================

    private fun drawArrow(
        canvas: Canvas,
        width: Float
    ) {

        val startX =
            -width / 2f

        val endX =
            width / 2f

        val headLength =
            (
                    width *
                            ARROW_HEAD_LENGTH_RATIO
                    )
                .coerceIn(
                    MIN_ARROW_HEAD_LENGTH,
                    MAX_ARROW_HEAD_LENGTH
                )

        val headWidth =
            (
                    headLength *
                            ARROW_HEAD_WIDTH_RATIO
                    )
                .coerceAtLeast(
                    strokeWidth * 2f
                )

        val lineEndX =
            endX -
                    headLength *
                    0.35f

        /*
         * Main arrow shaft.
         */
        canvas.drawLine(
            startX,
            0f,
            lineEndX,
            0f,
            shapePaint
        )

        /*
         * Arrow head.
         */
        val arrowPath =
            Path()

        arrowPath.moveTo(
            endX,
            0f
        )

        arrowPath.lineTo(
            endX - headLength,
            -headWidth / 2f
        )

        arrowPath.lineTo(
            endX - headLength * 0.65f,
            0f
        )

        arrowPath.lineTo(
            endX - headLength,
            headWidth / 2f
        )

        arrowPath.close()

        canvas.drawPath(
            arrowPath,
            shapePaint
        )
    }

    // =========================================================================
    // POINTER
    // =========================================================================

    private fun drawPointer(
        canvas: Canvas,
        width: Float
    ) {

        val startX =
            -width / 2f

        val endX =
            width / 2f

        val headLength =
            (
                    width *
                            ARROW_HEAD_LENGTH_RATIO
                    )
                .coerceIn(
                    MIN_ARROW_HEAD_LENGTH,
                    MAX_ARROW_HEAD_LENGTH
                )

        val headWidth =
            (
                    headLength *
                            POINTER_HEAD_WIDTH_RATIO
                    )
                .coerceAtLeast(
                    strokeWidth * 2f
                )

        val lineEndX =
            endX -
                    headLength *
                    0.45f

        /*
         * Main pointer shaft.
         */
        canvas.drawLine(
            startX,
            0f,
            lineEndX,
            0f,
            shapePaint
        )

        /*
         * Pointer head.
         */
        val pointerPath =
            Path()

        pointerPath.moveTo(
            endX,
            0f
        )

        pointerPath.lineTo(
            endX - headLength,
            -headWidth / 2f
        )

        pointerPath.lineTo(
            endX - headLength * 0.62f,
            0f
        )

        pointerPath.lineTo(
            endX - headLength,
            headWidth / 2f
        )

        pointerPath.close()

        canvas.drawPath(
            pointerPath,
            shapePaint
        )
    }

    // =========================================================================
    // BOUNDS
    // =========================================================================

    override fun getBounds(): RectF {

        val halfWidth =
            (
                    width.coerceAtLeast(
                        MIN_WIDTH
                    ) *
                            scale.coerceAtLeast(
                                MIN_SCALE
                            )
                    ) / 2f

        val halfHeight =
            (
                    height.coerceAtLeast(
                        MIN_HEIGHT
                    ) *
                            scale.coerceAtLeast(
                                MIN_SCALE
                            )
                    ) / 2f

        val corners =
            arrayOf(
                PointF(
                    -halfWidth,
                    -halfHeight
                ),
                PointF(
                    halfWidth,
                    -halfHeight
                ),
                PointF(
                    halfWidth,
                    halfHeight
                ),
                PointF(
                    -halfWidth,
                    halfHeight
                )
            )

        val radians =
            Math.toRadians(
                rotation.toDouble()
            )

        val cosValue =
            cos(
                radians
            ).toFloat()

        val sinValue =
            sin(
                radians
            ).toFloat()

        var minX =
            Float.MAX_VALUE

        var minY =
            Float.MAX_VALUE

        var maxX =
            -Float.MAX_VALUE

        var maxY =
            -Float.MAX_VALUE

        corners.forEach { point ->

            val rotatedX =
                point.x * cosValue -
                        point.y * sinValue

            val rotatedY =
                point.x * sinValue +
                        point.y * cosValue

            minX =
                minOf(
                    minX,
                    rotatedX
                )

            minY =
                minOf(
                    minY,
                    rotatedY
                )

            maxX =
                maxOf(
                    maxX,
                    rotatedX
                )

            maxY =
                maxOf(
                    maxY,
                    rotatedY
                )
        }

        return RectF(
            position.x + minX,
            position.y + minY,
            position.x + maxX,
            position.y + maxY
        )
    }

    // =========================================================================
    // HIT TEST
    // =========================================================================

    override fun contains(
        x: Float,
        y: Float
    ): Boolean {

        val dx =
            x - position.x

        val dy =
            y - position.y

        /*
         * Reverse element rotation.
         */
        val radians =
            Math.toRadians(
                (-rotation).toDouble()
            )

        val cosValue =
            cos(
                radians
            ).toFloat()

        val sinValue =
            sin(
                radians
            ).toFloat()

        val localX =
            dx * cosValue -
                    dy * sinValue

        val localY =
            dx * sinValue +
                    dy * cosValue

        /*
         * Remove element scale.
         */
        val safeScale =
            scale.coerceAtLeast(
                MIN_SCALE
            )

        val unscaledX =
            localX / safeScale

        val unscaledY =
            localY / safeScale

        val halfWidth =
            width.coerceAtLeast(
                MIN_WIDTH
            ) / 2f

        val halfHeight =
            height.coerceAtLeast(
                MIN_HEIGHT
            ) / 2f

        return (
                unscaledX >= -halfWidth &&
                        unscaledX <= halfWidth &&
                        unscaledY >= -halfHeight &&
                        unscaledY <= halfHeight
                )
    }

    // =========================================================================
    // MOVE
    // =========================================================================

    override fun moveBy(
        dx: Float,
        dy: Float
    ) {

        position.x += dx
        position.y += dy
    }

    // =========================================================================
    // SIZE
    // =========================================================================

    fun updateSize(
        newWidth: Float,
        newHeight: Float
    ) {

        width =
            newWidth.coerceAtLeast(
                MIN_WIDTH
            )

        height =
            newHeight.coerceAtLeast(
                MIN_HEIGHT
            )
    }

    // =========================================================================
    // ROTATION
    // =========================================================================

    fun updateRotation(
        newRotation: Float
    ) {

        rotation =
            normalizeRotation(
                newRotation
            )
    }

    // =========================================================================
    // SCALE
    // =========================================================================

    fun updateScale(
        newScale: Float
    ) {

        scale =
            newScale.coerceIn(
                MIN_SCALE,
                MAX_SCALE
            )
    }

    // =========================================================================
    // COLOR
    // =========================================================================

    fun updateColor(
        newColor: Int
    ) {

        color =
            newColor
    }

    // =========================================================================
    // STROKE WIDTH
    // =========================================================================

    fun updateStrokeWidth(
        newStrokeWidth: Float
    ) {

        strokeWidth =
            newStrokeWidth.coerceAtLeast(
                1f
            )
    }

    // =========================================================================
    // FILL
    // =========================================================================

    fun updateFill(
        filled: Boolean
    ) {

        isFilled =
            filled
    }

    // =========================================================================
    // ROTATION NORMALIZATION
    // =========================================================================

    private fun normalizeRotation(
        value: Float
    ): Float {

        var result =
            value % 360f

        if (result < 0f) {
            result += 360f
        }

        return result
    }
}
package com.allay.photoeditor.model

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

class TextElement(
    var text: String,
    var position: PointF,
    var textSize: Float = 80f,
    var color: Int = Color.WHITE,
    var rotation: Float = 0f,
    var scale: Float = 1f,
    var font: TextFont = TextFont.DEFAULT,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var alignment: TextAlignment = TextAlignment.CENTER
) : EditorElement() {

    data class TextColorRange(
        val start: Int,
        val end: Int,
        val color: Int
    )

    private val colorRanges = mutableListOf<TextColorRange>()

    enum class TextFont {
        DEFAULT,
        SANS_SERIF,
        SERIF,
        MONOSPACE,
        SANS_LIGHT,
        SANS_CONDENSED
    }


    // =========================================================================
    // TEXT ALIGNMENT
    // =========================================================================

    enum class TextAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    // =========================================================================
    // FONT DISPLAY NAME
    // =========================================================================

    fun getFontDisplayName(): String {
        return when (font) {
            TextFont.DEFAULT -> "Default"
            TextFont.SANS_SERIF -> "Sans"
            TextFont.SERIF -> "Serif"
            TextFont.MONOSPACE -> "Monospace"
            TextFont.SANS_LIGHT -> "Sans Light"
            TextFont.SANS_CONDENSED -> "Sans Condensed"
        }
    }

    // =========================================================================
    // TEXT BACKGROUND
    // =========================================================================

    var backgroundEnabled: Boolean = false
    var backgroundColor: Int = Color.BLACK
    var backgroundPadding: Float = 24f
    var backgroundCornerRadius: Float = 12f

    // =========================================================================
    // TEXT PAINT
    // =========================================================================

    private val paint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.FILL
    }

    // =========================================================================
    // BACKGROUND PAINT
    // =========================================================================

    private val backgroundPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.FILL
    }

    // =========================================================================
    // SELECTION PAINT
    // =========================================================================

    private val selectionPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }

    private val selectionBackgroundPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.FILL
        color = Color.argb(
            40,
            255,
            255,
            255
        )
    }

    // =========================================================================
    // HANDLE PAINT
    // =========================================================================

    private val handlePaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val handleStrokePaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    ).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.BLACK
    }

    // =========================================================================
    // UPDATE PAINT
    // =========================================================================

    /**
     * Updates Paint according to the current
     * text styling.
     *
     * Supports:
     * - Normal
     * - Bold
     * - Italic
     * - Bold + Italic
     * - Left alignment
     * - Center alignment
     * - Right alignment
     */
    private fun updatePaint() {

        paint.color = color

        paint.textSize = textSize

        // ---------------------------------------------------------------------
        // ALIGNMENT
        // ---------------------------------------------------------------------

        paint.textAlign = when (alignment) {

            TextAlignment.LEFT ->
                Paint.Align.LEFT

            TextAlignment.CENTER ->
                Paint.Align.CENTER

            TextAlignment.RIGHT ->
                Paint.Align.RIGHT
        }

        // ---------------------------------------------------------------------
        // TYPEFACE
        // ---------------------------------------------------------------------

        val style = when {

            bold && italic ->
                Typeface.BOLD_ITALIC

            bold ->
                Typeface.BOLD

            italic ->
                Typeface.ITALIC

            else ->
                Typeface.NORMAL
        }

        val baseTypeface = when (font) {
            TextFont.DEFAULT ->
                Typeface.DEFAULT

            TextFont.SANS_SERIF ->
                Typeface.SANS_SERIF

            TextFont.SERIF ->
                Typeface.SERIF

            TextFont.MONOSPACE ->
                Typeface.MONOSPACE

            TextFont.SANS_LIGHT ->
                Typeface.create(
                    "sans-serif",
                    Typeface.NORMAL
                )

            TextFont.SANS_CONDENSED ->
                Typeface.create(
                    "sans-serif-condensed",
                    Typeface.NORMAL
                )
        }

        paint.typeface = Typeface.create(
            baseTypeface,
            style
        )
    }

    // =========================================================================
    // UPDATE TEXT
    // =========================================================================

    fun updateText(
        newText: String
    ) {
        text = newText

        // Keep existing ranges valid after editing the text.
        colorRanges.removeAll {
            it.start >= text.length || it.end <= it.start
        }

        val normalized = colorRanges.map { range ->
            range.copy(
                start = range.start.coerceIn(0, text.length),
                end = range.end.coerceIn(0, text.length)
            )
        }.filter { it.end > it.start }

        colorRanges.clear()
        colorRanges.addAll(normalized)
    }

    // =========================================================================
    // UPDATE COLOR
    // =========================================================================

    fun updateColor(
        newColor: Int
    ) {
        color = newColor
        colorRanges.clear()
    }

    fun updateColorRange(
        start: Int,
        end: Int,
        newColor: Int
    ) {
        val safeStart = start.coerceIn(0, text.length)
        val safeEnd = end.coerceIn(0, text.length)

        if (safeStart >= safeEnd) return

        val updated = mutableListOf<TextColorRange>()

        for (range in colorRanges) {
            if (range.end <= safeStart || range.start >= safeEnd) {
                updated.add(range)
                continue
            }

            if (range.start < safeStart) {
                updated.add(
                    TextColorRange(
                        range.start,
                        safeStart,
                        range.color
                    )
                )
            }

            if (range.end > safeEnd) {
                updated.add(
                    TextColorRange(
                        safeEnd,
                        range.end,
                        range.color
                    )
                )
            }
        }

        updated.add(
            TextColorRange(
                safeStart,
                safeEnd,
                newColor
            )
        )

        colorRanges.clear()
        colorRanges.addAll(mergeColorRanges(updated))
    }

    fun getColorRanges(): List<TextColorRange> =
        colorRanges.toList()

    fun getColorAt(index: Int): Int {
        if (index !in text.indices) return color

        return colorRanges
            .lastOrNull {
                index >= it.start && index < it.end
            }
            ?.color
            ?: color
    }

    fun setColorRanges(
        ranges: List<TextColorRange>
    ) {
        colorRanges.clear()
        colorRanges.addAll(
            mergeColorRanges(
                ranges.map { range ->
                    TextColorRange(
                        range.start.coerceIn(0, text.length),
                        range.end.coerceIn(0, text.length),
                        range.color
                    )
                }.filter { it.end > it.start }
            )
        )
    }

    private fun mergeColorRanges(
        ranges: List<TextColorRange>
    ): List<TextColorRange> {
        if (ranges.isEmpty()) return emptyList()

        val sorted = ranges.sortedWith(
            compareBy<TextColorRange> { it.start }
                .thenBy { it.end }
        )

        val result = mutableListOf<TextColorRange>()

        for (range in sorted) {
            val last = result.lastOrNull()

            if (last != null &&
                last.end == range.start &&
                last.color == range.color
            ) {
                result[result.lastIndex] =
                    last.copy(end = range.end)
            } else {
                result.add(range)
            }
        }

        return result
    }

    // =========================================================================
    // UPDATE SIZE
    // =========================================================================

    fun updateTextSize(
        newSize: Float
    ) {
        textSize = newSize.coerceIn(
            20f,
            200f
        )
    }

    // =========================================================================
    // UPDATE BOLD
    // =========================================================================

    fun updateBold(
        enabled: Boolean
    ) {
        bold = enabled
    }

    // =========================================================================
    // UPDATE ITALIC
    // =========================================================================

    fun updateItalic(
        enabled: Boolean
    ) {
        italic = enabled
    }

    // =========================================================================
    // UPDATE ALIGNMENT
    // =========================================================================

    fun updateAlignment(
        newAlignment: TextAlignment
    ) {
        alignment = newAlignment
    }

    fun updateFont(
        newFont: TextFont
    ) {
        font = newFont
    }

    // =========================================================================
    // TEXT BACKGROUND
    // =========================================================================

    /**
     * Toggles the text background on/off.
     */
    fun toggleBackground() {
        backgroundEnabled = !backgroundEnabled
    }

    /**
     * Enables or disables the text background.
     */
    fun updateBackground(
        enabled: Boolean
    ) {
        backgroundEnabled = enabled
    }

    /**
     * Updates the background color.
     */
    fun updateBackgroundColor(
        newColor: Int
    ) {
        backgroundColor = newColor
    }

    /**
     * Updates the background padding.
     *
     * Padding is applied on all four sides.
     */
    fun updateBackgroundPadding(
        newPadding: Float
    ) {
        backgroundPadding =
            newPadding.coerceAtLeast(0f)
    }

    /**
     * Updates the background corner radius.
     */
    fun updateBackgroundCornerRadius(
        newRadius: Float
    ) {
        backgroundCornerRadius =
            newRadius.coerceAtLeast(0f)
    }

    // =========================================================================
    // RAW TEXT BOUNDS
    // =========================================================================

    /**
     * Returns the actual text bounds in local coordinates.
     *
     * position represents the text baseline.
     *
     * Unlike getTextBoundsInternal(), these bounds do not
     * contain the extra selection padding.
     */
    private fun getRawTextBounds(): RectF {

        updatePaint()

        val textBounds = Rect()

        paint.getTextBounds(
            text,
            0,
            text.length,
            textBounds
        )

        val textWidth =
            paint.measureText(text)

        val left: Float
        val right: Float

        when (alignment) {

            TextAlignment.LEFT -> {

                left = 0f
                right = textWidth
            }

            TextAlignment.CENTER -> {

                left = -(textWidth / 2f)
                right = textWidth / 2f
            }

            TextAlignment.RIGHT -> {

                left = -textWidth
                right = 0f
            }
        }

        return RectF(
            left,
            textBounds.top.toFloat(),
            right,
            textBounds.bottom.toFloat()
        )
    }

    // =========================================================================
    // TEXT BOUNDS
    // =========================================================================

    /**
     * Returns text bounds in local coordinates.
     *
     * Includes the existing selection padding.
     *
     * When background is enabled, the bounds also include
     * the background padding.
     */
    private fun getTextBoundsInternal(): RectF {

        val textBounds =
            getRawTextBounds()

        val selectionPadding = 20f

        var left =
            textBounds.left - selectionPadding

        var top =
            textBounds.top - selectionPadding

        var right =
            textBounds.right + selectionPadding

        var bottom =
            textBounds.bottom + selectionPadding

        // ---------------------------------------------------------------------
        // INCLUDE BACKGROUND
        // ---------------------------------------------------------------------

        if (backgroundEnabled) {

            left -= backgroundPadding
            top -= backgroundPadding
            right += backgroundPadding
            bottom += backgroundPadding
        }

        return RectF(
            left,
            top,
            right,
            bottom
        )
    }

    // =========================================================================
    // GET BOUNDS
    // =========================================================================

    override fun getBounds(): RectF {

        val textBounds =
            getTextBoundsInternal()

        /*
         * Apply element scale.
         */

        val scaledLeft =
            textBounds.left * scale

        val scaledTop =
            textBounds.top * scale

        val scaledRight =
            textBounds.right * scale

        val scaledBottom =
            textBounds.bottom * scale

        /*
         * Four corners before rotation.
         */

        val corners = arrayOf(

            PointF(
                scaledLeft,
                scaledTop
            ),

            PointF(
                scaledRight,
                scaledTop
            ),

            PointF(
                scaledRight,
                scaledBottom
            ),

            PointF(
                scaledLeft,
                scaledBottom
            )
        )

        val radians =
            Math.toRadians(
                rotation.toDouble()
            )

        val cosValue =
            cos(radians).toFloat()

        val sinValue =
            sin(radians).toFloat()

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

        /*
         * Convert world position into
         * element-local coordinates.
         */

        val dx =
            x - position.x

        val dy =
            y - position.y

        /*
         * Reverse the element rotation.
         */

        val radians =
            Math.toRadians(
                (-rotation).toDouble()
            )

        val cosValue =
            cos(radians).toFloat()

        val sinValue =
            sin(radians).toFloat()

        val localX =
            dx * cosValue -
                    dy * sinValue

        val localY =
            dx * sinValue +
                    dy * cosValue

        /*
         * Remove element scale.
         */

        val inverseScale =
            if (scale == 0f) {
                1f
            } else {
                1f / scale
            }

        val unscaledX =
            localX * inverseScale

        val unscaledY =
            localY * inverseScale

        val bounds =
            getTextBoundsInternal()

        return bounds.contains(
            unscaledX,
            unscaledY
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
    // TRANSFORM LOCAL POINT
    // =========================================================================

    /**
     * Transforms a local point using
     * the current element scale and rotation.
     */
    private fun transformLocalPoint(
        point: PointF
    ): PointF {

        /*
         * Apply scale.
         */

        val scaledX =
            point.x * scale

        val scaledY =
            point.y * scale

        /*
         * Apply rotation.
         */

        val radians =
            Math.toRadians(
                rotation.toDouble()
            )

        val cosValue =
            cos(radians).toFloat()

        val sinValue =
            sin(radians).toFloat()

        val rotatedX =
            scaledX * cosValue -
                    scaledY * sinValue

        val rotatedY =
            scaledX * sinValue +
                    scaledY * cosValue

        /*
         * Convert back into image coordinates.
         */

        return PointF(
            position.x + rotatedX,
            position.y + rotatedY
        )
    }

    // =========================================================================
    // RESIZE HANDLE POSITION
    // =========================================================================

    /**
     * Returns the bottom-right resize handle
     * position in IMAGE coordinates.
     */
    fun getResizeHandlePosition(): PointF {

        val bounds =
            getTextBoundsInternal()

        return transformLocalPoint(
            PointF(
                bounds.right,
                bounds.bottom
            )
        )
    }

    // =========================================================================
    // ROTATION HANDLE POSITION
    // =========================================================================

    /**
     * Returns the top-center rotation handle
     * position in IMAGE coordinates.
     */
    fun getRotationHandlePosition(): PointF {

        val bounds =
            getTextBoundsInternal()

        /*
         * Distance between selection box
         * and rotation handle.
         */

        val handleOffset =
            60f

        return transformLocalPoint(
            PointF(
                (bounds.left + bounds.right) / 2f,
                bounds.top - handleOffset
            )
        )
    }

    // =========================================================================
    // DRAW
    // =========================================================================

    override fun draw(
        canvas: Canvas,
        matrix: Matrix
    ) {

        updatePaint()

        /*
         * Convert element position
         * from image -> screen.
         */

        val screenPoint =
            floatArrayOf(
                position.x,
                position.y
            )

        matrix.mapPoints(
            screenPoint
        )

        /*
         * Get matrix scale.
         */

        val matrixValues =
            FloatArray(9)

        matrix.getValues(
            matrixValues
        )

        val matrixScale =
            matrixValues[
                Matrix.MSCALE_X
            ]

        canvas.save()

        // ---------------------------------------------------------------------
        // POSITION
        // ---------------------------------------------------------------------

        canvas.translate(
            screenPoint[0],
            screenPoint[1]
        )

        // ---------------------------------------------------------------------
        // ROTATION
        // ---------------------------------------------------------------------

        canvas.rotate(
            rotation
        )

        // ---------------------------------------------------------------------
        // SCALE
        // ---------------------------------------------------------------------

        canvas.scale(
            matrixScale * scale,
            matrixScale * scale
        )

        // ---------------------------------------------------------------------
        // BACKGROUND
        // ---------------------------------------------------------------------

        if (backgroundEnabled) {

            val textBounds =
                getRawTextBounds()

            backgroundPaint.color =
                backgroundColor

            val backgroundRect =
                RectF(
                    textBounds.left -
                            backgroundPadding,

                    textBounds.top -
                            backgroundPadding,

                    textBounds.right +
                            backgroundPadding,

                    textBounds.bottom +
                            backgroundPadding
                )

            canvas.drawRoundRect(
                backgroundRect,
                backgroundCornerRadius,
                backgroundCornerRadius,
                backgroundPaint
            )
        }

        // ---------------------------------------------------------------------
        // TEXT
        // ---------------------------------------------------------------------

        drawTextWithColors(canvas)

        canvas.restore()

        // ---------------------------------------------------------------------
        // SELECTION
        // ---------------------------------------------------------------------

        if (isSelected) {

            drawSelection(
                canvas,
                matrix,
                matrixScale
            )
        }
    }

    private fun drawTextWithColors(
        canvas: Canvas
    ) {
        if (text.isEmpty()) return

        // The current editor uses a single baseline for TextElement.
        // Draw each character with the color assigned to its range.
        val originalAlign = paint.textAlign
        val fullWidth = paint.measureText(text)

        val startX = when (alignment) {
            TextAlignment.LEFT -> 0f
            TextAlignment.CENTER -> -fullWidth / 2f
            TextAlignment.RIGHT -> -fullWidth
        }

        paint.textAlign = Paint.Align.LEFT

        var x = startX

        text.forEachIndexed { index, character ->
            val rangeColor = colorRanges
                .lastOrNull {
                    index >= it.start && index < it.end
                }
                ?.color

            paint.color = rangeColor ?: color

            val charText = character.toString()
            canvas.drawText(
                charText,
                x,
                0f,
                paint
            )

            x += paint.measureText(charText)
        }

        paint.textAlign = originalAlign
        paint.color = color
    }

    // =========================================================================
    // DRAW SELECTION
    // =========================================================================

    private fun drawSelection(
        canvas: Canvas,
        matrix: Matrix,
        matrixScale: Float
    ) {

        val bounds =
            getTextBoundsInternal()

        canvas.save()

        /*
         * Convert position to screen.
         */

        val screenPoint =
            floatArrayOf(
                position.x,
                position.y
            )

        matrix.mapPoints(
            screenPoint
        )

        canvas.translate(
            screenPoint[0],
            screenPoint[1]
        )

        /*
         * Apply same rotation as text.
         */

        canvas.rotate(
            rotation
        )

        /*
         * Apply same scale as text.
         */

        canvas.scale(
            matrixScale * scale,
            matrixScale * scale
        )

        // ---------------------------------------------------------------------
        // SELECTION BACKGROUND
        // ---------------------------------------------------------------------

        canvas.drawRect(
            bounds,
            selectionBackgroundPaint
        )

        // ---------------------------------------------------------------------
        // SELECTION BORDER
        // ---------------------------------------------------------------------

        canvas.drawRect(
            bounds,
            selectionPaint
        )

        /*
         * Compensate handle size for canvas scale.
         *
         * This keeps the handles approximately
         * the same size on screen regardless
         * of zoom or element scale.
         */

        val totalScale =
            (matrixScale * scale)
                .coerceAtLeast(
                    0.01f
                )

        val handleRadius =
            12f / totalScale

        // ---------------------------------------------------------------------
        // ROTATION HANDLE
        // ---------------------------------------------------------------------

        val rotationHandleOffset =
            60f

        val rotationHandleX =
            (bounds.left + bounds.right) / 2f

        val rotationHandleY =
            bounds.top -
                    rotationHandleOffset

        /*
         * Connector line.
         */

        canvas.drawLine(
            rotationHandleX,
            bounds.top,
            rotationHandleX,
            rotationHandleY,
            selectionPaint
        )

        /*
         * Handle fill.
         */

        canvas.drawCircle(
            rotationHandleX,
            rotationHandleY,
            handleRadius,
            handlePaint
        )

        /*
         * Handle border.
         */

        canvas.drawCircle(
            rotationHandleX,
            rotationHandleY,
            handleRadius,
            handleStrokePaint
        )

        // ---------------------------------------------------------------------
        // RESIZE HANDLE
        // ---------------------------------------------------------------------

        /*
         * Handle fill.
         */

        canvas.drawCircle(
            bounds.right,
            bounds.bottom,
            handleRadius,
            handlePaint
        )

        /*
         * Handle border.
         */

        canvas.drawCircle(
            bounds.right,
            bounds.bottom,
            handleRadius,
            handleStrokePaint
        )

        canvas.restore()
    }
    // =========================================================================
    // LAYER DUPLICATION - PHASE 10.4
    // =========================================================================

    /** Creates an independent copy for layer duplication. */
    override fun duplicate(): TextElement {
        return copyForCropSession().also { copy ->
            copy.isSelected = false
            copy.isVisible = true
        }
    }

    // =========================================================================
    // CROP SESSION COPY
    // =========================================================================

    /** Creates an independent snapshot copy for Cancel Crop. */
    fun copyForCropSession(): TextElement {
        return TextElement(
            text = text,
            position = PointF(position.x, position.y),
            textSize = textSize,
            color = color,
            rotation = rotation,
            scale = scale,
            font = font,
            bold = bold,
            italic = italic,
            alignment = alignment
        ).also { copy ->
            copy.isSelected = isSelected
            copy.isVisible = isVisible
            copy.backgroundEnabled = backgroundEnabled
            copy.backgroundColor = backgroundColor
            copy.backgroundPadding = backgroundPadding
            copy.backgroundCornerRadius = backgroundCornerRadius
            copy.setColorRanges(getColorRanges())
        }
    }

}


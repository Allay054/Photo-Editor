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

    // =========================================================================
    // TEXT FONT
    // =========================================================================

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
    // MULTI COLOR
    // =========================================================================

    /**
     * Represents a color applied to a character range.
     *
     * start = inclusive
     * end   = exclusive
     *
     * Example:
     *
     * Text:  Hello World
     *
     * Range:
     * start = 0
     * end   = 5
     *
     * applies the color to "Hello".
     */
    data class TextColorRange(
        val start: Int,
        val end: Int,
        val color: Int
    )

    /**
     * Stores custom colors for portions of the text.
     *
     * Any character that does not belong to a range
     * automatically uses the main [color].
     */
    private val colorRanges =
        mutableListOf<TextColorRange>()

    /**
     * Returns all currently configured color ranges.
     *
     * A copy is returned so callers cannot directly modify
     * the internal list.
     */
    fun getColorRanges(): List<TextColorRange> {
        return colorRanges.toList()
    }

    /**
     * Returns the color for the character at [index].
     *
     * If the character does not have a custom color,
     * the TextElement's default [color] is returned.
     */
    fun getColorAt(index: Int): Int {

        if (index !in text.indices) {
            return color
        }

        return colorRanges
            .lastOrNull {
                index >= it.start &&
                        index < it.end
            }
            ?.color
            ?: color
    }

    /**
     * Applies a color to a character range.
     *
     * Existing overlapping ranges are split/replaced.
     *
     * Example:
     *
     * Existing:
     *
     * 0..10 = RED
     *
     * Applying:
     *
     * 3..7 = BLUE
     *
     * Result:
     *
     * 0..3 = RED
     * 3..7 = BLUE
     * 7..10 = RED
     */
    fun updateColorRange(
        start: Int,
        end: Int,
        newColor: Int
    ) {

        val safeStart =
            start.coerceIn(0, text.length)

        val safeEnd =
            end.coerceIn(0, text.length)

        if (safeStart >= safeEnd) {
            return
        }

        val updated =
            mutableListOf<TextColorRange>()

        for (range in colorRanges) {

            // No overlap.
            if (
                range.end <= safeStart ||
                range.start >= safeEnd
            ) {
                updated.add(range)
                continue
            }

            // Preserve the part before the new range.
            if (range.start < safeStart) {
                updated.add(
                    TextColorRange(
                        start = range.start,
                        end = safeStart,
                        color = range.color
                    )
                )
            }

            // Preserve the part after the new range.
            if (range.end > safeEnd) {
                updated.add(
                    TextColorRange(
                        start = safeEnd,
                        end = range.end,
                        color = range.color
                    )
                )
            }
        }

        // Add the newly selected range.
        updated.add(
            TextColorRange(
                start = safeStart,
                end = safeEnd,
                color = newColor
            )
        )

        colorRanges.clear()

        colorRanges.addAll(
            mergeColorRanges(updated)
        )
    }

    /**
     * Restores a complete set of color ranges.
     *
     * Used later by undo/cancel functionality.
     */
    fun setColorRanges(
        ranges: List<TextColorRange>
    ) {

        colorRanges.clear()

        val validRanges =
            ranges.mapNotNull { range ->

                val safeStart =
                    range.start.coerceIn(
                        0,
                        text.length
                    )

                val safeEnd =
                    range.end.coerceIn(
                        0,
                        text.length
                    )

                if (safeStart >= safeEnd) {
                    null
                } else {
                    TextColorRange(
                        start = safeStart,
                        end = safeEnd,
                        color = range.color
                    )
                }
            }

        colorRanges.addAll(
            mergeColorRanges(validRanges)
        )
    }

    /**
     * Merges adjacent ranges that have the same color.
     */
    private fun mergeColorRanges(
        ranges: List<TextColorRange>
    ): List<TextColorRange> {

        if (ranges.isEmpty()) {
            return emptyList()
        }

        val sorted =
            ranges.sortedWith(
                compareBy<TextColorRange> {
                    it.start
                }.thenBy {
                    it.end
                }
            )

        val result =
            mutableListOf<TextColorRange>()

        for (range in sorted) {

            val last =
                result.lastOrNull()

            if (
                last != null &&
                last.end == range.start &&
                last.color == range.color
            ) {

                result[result.lastIndex] =
                    last.copy(
                        end = range.end
                    )

            } else {

                result.add(range)
            }
        }

        return result
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

    private val paint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            style =
                Paint.Style.FILL
        }

    // =========================================================================
    // BACKGROUND PAINT
    // =========================================================================

    private val backgroundPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            style =
                Paint.Style.FILL
        }

    // =========================================================================
    // SELECTION PAINT
    // =========================================================================

    private val selectionPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            style =
                Paint.Style.STROKE

            strokeWidth =
                3f

            color =
                Color.WHITE
        }

    private val selectionBackgroundPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            style =
                Paint.Style.FILL

            color =
                Color.argb(
                    40,
                    255,
                    255,
                    255
                )
        }

    // =========================================================================
    // HANDLE PAINT
    // =========================================================================

    private val handlePaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            style =
                Paint.Style.FILL

            color =
                Color.WHITE
        }

    private val handleStrokePaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {

            style =
                Paint.Style.STROKE

            strokeWidth =
                3f

            color =
                Color.BLACK
        }

    // =========================================================================
    // UPDATE PAINT
    // =========================================================================

    /**
     * Updates Paint according to the current
     * text styling.
     *
     * Supports:
     *
     * - Normal
     * - Bold
     * - Italic
     * - Bold + Italic
     * - Left alignment
     * - Center alignment
     * - Right alignment
     */
    private fun updatePaint() {

        paint.color =
            color

        paint.textSize =
            textSize

        // ---------------------------------------------------------------------
        // ALIGNMENT
        // ---------------------------------------------------------------------

        paint.textAlign =
            when (alignment) {

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

        val style =
            when {

                bold && italic ->
                    Typeface.BOLD_ITALIC

                bold ->
                    Typeface.BOLD

                italic ->
                    Typeface.ITALIC

                else ->
                    Typeface.NORMAL
            }

        val baseTypeface =
            when (font) {

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

        paint.typeface =
            Typeface.create(
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

        /*
         * Remove ranges that are completely outside
         * the new text.
         */
        colorRanges.removeAll {

            it.start >= text.length ||
                    it.end <= it.start
        }

        /*
         * Clamp remaining ranges to the new
         * text length.
         */
        val normalized =
            colorRanges
                .map {

                    it.copy(
                        start = it.start.coerceIn(
                            0,
                            text.length
                        ),
                        end = it.end.coerceIn(
                            0,
                            text.length
                        )
                    )
                }
                .filter {
                    it.end > it.start
                }

        colorRanges.clear()

        colorRanges.addAll(
            mergeColorRanges(
                normalized
            )
        )
    }

    // =========================================================================
    // UPDATE COLOR
    // =========================================================================

    fun updateColor(
        newColor: Int
    ) {

        color =
            newColor

        /*
         * Changing the main color resets
         * custom ranges.
         *
         * The entire text will therefore
         * use the new default color.
         */
        colorRanges.clear()
    }

    // =========================================================================
    // UPDATE SIZE
    // =========================================================================

    fun updateTextSize(
        newSize: Float
    ) {

        textSize =
            newSize.coerceIn(
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

        bold =
            enabled
    }

    // =========================================================================
    // UPDATE ITALIC
    // =========================================================================

    fun updateItalic(
        enabled: Boolean
    ) {

        italic =
            enabled
    }

    // =========================================================================
    // UPDATE ALIGNMENT
    // =========================================================================

    fun updateAlignment(
        newAlignment: TextAlignment
    ) {

        alignment =
            newAlignment
    }

    // =========================================================================
    // UPDATE FONT
    // =========================================================================

    fun updateFont(
        newFont: TextFont
    ) {

        font =
            newFont
    }

    // =========================================================================
    // TEXT BACKGROUND
    // =========================================================================

    /**
     * Toggles the text background on/off.
     */
    fun toggleBackground() {

        backgroundEnabled =
            !backgroundEnabled
    }

    /**
     * Enables or disables the text background.
     */
    fun updateBackground(
        enabled: Boolean
    ) {

        backgroundEnabled =
            enabled
    }

    /**
     * Updates the background color.
     */
    fun updateBackgroundColor(
        newColor: Int
    ) {

        backgroundColor =
            newColor
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
            newPadding.coerceAtLeast(
                0f
            )
    }

    /**
     * Updates the background corner radius.
     */
    fun updateBackgroundCornerRadius(
        newRadius: Float
    ) {

        backgroundCornerRadius =
            newRadius.coerceAtLeast(
                0f
            )
    }

    // =========================================================================
    // RAW TEXT BOUNDS
    // =========================================================================

    /**
     * Returns the actual text bounds
     * in local coordinates.
     *
     * position represents the text baseline.
     *
     * These bounds do not contain
     * selection padding.
     */
    private fun getRawTextBounds(): RectF {

        updatePaint()

        val textBounds =
            Rect()

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

                left =
                    0f

                right =
                    textWidth
            }

            TextAlignment.CENTER -> {

                left =
                    -(textWidth / 2f)

                right =
                    textWidth / 2f
            }

            TextAlignment.RIGHT -> {

                left =
                    -textWidth

                right =
                    0f
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
     * Includes selection padding.
     *
     * When background is enabled,
     * background padding is also included.
     */
    private fun getTextBoundsInternal(): RectF {

        val textBounds =
            getRawTextBounds()

        val selectionPadding =
            20f

        var left =
            textBounds.left -
                    selectionPadding

        var top =
            textBounds.top -
                    selectionPadding

        var right =
            textBounds.right +
                    selectionPadding

        var bottom =
            textBounds.bottom +
                    selectionPadding

        // ---------------------------------------------------------------------
        // INCLUDE BACKGROUND
        // ---------------------------------------------------------------------

        if (backgroundEnabled) {

            left -=
                backgroundPadding

            top -=
                backgroundPadding

            right +=
                backgroundPadding

            bottom +=
                backgroundPadding
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
            textBounds.left *
                    scale

        val scaledTop =
            textBounds.top *
                    scale

        val scaledRight =
            textBounds.right *
                    scale

        val scaledBottom =
            textBounds.bottom *
                    scale

        /*
         * Four corners before rotation.
         */

        val corners =
            arrayOf(

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
            localX *
                    inverseScale

        val unscaledY =
            localY *
                    inverseScale

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

        position.x +=
            dx

        position.y +=
            dy
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
            point.x *
                    scale

        val scaledY =
            point.y *
                    scale

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
            position.x +
                    rotatedX,

            position.y +
                    rotatedY
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
                (bounds.left +
                        bounds.right) / 2f,

                bounds.top -
                        handleOffset
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

        drawTextWithColors(
            canvas
        )

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

    // =========================================================================
    // DRAW TEXT WITH COLORS
    // =========================================================================

    /**
     * Draws the text while respecting custom
     * color ranges.
     *
     * Consecutive characters having the same
     * color are grouped together into one drawText()
     * operation.
     *
     * This is more efficient than drawing every
     * individual character separately.
     */
    private fun drawTextWithColors(
        canvas: Canvas
    ) {

        if (text.isEmpty()) {
            return
        }

        /*
         * If there are no custom color ranges,
         * use the normal Android drawText path.
         */
        if (colorRanges.isEmpty()) {

            paint.color =
                color

            canvas.drawText(
                text,
                0f,
                0f,
                paint
            )

            return
        }

        /*
         * We calculate the complete text width
         * using the current Paint.
         *
         * This preserves the existing alignment.
         */
        val fullWidth =
            paint.measureText(text)

        val startX =
            when (alignment) {

                TextAlignment.LEFT ->
                    0f

                TextAlignment.CENTER ->
                    -fullWidth / 2f

                TextAlignment.RIGHT ->
                    -fullWidth
            }

        /*
         * Drawing ranges with Paint.Align.LEFT
         * gives us precise control over the X
         * position of each segment.
         */
        val originalAlign =
            paint.textAlign

        paint.textAlign =
            Paint.Align.LEFT

        var currentIndex =
            0

        var currentX =
            startX

        while (currentIndex < text.length) {

            val currentColor =
                getColorAt(
                    currentIndex
                )

            /*
             * Find the end of the consecutive
             * characters using the same color.
             */
            var endIndex =
                currentIndex + 1

            while (
                endIndex < text.length &&
                getColorAt(endIndex) == currentColor
            ) {

                endIndex++
            }

            val segment =
                text.substring(
                    currentIndex,
                    endIndex
                )

            paint.color =
                currentColor

            canvas.drawText(
                segment,
                currentX,
                0f,
                paint
            )

            currentX +=
                paint.measureText(
                    segment
                )

            currentIndex =
                endIndex
        }

        /*
         * Restore Paint state.
         */
        paint.textAlign =
            originalAlign

        paint.color =
            color
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
         * This keeps handles approximately
         * the same size on screen regardless
         * of zoom or element scale.
         */

        val totalScale =
            (matrixScale * scale)
                .coerceAtLeast(
                    0.01f
                )

        val handleRadius =
            12f /
                    totalScale

        // ---------------------------------------------------------------------
        // ROTATION HANDLE
        // ---------------------------------------------------------------------

        val rotationHandleOffset =
            60f

        val rotationHandleX =
            (bounds.left +
                    bounds.right) / 2f

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
}

//package com.allay.photoeditor.model
//
//import android.graphics.Canvas
//import android.graphics.Color
//import android.graphics.Matrix
//import android.graphics.Paint
//import android.graphics.PointF
//import android.graphics.Rect
//import android.graphics.RectF
//import android.graphics.Typeface
//import kotlin.math.cos
//import kotlin.math.sin
//
//class TextElement(
//    var text: String,
//    var position: PointF,
//    var textSize: Float = 80f,
//    var color: Int = Color.WHITE,
//    var rotation: Float = 0f,
//    var scale: Float = 1f,
//    var font: TextFont = TextFont.DEFAULT,
//    var bold: Boolean = false,
//    var italic: Boolean = false,
//    var alignment: TextAlignment = TextAlignment.CENTER
//) : EditorElement() {
//
//    enum class TextFont {
//        DEFAULT,
//        SANS_SERIF,
//        SERIF,
//        MONOSPACE,
//        SANS_LIGHT,
//        SANS_CONDENSED
//    }
//
//
//    // =========================================================================
//    // TEXT ALIGNMENT
//    // =========================================================================
//
//    enum class TextAlignment {
//        LEFT,
//        CENTER,
//        RIGHT
//    }
//
//    // =========================================================================
//    // FONT DISPLAY NAME
//    // =========================================================================
//
//    fun getFontDisplayName(): String {
//        return when (font) {
//            TextFont.DEFAULT -> "Default"
//            TextFont.SANS_SERIF -> "Sans"
//            TextFont.SERIF -> "Serif"
//            TextFont.MONOSPACE -> "Monospace"
//            TextFont.SANS_LIGHT -> "Sans Light"
//            TextFont.SANS_CONDENSED -> "Sans Condensed"
//        }
//    }
//
//    // =========================================================================
//    // TEXT BACKGROUND
//    // =========================================================================
//
//    var backgroundEnabled: Boolean = false
//    var backgroundColor: Int = Color.BLACK
//    var backgroundPadding: Float = 24f
//    var backgroundCornerRadius: Float = 12f
//
//    // =========================================================================
//    // TEXT PAINT
//    // =========================================================================
//
//    private val paint = Paint(
//        Paint.ANTI_ALIAS_FLAG
//    ).apply {
//        style = Paint.Style.FILL
//    }
//
//    // =========================================================================
//    // BACKGROUND PAINT
//    // =========================================================================
//
//    private val backgroundPaint = Paint(
//        Paint.ANTI_ALIAS_FLAG
//    ).apply {
//        style = Paint.Style.FILL
//    }
//
//    // =========================================================================
//    // SELECTION PAINT
//    // =========================================================================
//
//    private val selectionPaint = Paint(
//        Paint.ANTI_ALIAS_FLAG
//    ).apply {
//        style = Paint.Style.STROKE
//        strokeWidth = 3f
//        color = Color.WHITE
//    }
//
//    private val selectionBackgroundPaint = Paint(
//        Paint.ANTI_ALIAS_FLAG
//    ).apply {
//        style = Paint.Style.FILL
//        color = Color.argb(
//            40,
//            255,
//            255,
//            255
//        )
//    }
//
//    // =========================================================================
//    // HANDLE PAINT
//    // =========================================================================
//
//    private val handlePaint = Paint(
//        Paint.ANTI_ALIAS_FLAG
//    ).apply {
//        style = Paint.Style.FILL
//        color = Color.WHITE
//    }
//
//    private val handleStrokePaint = Paint(
//        Paint.ANTI_ALIAS_FLAG
//    ).apply {
//        style = Paint.Style.STROKE
//        strokeWidth = 3f
//        color = Color.BLACK
//    }
//
//    // =========================================================================
//    // UPDATE PAINT
//    // =========================================================================
//
//    /**
//     * Updates Paint according to the current
//     * text styling.
//     *
//     * Supports:
//     * - Normal
//     * - Bold
//     * - Italic
//     * - Bold + Italic
//     * - Left alignment
//     * - Center alignment
//     * - Right alignment
//     */
//    private fun updatePaint() {
//
//        paint.color = color
//
//        paint.textSize = textSize
//
//        // ---------------------------------------------------------------------
//        // ALIGNMENT
//        // ---------------------------------------------------------------------
//
//        paint.textAlign = when (alignment) {
//
//            TextAlignment.LEFT ->
//                Paint.Align.LEFT
//
//            TextAlignment.CENTER ->
//                Paint.Align.CENTER
//
//            TextAlignment.RIGHT ->
//                Paint.Align.RIGHT
//        }
//
//        // ---------------------------------------------------------------------
//        // TYPEFACE
//        // ---------------------------------------------------------------------
//
//        val style = when {
//
//            bold && italic ->
//                Typeface.BOLD_ITALIC
//
//            bold ->
//                Typeface.BOLD
//
//            italic ->
//                Typeface.ITALIC
//
//            else ->
//                Typeface.NORMAL
//        }
//
//        val baseTypeface = when (font) {
//            TextFont.DEFAULT ->
//                Typeface.DEFAULT
//
//            TextFont.SANS_SERIF ->
//                Typeface.SANS_SERIF
//
//            TextFont.SERIF ->
//                Typeface.SERIF
//
//            TextFont.MONOSPACE ->
//                Typeface.MONOSPACE
//
//            TextFont.SANS_LIGHT ->
//                Typeface.create(
//                    "sans-serif",
//                    Typeface.NORMAL
//                )
//
//            TextFont.SANS_CONDENSED ->
//                Typeface.create(
//                    "sans-serif-condensed",
//                    Typeface.NORMAL
//                )
//        }
//
//        paint.typeface = Typeface.create(
//            baseTypeface,
//            style
//        )
//    }
//
//    // =========================================================================
//    // UPDATE TEXT
//    // =========================================================================
//
//    fun updateText(
//        newText: String
//    ) {
//        text = newText
//    }
//
//    // =========================================================================
//    // UPDATE COLOR
//    // =========================================================================
//
//    fun updateColor(
//        newColor: Int
//    ) {
//        color = newColor
//    }
//
//    // =========================================================================
//    // UPDATE SIZE
//    // =========================================================================
//
//    fun updateTextSize(
//        newSize: Float
//    ) {
//        textSize = newSize.coerceIn(
//            20f,
//            200f
//        )
//    }
//
//    // =========================================================================
//    // UPDATE BOLD
//    // =========================================================================
//
//    fun updateBold(
//        enabled: Boolean
//    ) {
//        bold = enabled
//    }
//
//    // =========================================================================
//    // UPDATE ITALIC
//    // =========================================================================
//
//    fun updateItalic(
//        enabled: Boolean
//    ) {
//        italic = enabled
//    }
//
//    // =========================================================================
//    // UPDATE ALIGNMENT
//    // =========================================================================
//
//    fun updateAlignment(
//        newAlignment: TextAlignment
//    ) {
//        alignment = newAlignment
//    }
//
//    fun updateFont(
//        newFont: TextFont
//    ) {
//        font = newFont
//    }
//
//    // =========================================================================
//    // TEXT BACKGROUND
//    // =========================================================================
//
//    /**
//     * Toggles the text background on/off.
//     */
//    fun toggleBackground() {
//        backgroundEnabled = !backgroundEnabled
//    }
//
//    /**
//     * Enables or disables the text background.
//     */
//    fun updateBackground(
//        enabled: Boolean
//    ) {
//        backgroundEnabled = enabled
//    }
//
//    /**
//     * Updates the background color.
//     */
//    fun updateBackgroundColor(
//        newColor: Int
//    ) {
//        backgroundColor = newColor
//    }
//
//    /**
//     * Updates the background padding.
//     *
//     * Padding is applied on all four sides.
//     */
//    fun updateBackgroundPadding(
//        newPadding: Float
//    ) {
//        backgroundPadding =
//            newPadding.coerceAtLeast(0f)
//    }
//
//    /**
//     * Updates the background corner radius.
//     */
//    fun updateBackgroundCornerRadius(
//        newRadius: Float
//    ) {
//        backgroundCornerRadius =
//            newRadius.coerceAtLeast(0f)
//    }
//
//    // =========================================================================
//    // RAW TEXT BOUNDS
//    // =========================================================================
//
//    /**
//     * Returns the actual text bounds in local coordinates.
//     *
//     * position represents the text baseline.
//     *
//     * Unlike getTextBoundsInternal(), these bounds do not
//     * contain the extra selection padding.
//     */
//    private fun getRawTextBounds(): RectF {
//
//        updatePaint()
//
//        val textBounds = Rect()
//
//        paint.getTextBounds(
//            text,
//            0,
//            text.length,
//            textBounds
//        )
//
//        val textWidth =
//            paint.measureText(text)
//
//        val left: Float
//        val right: Float
//
//        when (alignment) {
//
//            TextAlignment.LEFT -> {
//
//                left = 0f
//                right = textWidth
//            }
//
//            TextAlignment.CENTER -> {
//
//                left = -(textWidth / 2f)
//                right = textWidth / 2f
//            }
//
//            TextAlignment.RIGHT -> {
//
//                left = -textWidth
//                right = 0f
//            }
//        }
//
//        return RectF(
//            left,
//            textBounds.top.toFloat(),
//            right,
//            textBounds.bottom.toFloat()
//        )
//    }
//
//    // =========================================================================
//    // TEXT BOUNDS
//    // =========================================================================
//
//    /**
//     * Returns text bounds in local coordinates.
//     *
//     * Includes the existing selection padding.
//     *
//     * When background is enabled, the bounds also include
//     * the background padding.
//     */
//    private fun getTextBoundsInternal(): RectF {
//
//        val textBounds =
//            getRawTextBounds()
//
//        val selectionPadding = 20f
//
//        var left =
//            textBounds.left - selectionPadding
//
//        var top =
//            textBounds.top - selectionPadding
//
//        var right =
//            textBounds.right + selectionPadding
//
//        var bottom =
//            textBounds.bottom + selectionPadding
//
//        // ---------------------------------------------------------------------
//        // INCLUDE BACKGROUND
//        // ---------------------------------------------------------------------
//
//        if (backgroundEnabled) {
//
//            left -= backgroundPadding
//            top -= backgroundPadding
//            right += backgroundPadding
//            bottom += backgroundPadding
//        }
//
//        return RectF(
//            left,
//            top,
//            right,
//            bottom
//        )
//    }
//
//    // =========================================================================
//    // GET BOUNDS
//    // =========================================================================
//
//    override fun getBounds(): RectF {
//
//        val textBounds =
//            getTextBoundsInternal()
//
//        /*
//         * Apply element scale.
//         */
//
//        val scaledLeft =
//            textBounds.left * scale
//
//        val scaledTop =
//            textBounds.top * scale
//
//        val scaledRight =
//            textBounds.right * scale
//
//        val scaledBottom =
//            textBounds.bottom * scale
//
//        /*
//         * Four corners before rotation.
//         */
//
//        val corners = arrayOf(
//
//            PointF(
//                scaledLeft,
//                scaledTop
//            ),
//
//            PointF(
//                scaledRight,
//                scaledTop
//            ),
//
//            PointF(
//                scaledRight,
//                scaledBottom
//            ),
//
//            PointF(
//                scaledLeft,
//                scaledBottom
//            )
//        )
//
//        val radians =
//            Math.toRadians(
//                rotation.toDouble()
//            )
//
//        val cosValue =
//            cos(radians).toFloat()
//
//        val sinValue =
//            sin(radians).toFloat()
//
//        var minX =
//            Float.MAX_VALUE
//
//        var minY =
//            Float.MAX_VALUE
//
//        var maxX =
//            -Float.MAX_VALUE
//
//        var maxY =
//            -Float.MAX_VALUE
//
//        corners.forEach { point ->
//
//            val rotatedX =
//                point.x * cosValue -
//                        point.y * sinValue
//
//            val rotatedY =
//                point.x * sinValue +
//                        point.y * cosValue
//
//            minX =
//                minOf(
//                    minX,
//                    rotatedX
//                )
//
//            minY =
//                minOf(
//                    minY,
//                    rotatedY
//                )
//
//            maxX =
//                maxOf(
//                    maxX,
//                    rotatedX
//                )
//
//            maxY =
//                maxOf(
//                    maxY,
//                    rotatedY
//                )
//        }
//
//        return RectF(
//            position.x + minX,
//            position.y + minY,
//            position.x + maxX,
//            position.y + maxY
//        )
//    }
//
//    // =========================================================================
//    // HIT TEST
//    // =========================================================================
//
//    override fun contains(
//        x: Float,
//        y: Float
//    ): Boolean {
//
//        /*
//         * Convert world position into
//         * element-local coordinates.
//         */
//
//        val dx =
//            x - position.x
//
//        val dy =
//            y - position.y
//
//        /*
//         * Reverse the element rotation.
//         */
//
//        val radians =
//            Math.toRadians(
//                (-rotation).toDouble()
//            )
//
//        val cosValue =
//            cos(radians).toFloat()
//
//        val sinValue =
//            sin(radians).toFloat()
//
//        val localX =
//            dx * cosValue -
//                    dy * sinValue
//
//        val localY =
//            dx * sinValue +
//                    dy * cosValue
//
//        /*
//         * Remove element scale.
//         */
//
//        val inverseScale =
//            if (scale == 0f) {
//                1f
//            } else {
//                1f / scale
//            }
//
//        val unscaledX =
//            localX * inverseScale
//
//        val unscaledY =
//            localY * inverseScale
//
//        val bounds =
//            getTextBoundsInternal()
//
//        return bounds.contains(
//            unscaledX,
//            unscaledY
//        )
//    }
//
//    // =========================================================================
//    // MOVE
//    // =========================================================================
//
//    override fun moveBy(
//        dx: Float,
//        dy: Float
//    ) {
//
//        position.x += dx
//
//        position.y += dy
//    }
//
//    // =========================================================================
//    // TRANSFORM LOCAL POINT
//    // =========================================================================
//
//    /**
//     * Transforms a local point using
//     * the current element scale and rotation.
//     */
//    private fun transformLocalPoint(
//        point: PointF
//    ): PointF {
//
//        /*
//         * Apply scale.
//         */
//
//        val scaledX =
//            point.x * scale
//
//        val scaledY =
//            point.y * scale
//
//        /*
//         * Apply rotation.
//         */
//
//        val radians =
//            Math.toRadians(
//                rotation.toDouble()
//            )
//
//        val cosValue =
//            cos(radians).toFloat()
//
//        val sinValue =
//            sin(radians).toFloat()
//
//        val rotatedX =
//            scaledX * cosValue -
//                    scaledY * sinValue
//
//        val rotatedY =
//            scaledX * sinValue +
//                    scaledY * cosValue
//
//        /*
//         * Convert back into image coordinates.
//         */
//
//        return PointF(
//            position.x + rotatedX,
//            position.y + rotatedY
//        )
//    }
//
//    // =========================================================================
//    // RESIZE HANDLE POSITION
//    // =========================================================================
//
//    /**
//     * Returns the bottom-right resize handle
//     * position in IMAGE coordinates.
//     */
//    fun getResizeHandlePosition(): PointF {
//
//        val bounds =
//            getTextBoundsInternal()
//
//        return transformLocalPoint(
//            PointF(
//                bounds.right,
//                bounds.bottom
//            )
//        )
//    }
//
//    // =========================================================================
//    // ROTATION HANDLE POSITION
//    // =========================================================================
//
//    /**
//     * Returns the top-center rotation handle
//     * position in IMAGE coordinates.
//     */
//    fun getRotationHandlePosition(): PointF {
//
//        val bounds =
//            getTextBoundsInternal()
//
//        /*
//         * Distance between selection box
//         * and rotation handle.
//         */
//
//        val handleOffset =
//            60f
//
//        return transformLocalPoint(
//            PointF(
//                (bounds.left + bounds.right) / 2f,
//                bounds.top - handleOffset
//            )
//        )
//    }
//
//    // =========================================================================
//    // DRAW
//    // =========================================================================
//
//    override fun draw(
//        canvas: Canvas,
//        matrix: Matrix
//    ) {
//
//        updatePaint()
//
//        /*
//         * Convert element position
//         * from image -> screen.
//         */
//
//        val screenPoint =
//            floatArrayOf(
//                position.x,
//                position.y
//            )
//
//        matrix.mapPoints(
//            screenPoint
//        )
//
//        /*
//         * Get matrix scale.
//         */
//
//        val matrixValues =
//            FloatArray(9)
//
//        matrix.getValues(
//            matrixValues
//        )
//
//        val matrixScale =
//            matrixValues[
//                Matrix.MSCALE_X
//            ]
//
//        canvas.save()
//
//        // ---------------------------------------------------------------------
//        // POSITION
//        // ---------------------------------------------------------------------
//
//        canvas.translate(
//            screenPoint[0],
//            screenPoint[1]
//        )
//
//        // ---------------------------------------------------------------------
//        // ROTATION
//        // ---------------------------------------------------------------------
//
//        canvas.rotate(
//            rotation
//        )
//
//        // ---------------------------------------------------------------------
//        // SCALE
//        // ---------------------------------------------------------------------
//
//        canvas.scale(
//            matrixScale * scale,
//            matrixScale * scale
//        )
//
//        // ---------------------------------------------------------------------
//        // BACKGROUND
//        // ---------------------------------------------------------------------
//
//        if (backgroundEnabled) {
//
//            val textBounds =
//                getRawTextBounds()
//
//            backgroundPaint.color =
//                backgroundColor
//
//            val backgroundRect =
//                RectF(
//                    textBounds.left -
//                            backgroundPadding,
//
//                    textBounds.top -
//                            backgroundPadding,
//
//                    textBounds.right +
//                            backgroundPadding,
//
//                    textBounds.bottom +
//                            backgroundPadding
//                )
//
//            canvas.drawRoundRect(
//                backgroundRect,
//                backgroundCornerRadius,
//                backgroundCornerRadius,
//                backgroundPaint
//            )
//        }
//
//        // ---------------------------------------------------------------------
//        // TEXT
//        // ---------------------------------------------------------------------
//
//        canvas.drawText(
//            text,
//            0f,
//            0f,
//            paint
//        )
//
//        canvas.restore()
//
//        // ---------------------------------------------------------------------
//        // SELECTION
//        // ---------------------------------------------------------------------
//
//        if (isSelected) {
//
//            drawSelection(
//                canvas,
//                matrix,
//                matrixScale
//            )
//        }
//    }
//
//    // =========================================================================
//    // DRAW SELECTION
//    // =========================================================================
//
//    private fun drawSelection(
//        canvas: Canvas,
//        matrix: Matrix,
//        matrixScale: Float
//    ) {
//
//        val bounds =
//            getTextBoundsInternal()
//
//        canvas.save()
//
//        /*
//         * Convert position to screen.
//         */
//
//        val screenPoint =
//            floatArrayOf(
//                position.x,
//                position.y
//            )
//
//        matrix.mapPoints(
//            screenPoint
//        )
//
//        canvas.translate(
//            screenPoint[0],
//            screenPoint[1]
//        )
//
//        /*
//         * Apply same rotation as text.
//         */
//
//        canvas.rotate(
//            rotation
//        )
//
//        /*
//         * Apply same scale as text.
//         */
//
//        canvas.scale(
//            matrixScale * scale,
//            matrixScale * scale
//        )
//
//        // ---------------------------------------------------------------------
//        // SELECTION BACKGROUND
//        // ---------------------------------------------------------------------
//
//        canvas.drawRect(
//            bounds,
//            selectionBackgroundPaint
//        )
//
//        // ---------------------------------------------------------------------
//        // SELECTION BORDER
//        // ---------------------------------------------------------------------
//
//        canvas.drawRect(
//            bounds,
//            selectionPaint
//        )
//
//        /*
//         * Compensate handle size for canvas scale.
//         *
//         * This keeps the handles approximately
//         * the same size on screen regardless
//         * of zoom or element scale.
//         */
//
//        val totalScale =
//            (matrixScale * scale)
//                .coerceAtLeast(
//                    0.01f
//                )
//
//        val handleRadius =
//            12f / totalScale
//
//        // ---------------------------------------------------------------------
//        // ROTATION HANDLE
//        // ---------------------------------------------------------------------
//
//        val rotationHandleOffset =
//            60f
//
//        val rotationHandleX =
//            (bounds.left + bounds.right) / 2f
//
//        val rotationHandleY =
//            bounds.top -
//                    rotationHandleOffset
//
//        /*
//         * Connector line.
//         */
//
//        canvas.drawLine(
//            rotationHandleX,
//            bounds.top,
//            rotationHandleX,
//            rotationHandleY,
//            selectionPaint
//        )
//
//        /*
//         * Handle fill.
//         */
//
//        canvas.drawCircle(
//            rotationHandleX,
//            rotationHandleY,
//            handleRadius,
//            handlePaint
//        )
//
//        /*
//         * Handle border.
//         */
//
//        canvas.drawCircle(
//            rotationHandleX,
//            rotationHandleY,
//            handleRadius,
//            handleStrokePaint
//        )
//
//        // ---------------------------------------------------------------------
//        // RESIZE HANDLE
//        // ---------------------------------------------------------------------
//
//        /*
//         * Handle fill.
//         */
//
//        canvas.drawCircle(
//            bounds.right,
//            bounds.bottom,
//            handleRadius,
//            handlePaint
//        )
//
//        /*
//         * Handle border.
//         */
//
//        canvas.drawCircle(
//            bounds.right,
//            bounds.bottom,
//            handleRadius,
//            handleStrokePaint
//        )
//
//        canvas.restore()
//    }
//}
//
//

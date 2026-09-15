package com.allay.photoeditor.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.TextElement
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class PhotoEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {

        private const val TAG = "PhotoEditor"

        // ---------------------------------------------------------------------
        // IMAGE SCALE
        // ---------------------------------------------------------------------

        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 5.0f

        // ---------------------------------------------------------------------
        // ELEMENT SCALE
        // ---------------------------------------------------------------------

        private const val MIN_ELEMENT_SCALE = 0.2f
        private const val MAX_ELEMENT_SCALE = 5.0f

        // ---------------------------------------------------------------------
        // TRANSFORM HANDLES
        // ---------------------------------------------------------------------

        private const val HANDLE_TOUCH_RADIUS = 40f

        private const val ROTATION_HANDLE_DISTANCE = 70f
        private const val HANDLE_RADIUS = 18f

        private const val SELECTION_STROKE_WIDTH = 3f
    }

    // =========================================================================
    // PAINT
    // =========================================================================

    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    )

    // =========================================================================
    // BITMAP
    // =========================================================================

    private var bitmap: Bitmap? = null

    // =========================================================================
    // IMAGE TRANSFORM
    // =========================================================================

    private var scaleFactor = MIN_SCALE

    private var translationX = 0f

    private var translationY = 0f

    // =========================================================================
    // TOUCH
    // =========================================================================

    private var lastTouchX = 0f

    private var lastTouchY = 0f

    private var isDragging = false

    /**
     * True when the current gesture is moving
     * an editor element.
     *
     * False means the gesture is being used
     * for image panning.
     */
    private var isMovingElement = false

    // =========================================================================
    // ELEMENT TRANSFORM
    // =========================================================================

    private enum class TransformMode {
        NONE,
        ROTATE,
        RESIZE
    }

    private var transformMode = TransformMode.NONE

    private var initialRotation = 0f

    private var initialRotationAngle = 0f

    private var initialElementScale = 1f

    private var initialResizeDistance = 1f

    // =========================================================================
    // ELEMENTS
    // =========================================================================

    /**
     * Elements are stored in ORIGINAL IMAGE coordinates.
     */
    private val elements = mutableListOf<EditorElement>()

    private var selectedElement: EditorElement? = null

    // =========================================================================
    // CALLBACKS
    // =========================================================================

    /**
     * Called whenever the selected element changes.
     */
    var onSelectionChanged:
            ((EditorElement?) -> Unit)? = null

    /**
     * Called when an existing TextElement is double-tapped.
     *
     * MainActivity opens the edit dialog.
     */
    var onEditTextRequested:
            ((TextElement) -> Unit)? = null

    // =========================================================================
    // MATRICES
    // =========================================================================

    private val imageToScreenMatrix = Matrix()

    private val screenToImageMatrix = Matrix()

    // =========================================================================
    // SCALE GESTURE
    // =========================================================================

    private val scaleGestureDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {

                    /*
                     * Do not zoom the image while the user is
                     * resizing an editor element.
                     */
                    if (transformMode != TransformMode.NONE) {
                        return false
                    }

                    scaleFactor *= detector.scaleFactor

                    scaleFactor = scaleFactor.coerceIn(
                        MIN_SCALE,
                        MAX_SCALE
                    )

                    invalidate()

                    return true
                }
            }
        )

    // =========================================================================
    // DOUBLE TAP GESTURE
    // =========================================================================

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onDown(
                    e: MotionEvent
                ): Boolean {

                    /*
                     * Must return true so GestureDetector
                     * continues receiving this gesture.
                     */
                    return true
                }

                override fun onDoubleTap(
                    e: MotionEvent
                ): Boolean {

                    Log.d(
                        TAG,
                        "Double tap detected at " +
                                "x=${e.x}, y=${e.y}"
                    )

                    /*
                     * Handle double tap only on actual elements.
                     */
                    val imagePoint = screenToImage(
                        e.x,
                        e.y
                    )

                    if (imagePoint == null) {

                        Log.d(
                            TAG,
                            "Double tap ignored: no image"
                        )

                        return true
                    }

                    val tappedElement = findElementAt(
                        imagePoint.x,
                        imagePoint.y
                    )

                    if (tappedElement == null) {

                        Log.d(
                            TAG,
                            "Double tap ignored: no element"
                        )

                        return true
                    }

                    Log.d(
                        TAG,
                        "Double tapped element: $tappedElement"
                    )

                    /*
                     * Only TextElement supports text editing.
                     */
                    if (tappedElement is TextElement) {

                        selectElement(
                            tappedElement
                        )

                        Log.d(
                            TAG,
                            "Opening text editor for: " +
                                    tappedElement.text
                        )

                        onEditTextRequested?.invoke(
                            tappedElement
                        )
                    }

                    return true
                }
            }
        )

    // =========================================================================
    // INIT
    // =========================================================================

    init {

        setBackgroundColor(
            Color.BLACK
        )

        isClickable = true
        isFocusable = true
    }

    // =========================================================================
    // IMAGE
    // =========================================================================

    fun setImage(
        bitmap: Bitmap
    ) {

        this.bitmap = bitmap

        resetTransform()

        elements.clear()

        selectedElement = null

        transformMode = TransformMode.NONE

        notifySelectionChanged()

        Log.d(
            TAG,
            "Image set: ${bitmap.width} x ${bitmap.height}"
        )

        invalidate()
    }

    fun clearImage() {

        bitmap = null

        elements.clear()

        selectedElement = null

        resetTransform()

        transformMode = TransformMode.NONE

        notifySelectionChanged()

        invalidate()
    }

    fun getCurrentBitmap(): Bitmap? {

        return bitmap
    }

    fun getImageWidth(): Int {

        return bitmap?.width ?: 0
    }

    fun getImageHeight(): Int {

        return bitmap?.height ?: 0
    }

    // =========================================================================
    // IMAGE TRANSFORM
    // =========================================================================

    private fun resetTransform() {

        scaleFactor = MIN_SCALE

        translationX = 0f

        translationY = 0f
    }

    // =========================================================================
    // BASE IMAGE RECT
    // =========================================================================

    fun getBaseImageRect(): RectF {

        val currentBitmap =
            bitmap ?: return RectF()

        val bitmapWidth =
            currentBitmap.width.toFloat()

        val bitmapHeight =
            currentBitmap.height.toFloat()

        val viewWidth =
            width.toFloat()

        val viewHeight =
            height.toFloat()

        if (
            viewWidth <= 0f ||
            viewHeight <= 0f
        ) {

            return RectF()
        }

        val fitScale =
            min(
                viewWidth / bitmapWidth,
                viewHeight / bitmapHeight
            )

        val scaledWidth =
            bitmapWidth * fitScale

        val scaledHeight =
            bitmapHeight * fitScale

        val left =
            (viewWidth - scaledWidth) / 2f

        val top =
            (viewHeight - scaledHeight) / 2f

        return RectF(
            left,
            top,
            left + scaledWidth,
            top + scaledHeight
        )
    }

    // =========================================================================
    // FIT SCALE
    // =========================================================================

    private fun getFitScale(): Float {

        val currentBitmap =
            bitmap ?: return 1f

        if (
            width <= 0 ||
            height <= 0
        ) {

            return 1f
        }

        return min(
            width.toFloat() /
                    currentBitmap.width,

            height.toFloat() /
                    currentBitmap.height
        )
    }

    // =========================================================================
    // UPDATE MATRICES
    // =========================================================================

    private fun updateMatrices() {

        val currentBitmap =
            bitmap ?: return

        if (
            width <= 0 ||
            height <= 0
        ) {

            return
        }

        val fitScale =
            getFitScale()

        val centerX =
            width / 2f

        val centerY =
            height / 2f

        // ---------------------------------------------------------------------
        // IMAGE -> SCREEN
        // ---------------------------------------------------------------------

        imageToScreenMatrix.reset()

        imageToScreenMatrix.postTranslate(
            -currentBitmap.width / 2f,
            -currentBitmap.height / 2f
        )

        imageToScreenMatrix.postScale(
            fitScale * scaleFactor,
            fitScale * scaleFactor
        )

        imageToScreenMatrix.postTranslate(
            centerX + translationX,
            centerY + translationY
        )

        // ---------------------------------------------------------------------
        // SCREEN -> IMAGE
        // ---------------------------------------------------------------------

        imageToScreenMatrix.invert(
            screenToImageMatrix
        )
    }

    // =========================================================================
    // SCREEN -> IMAGE
    // =========================================================================

    fun screenToImage(
        screenX: Float,
        screenY: Float
    ): PointF? {

        if (
            bitmap == null
        ) {

            return null
        }

        updateMatrices()

        val points =
            floatArrayOf(
                screenX,
                screenY
            )

        screenToImageMatrix.mapPoints(
            points
        )

        return PointF(
            points[0],
            points[1]
        )
    }

    // =========================================================================
    // IMAGE -> SCREEN
    // =========================================================================

    fun imageToScreen(
        imageX: Float,
        imageY: Float
    ): PointF? {

        if (
            bitmap == null
        ) {

            return null
        }

        updateMatrices()

        val points =
            floatArrayOf(
                imageX,
                imageY
            )

        imageToScreenMatrix.mapPoints(
            points
        )

        return PointF(
            points[0],
            points[1]
        )
    }

    // =========================================================================
    // ELEMENTS
    // =========================================================================

    fun addElement(
        element: EditorElement
    ) {

        /*
         * Deselect previous element.
         */
        selectedElement?.isSelected = false

        /*
         * New element becomes selected.
         */
        element.isSelected = true

        selectedElement = element

        elements.add(
            element
        )

        Log.d(
            TAG,
            "Element added. Total elements: " +
                    elements.size
        )

        notifySelectionChanged()

        invalidate()
    }

    fun getElements(): List<EditorElement> {

        return elements
    }

    fun clearElements() {

        elements.forEach {
            it.isSelected = false
        }

        elements.clear()

        selectedElement = null

        transformMode = TransformMode.NONE

        notifySelectionChanged()

        invalidate()
    }

    // =========================================================================
    // TEXT UPDATE
    // =========================================================================

    /**
     * Updates an existing TextElement.
     *
     * The existing element is retained.
     *
     * Position, color, size, rotation,
     * scale, typeface, bold, italic and
     * alignment remain unchanged.
     */
    fun updateTextElement(
        textElement: TextElement,
        newText: String
    ) {

        if (
            !elements.contains(
                textElement
            )
        ) {

            Log.w(
                TAG,
                "Cannot update text. Element not found."
            )

            return
        }

        textElement.updateText(
            newText
        )

        textElement.isSelected = true

        selectedElement = textElement

        Log.d(
            TAG,
            "Text element updated: $newText"
        )

        notifySelectionChanged()

        invalidate()
    }

    /**
     * Updates the color of an existing TextElement.
     */
    fun updateTextElementColor(
        textElement: TextElement,
        newColor: Int
    ) {

        if (
            !elements.contains(
                textElement
            )
        ) {

            Log.w(
                TAG,
                "Cannot update text color. " +
                        "Element not found."
            )

            return
        }

        textElement.updateColor(
            newColor
        )

        textElement.isSelected = true

        selectedElement = textElement

        Log.d(
            TAG,
            "Text color updated: $newColor"
        )

        notifySelectionChanged()

        invalidate()
    }

    /**
     * Updates the color of a selected range inside a TextElement.
     */
    fun updateTextElementColorRange(
        textElement: TextElement,
        start: Int,
        end: Int,
        newColor: Int
    ) {

        if (!elements.contains(textElement)) {
            Log.w(
                TAG,
                "Cannot update text color range. Element not found."
            )
            return
        }

        textElement.updateColorRange(
            start,
            end,
            newColor
        )

        textElement.isSelected = true
        selectedElement = textElement

        Log.d(
            TAG,
            "Text color range updated: " +
                    "start=$start end=$end color=$newColor"
        )

        notifySelectionChanged()
        invalidate()
    }

    /**
     * Restores previously saved text color ranges.
     */
    fun restoreTextElementColorRanges(
        textElement: TextElement,
        ranges: List<TextElement.TextColorRange>
    ) {

        if (!elements.contains(textElement)) {
            Log.w(
                TAG,
                "Cannot restore text color ranges. Element not found."
            )
            return
        }

        textElement.setColorRanges(ranges)
        textElement.isSelected = true
        selectedElement = textElement

        Log.d(
            TAG,
            "Text color ranges restored: ${ranges.size} ranges"
        )

        notifySelectionChanged()
        invalidate()
    }

    /**
     * Updates the size of an existing TextElement.
     */
    fun updateTextElementSize(
        textElement: TextElement,
        newSize: Float
    ) {

        if (
            !elements.contains(
                textElement
            )
        ) {

            Log.w(
                TAG,
                "Cannot update text size. " +
                        "Element not found."
            )

            return
        }

        textElement.updateTextSize(
            newSize
        )

        textElement.isSelected = true

        selectedElement = textElement

        Log.d(
            TAG,
            "Text size updated: " +
                    textElement.textSize
        )

        notifySelectionChanged()

        invalidate()
    }

    /**
     * Updates the Bold state.
     */
    fun updateTextElementBold(
        textElement: TextElement,
        enabled: Boolean
    ) {

        if (
            !elements.contains(
                textElement
            )
        ) {

            Log.w(
                TAG,
                "Cannot update text bold. " +
                        "Element not found."
            )

            return
        }

        textElement.updateBold(
            enabled
        )

        textElement.isSelected = true

        selectedElement = textElement

        Log.d(
            TAG,
            "Text bold updated: " +
                    textElement.bold
        )

        notifySelectionChanged()

        invalidate()
    }

    /**
     * Updates the Italic state.
     */
    fun updateTextElementItalic(
        textElement: TextElement,
        enabled: Boolean
    ) {

        if (
            !elements.contains(
                textElement
            )
        ) {

            Log.w(
                TAG,
                "Cannot update text italic. " +
                        "Element not found."
            )

            return
        }

        textElement.updateItalic(
            enabled
        )

        textElement.isSelected = true

        selectedElement = textElement

        Log.d(
            TAG,
            "Text italic updated: " +
                    textElement.italic
        )

        notifySelectionChanged()

        invalidate()
    }

    /**
     * Updates text alignment.
     */
    fun updateTextElementAlignment(
        textElement: TextElement,
        alignment: TextElement.TextAlignment
    ) {

        if (
            !elements.contains(
                textElement
            )
        ) {

            Log.w(
                TAG,
                "Cannot update text alignment. " +
                        "Element not found."
            )

            return
        }

        textElement.updateAlignment(
            alignment
        )

        textElement.isSelected = true

        selectedElement = textElement

        Log.d(
            TAG,
            "Text alignment updated: " +
                    textElement.alignment
        )

        notifySelectionChanged()

        invalidate()
    }

    /**
     * Updates the typeface of an existing TextElement.
     */
    fun updateTextElementFont(
        textElement: TextElement,
        font: TextElement.TextFont
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update font. Element not found.")
            return
        }

        textElement.updateFont(font)

        textElement.isSelected = true
        selectedElement = textElement

        Log.d(
            TAG,
            "Text font updated: ${textElement.getFontDisplayName()}"
        )

        notifySelectionChanged()
        invalidate()
    }




    /**
     * Updates the background enabled state
     * of an existing TextElement.
     */
    fun updateTextElementBackground(
        textElement: TextElement,
        enabled: Boolean
    ) {

        if (
            !elements.contains(
                textElement
            )
        ) {

            Log.w(
                TAG,
                "Cannot update text background. " +
                        "Element not found."
            )

            return
        }

        textElement.updateBackground(
            enabled
        )

        textElement.isSelected = true

        selectedElement = textElement

        Log.d(
            TAG,
            "Text background updated: " +
                    enabled
        )

        notifySelectionChanged()

        invalidate()
    }

    /**
     * Updates the background color
     * of an existing TextElement.
     */
    fun updateTextElementBackgroundColor(
        textElement: TextElement,
        newColor: Int
    ) {

        if (
            !elements.contains(
                textElement
            )
        ) {

            Log.w(
                TAG,
                "Cannot update text background color. " +
                        "Element not found."
            )

            return
        }

        textElement.updateBackgroundColor(
            newColor
        )

        textElement.isSelected = true

        selectedElement = textElement

        Log.d(
            TAG,
            "Text background color updated: " +
                    newColor
        )

        notifySelectionChanged()

        invalidate()
    }

    // =========================================================================
    // SELECTION
    // =========================================================================

    private fun selectElement(
        element: EditorElement?
    ) {

        /*
         * Already selected.
         */
        if (
            selectedElement === element
        ) {

            return
        }

        /*
         * Deselect previous element.
         */
        selectedElement?.isSelected = false

        /*
         * Select new element.
         */
        selectedElement = element

        selectedElement?.isSelected = true

        transformMode = TransformMode.NONE

        Log.d(
            TAG,
            "Selected element: $selectedElement"
        )

        notifySelectionChanged()

        invalidate()
    }

    private fun notifySelectionChanged() {

        onSelectionChanged?.invoke(
            selectedElement
        )
    }

    fun getSelectedElement(): EditorElement? {

        return selectedElement
    }

    // =========================================================================
    // ELEMENT HIT TESTING
    // =========================================================================

    private fun findElementAt(
        imageX: Float,
        imageY: Float
    ): EditorElement? {

        /*
         * Search backwards so the top-most
         * element is selected first.
         */
        for (
        index in elements.indices.reversed()
        ) {

            val element =
                elements[index]

            if (
                element.contains(
                    imageX,
                    imageY
                )
            ) {

                return element
            }
        }

        return null
    }

    // =========================================================================
    // ROTATION HANDLE
    // =========================================================================

    private fun getRotationHandlePosition(
        textElement: TextElement
    ): PointF {

        val bounds =
            textElement.getBounds()

        /*
         * Handle starts from the top-center
         * of the element.
         */
        val localX =
            (bounds.left + bounds.right) / 2f

        val localY =
            bounds.top -
                    ROTATION_HANDLE_DISTANCE

        val localPoint =
            PointF(
                localX,
                localY
            )

        return transformElementPoint(
            textElement,
            localPoint
        )
    }

    // =========================================================================
    // RESIZE HANDLE
    // =========================================================================

    private fun getResizeHandlePosition(
        textElement: TextElement
    ): PointF {

        val bounds =
            textElement.getBounds()

        val localPoint =
            PointF(
                bounds.right,
                bounds.bottom
            )

        return transformElementPoint(
            textElement,
            localPoint
        )
    }

    // =========================================================================
    // ELEMENT POINT TRANSFORMATION
    // =========================================================================

    private fun transformElementPoint(
        textElement: TextElement,
        localPoint: PointF
    ): PointF {

        /*
         * TextElement position is the local origin.
         */
        val dx =
            localPoint.x

        val dy =
            localPoint.y

        val rotationRadians =
            Math.toRadians(
                textElement.rotation.toDouble()
            )

        val cosValue =
            cos(rotationRadians).toFloat()

        val sinValue =
            sin(rotationRadians).toFloat()

        /*
         * Apply element scale.
         */
        val scaledX =
            dx * textElement.scale

        val scaledY =
            dy * textElement.scale

        /*
         * Apply element rotation.
         */
        val rotatedX =
            scaledX * cosValue -
                    scaledY * sinValue

        val rotatedY =
            scaledX * sinValue +
                    scaledY * cosValue

        /*
         * Convert from image coordinates
         * to screen coordinates.
         */
        return imageToScreen(
            textElement.position.x + rotatedX,
            textElement.position.y + rotatedY
        ) ?: PointF()
    }

    // =========================================================================
    // ROTATION HANDLE HIT TEST
    // =========================================================================

    private fun isOnRotationHandle(
        eventX: Float,
        eventY: Float
    ): Boolean {

        val element =
            selectedElement as? TextElement
                ?: return false

        val handle =
            getRotationHandlePosition(
                element
            )

        return distance(
            eventX,
            eventY,
            handle.x,
            handle.y
        ) <= HANDLE_TOUCH_RADIUS
    }

    // =========================================================================
    // RESIZE HANDLE HIT TEST
    // =========================================================================

    private fun isOnResizeHandle(
        eventX: Float,
        eventY: Float
    ): Boolean {

        val element =
            selectedElement as? TextElement
                ?: return false

        val handle =
            getResizeHandlePosition(
                element
            )

        return distance(
            eventX,
            eventY,
            handle.x,
            handle.y
        ) <= HANDLE_TOUCH_RADIUS
    }

    // =========================================================================
    // DISTANCE
    // =========================================================================

    private fun distance(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {

        return hypot(
            x2 - x1,
            y2 - y1
        )
    }

    // =========================================================================
    // START ROTATION
    // =========================================================================

    private fun startRotation(
        touchX: Float,
        touchY: Float
    ) {

        val element =
            selectedElement as? TextElement
                ?: return

        transformMode =
            TransformMode.ROTATE

        initialRotation =
            element.rotation

        /*
         * Calculate the center of the text
         * in screen coordinates.
         */
        val center =
            imageToScreen(
                element.position.x,
                element.position.y
            ) ?: return

        initialRotationAngle =
            Math.toDegrees(
                atan2(
                    (touchY - center.y).toDouble(),
                    (touchX - center.x).toDouble()
                )
            ).toFloat()

        Log.d(
            TAG,
            "Rotation started. " +
                    "initialRotation=$initialRotation " +
                    "initialAngle=$initialRotationAngle"
        )
    }

    // =========================================================================
    // UPDATE ROTATION
    // =========================================================================

    private fun updateRotation(
        touchX: Float,
        touchY: Float
    ) {

        val element =
            selectedElement as? TextElement
                ?: return

        val center =
            imageToScreen(
                element.position.x,
                element.position.y
            ) ?: return

        val currentAngle =
            Math.toDegrees(
                atan2(
                    (touchY - center.y).toDouble(),
                    (touchX - center.x).toDouble()
                )
            ).toFloat()

        var delta =
            currentAngle -
                    initialRotationAngle

        /*
         * Normalize delta to -180..180.
         */
        while (delta > 180f) {
            delta -= 360f
        }

        while (delta < -180f) {
            delta += 360f
        }

        var newRotation =
            initialRotation + delta

        /*
         * Normalize rotation to 0..360.
         */
        while (newRotation < 0f) {
            newRotation += 360f
        }

        while (newRotation >= 360f) {
            newRotation -= 360f
        }

        element.rotation =
            newRotation

        Log.d(
            TAG,
            "Element rotation=$newRotation"
        )

        invalidate()
    }

    // =========================================================================
    // START RESIZE
    // =========================================================================

    private fun startResize(
        touchX: Float,
        touchY: Float
    ) {

        val element =
            selectedElement as? TextElement
                ?: return

        transformMode =
            TransformMode.RESIZE

        initialElementScale =
            element.scale

        val center =
            imageToScreen(
                element.position.x,
                element.position.y
            ) ?: return

        initialResizeDistance =
            distance(
                center.x,
                center.y,
                touchX,
                touchY
            ).coerceAtLeast(
                1f
            )

        Log.d(
            TAG,
            "Resize started. " +
                    "initialScale=$initialElementScale " +
                    "initialDistance=$initialResizeDistance"
        )
    }

    // =========================================================================
    // UPDATE RESIZE
    // =========================================================================

    private fun updateResize(
        touchX: Float,
        touchY: Float
    ) {

        val element =
            selectedElement as? TextElement
                ?: return

        val center =
            imageToScreen(
                element.position.x,
                element.position.y
            ) ?: return

        val currentDistance =
            distance(
                center.x,
                center.y,
                touchX,
                touchY
            )

        if (
            initialResizeDistance <= 0f
        ) {
            return
        }

        val ratio =
            currentDistance /
                    initialResizeDistance

        val newScale =
            (
                    initialElementScale *
                            ratio
                    ).coerceIn(
                    MIN_ELEMENT_SCALE,
                    MAX_ELEMENT_SCALE
                )

        element.scale =
            newScale

        Log.d(
            TAG,
            "Element scale=$newScale"
        )

        invalidate()
    }

    // =========================================================================
    // DELETE
    // =========================================================================

    fun deleteSelectedElement() {

        val element =
            selectedElement
                ?: return

        Log.d(
            TAG,
            "Deleting selected element"
        )

        elements.remove(
            element
        )

        element.isSelected = false

        selectedElement = null

        transformMode = TransformMode.NONE

        notifySelectionChanged()

        invalidate()
    }

    // =========================================================================
    // TEST TEXT
    // =========================================================================

    fun addTestText() {

        if (
            bitmap == null
        ) {

            Log.d(
                TAG,
                "Cannot add text. No image selected."
            )

            return
        }

        val imageWidth =
            bitmap!!.width.toFloat()

        val imageHeight =
            bitmap!!.height.toFloat()

        val position =
            PointF(
                imageWidth / 2f,
                imageHeight / 2f
            )

        val textElement =
            TextElement(
                text = "Hello Photo Editor",
                position = position,
                textSize = 80f,
                color = Color.WHITE
            )

        addElement(
            textElement
        )
    }

    // =========================================================================
    // DRAW
    // =========================================================================

    override fun onDraw(
        canvas: Canvas
    ) {

        super.onDraw(
            canvas
        )

        val currentBitmap =
            bitmap ?: return

        if (
            width <= 0 ||
            height <= 0
        ) {

            return
        }

        updateMatrices()

        // ---------------------------------------------------------------------
        // DRAW IMAGE
        // ---------------------------------------------------------------------

        canvas.save()

        canvas.concat(
            imageToScreenMatrix
        )

        canvas.drawBitmap(
            currentBitmap,
            0f,
            0f,
            bitmapPaint
        )

        canvas.restore()

        // ---------------------------------------------------------------------
        // DRAW ELEMENTS
        // ---------------------------------------------------------------------

        elements.forEach { element ->

            element.draw(
                canvas,
                imageToScreenMatrix
            )
        }

        // ---------------------------------------------------------------------
        // DRAW SELECTION HANDLES
        // ---------------------------------------------------------------------

        val selectedText =
            selectedElement as? TextElement

        if (
            selectedText != null &&
            selectedText.isSelected
        ) {

            drawTextSelectionHandles(
                canvas,
                selectedText
            )
        }
    }

    // =========================================================================
    // DRAW TEXT HANDLES
    // =========================================================================

    private fun drawTextSelectionHandles(
        canvas: Canvas,
        textElement: TextElement
    ) {

        val rotationHandle =
            getRotationHandlePosition(
                textElement
            )

        val resizeHandle =
            getResizeHandlePosition(
                textElement
            )

        val bounds =
            textElement.getBounds()

        /*
         * Four corners of the local selection bounds.
         */
        val topLeft =
            transformElementPoint(
                textElement,
                PointF(
                    bounds.left,
                    bounds.top
                )
            )

        val topRight =
            transformElementPoint(
                textElement,
                PointF(
                    bounds.right,
                    bounds.top
                )
            )

        val bottomLeft =
            transformElementPoint(
                textElement,
                PointF(
                    bounds.left,
                    bounds.bottom
                )
            )

        val bottomRight =
            transformElementPoint(
                textElement,
                PointF(
                    bounds.right,
                    bounds.bottom
                )
            )

        val selectionPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    SELECTION_STROKE_WIDTH

                color =
                    Color.WHITE
            }

        // ---------------------------------------------------------------------
        // SELECTION RECTANGLE
        // ---------------------------------------------------------------------

        val path =
            android.graphics.Path()

        path.moveTo(
            topLeft.x,
            topLeft.y
        )

        path.lineTo(
            topRight.x,
            topRight.y
        )

        path.lineTo(
            bottomRight.x,
            bottomRight.y
        )

        path.lineTo(
            bottomLeft.x,
            bottomLeft.y
        )

        path.close()

        canvas.drawPath(
            path,
            selectionPaint
        )

        // ---------------------------------------------------------------------
        // ROTATION CONNECTOR
        // ---------------------------------------------------------------------

        val topCenter =
            PointF(
                (topLeft.x + topRight.x) / 2f,
                (topLeft.y + topRight.y) / 2f
            )

        canvas.drawLine(
            topCenter.x,
            topCenter.y,
            rotationHandle.x,
            rotationHandle.y,
            selectionPaint
        )

        // ---------------------------------------------------------------------
        // ROTATION HANDLE
        // ---------------------------------------------------------------------

        val handleFillPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {

                style =
                    Paint.Style.FILL

                color =
                    Color.WHITE
            }

        canvas.drawCircle(
            rotationHandle.x,
            rotationHandle.y,
            HANDLE_RADIUS,
            handleFillPaint
        )

        canvas.drawCircle(
            rotationHandle.x,
            rotationHandle.y,
            HANDLE_RADIUS,
            selectionPaint
        )

        // ---------------------------------------------------------------------
        // RESIZE HANDLE
        // ---------------------------------------------------------------------

        canvas.drawCircle(
            resizeHandle.x,
            resizeHandle.y,
            HANDLE_RADIUS,
            handleFillPaint
        )

        canvas.drawCircle(
            resizeHandle.x,
            resizeHandle.y,
            HANDLE_RADIUS,
            selectionPaint
        )
    }

    // =========================================================================
    // TOUCH
    // =========================================================================

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        /*
         * First let GestureDetector process
         * taps / double taps.
         */
        gestureDetector.onTouchEvent(
            event
        )

        /*
         * Always allow ScaleGestureDetector
         * to process the event.
         */
        scaleGestureDetector.onTouchEvent(
            event
        )

        when (
            event.actionMasked
        ) {

            // =================================================================
            // TOUCH DOWN
            // =================================================================

            MotionEvent.ACTION_DOWN -> {

                lastTouchX =
                    event.x

                lastTouchY =
                    event.y

                isDragging =
                    true

                transformMode =
                    TransformMode.NONE

                /*
                 * -------------------------------------------------------------
                 * CHECK ROTATION HANDLE FIRST
                 * -------------------------------------------------------------
                 */

                if (
                    event.pointerCount == 1 &&
                    selectedElement is TextElement &&
                    isOnRotationHandle(
                        event.x,
                        event.y
                    )
                ) {

                    startRotation(
                        event.x,
                        event.y
                    )

                    isMovingElement =
                        false

                    Log.d(
                        TAG,
                        "Rotation handle touched"
                    )

                    return true
                }

                /*
                 * -------------------------------------------------------------
                 * CHECK RESIZE HANDLE
                 * -------------------------------------------------------------
                 */

                if (
                    event.pointerCount == 1 &&
                    selectedElement is TextElement &&
                    isOnResizeHandle(
                        event.x,
                        event.y
                    )
                ) {

                    startResize(
                        event.x,
                        event.y
                    )

                    isMovingElement =
                        false

                    Log.d(
                        TAG,
                        "Resize handle touched"
                    )

                    return true
                }

                /*
                 * -------------------------------------------------------------
                 * CONVERT SCREEN TO IMAGE
                 * -------------------------------------------------------------
                 */

                val imagePoint =
                    screenToImage(
                        event.x,
                        event.y
                    )

                if (
                    imagePoint != null
                ) {

                    /*
                     * ---------------------------------------------------------
                     * CHECK ELEMENT
                     * ---------------------------------------------------------
                     */

                    val touchedElement =
                        findElementAt(
                            imagePoint.x,
                            imagePoint.y
                        )

                    if (
                        touchedElement != null
                    ) {

                        /*
                         * Select it.
                         */
                        selectElement(
                            touchedElement
                        )

                        /*
                         * Current gesture moves
                         * the selected element.
                         */
                        isMovingElement =
                            true

                        Log.d(
                            TAG,
                            "Element touched"
                        )

                    } else {

                        /*
                         * -----------------------------------------------------
                         * EMPTY CANVAS
                         * -----------------------------------------------------
                         */

                        selectElement(
                            null
                        )

                        /*
                         * Current gesture pans
                         * the image.
                         */
                        isMovingElement =
                            false

                        Log.d(
                            TAG,
                            "Empty canvas touched"
                        )
                    }
                }

                return true
            }

            // =================================================================
            // TOUCH MOVE
            // =================================================================

            MotionEvent.ACTION_MOVE -> {

                /*
                 * -------------------------------------------------------------
                 * ROTATE ELEMENT
                 * -------------------------------------------------------------
                 */

                if (
                    transformMode ==
                    TransformMode.ROTATE &&
                    event.pointerCount == 1
                ) {

                    updateRotation(
                        event.x,
                        event.y
                    )

                    lastTouchX =
                        event.x

                    lastTouchY =
                        event.y

                    return true
                }

                /*
                 * -------------------------------------------------------------
                 * RESIZE ELEMENT
                 * -------------------------------------------------------------
                 */

                if (
                    transformMode ==
                    TransformMode.RESIZE &&
                    event.pointerCount == 1
                ) {

                    updateResize(
                        event.x,
                        event.y
                    )

                    lastTouchX =
                        event.x

                    lastTouchY =
                        event.y

                    return true
                }

                /*
                 * -------------------------------------------------------------
                 * NORMAL SINGLE FINGER MOVEMENT
                 * -------------------------------------------------------------
                 *
                 * Multi-touch is handled by
                 * ScaleGestureDetector.
                 */

                if (
                    event.pointerCount == 1 &&
                    !scaleGestureDetector.isInProgress &&
                    isDragging
                ) {

                    if (
                        isMovingElement &&
                        selectedElement != null
                    ) {

                        /*
                         * MOVE ELEMENT
                         */

                        val previousPoint =
                            screenToImage(
                                lastTouchX,
                                lastTouchY
                            )

                        val currentPoint =
                            screenToImage(
                                event.x,
                                event.y
                            )

                        if (
                            previousPoint != null &&
                            currentPoint != null
                        ) {

                            val dx =
                                currentPoint.x -
                                        previousPoint.x

                            val dy =
                                currentPoint.y -
                                        previousPoint.y

                            selectedElement?.moveBy(
                                dx,
                                dy
                            )

                            Log.d(
                                TAG,
                                "Moving element " +
                                        "dx=$dx dy=$dy"
                            )
                        }

                    } else {

                        /*
                         * MOVE IMAGE
                         */

                        val dx =
                            event.x -
                                    lastTouchX

                        val dy =
                            event.y -
                                    lastTouchY

                        translationX +=
                            dx

                        translationY +=
                            dy

                        Log.d(
                            TAG,
                            "Panning image " +
                                    "dx=$dx dy=$dy"
                        )
                    }

                    lastTouchX =
                        event.x

                    lastTouchY =
                        event.y

                    invalidate()
                }

                return true
            }

            // =================================================================
            // TOUCH UP / CANCEL
            // =================================================================

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                isDragging =
                    false

                isMovingElement =
                    false

                transformMode =
                    TransformMode.NONE

                return true
            }
        }

        return true
    }
}
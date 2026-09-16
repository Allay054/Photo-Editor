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
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
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

        // ---------------------------------------------------------------------
        // CROP
        // ---------------------------------------------------------------------

        // Corner handles have a generous invisible touch target so they are
        // easy to select even when the visible handle is small.
        private const val CROP_HANDLE_TOUCH_RADIUS = 64f

        // Visible corner handle size.
        private const val CROP_HANDLE_LENGTH = 36f

        // Rule-of-thirds crop grid.
        private const val CROP_GRID_STROKE_WIDTH = 1.5f

        // Minimum crop size in original image pixels.
        private const val MIN_CROP_SIZE = 100f
    }

    // =========================================================================
    // PAINT
    // =========================================================================

    private val bitmapPaint = Paint(
        Paint.ANTI_ALIAS_FLAG
    )

    private val cropOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(150, 0, 0, 0)
    }

    private val cropBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }

    private val cropGridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = CROP_GRID_STROKE_WIDTH
        color = Color.argb(140, 255, 255, 255)
    }

    private val cropHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.SQUARE
        color = Color.WHITE
    }

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

    // =========================================================================
    // CROP MODE
    // =========================================================================

    /**
     * True when crop mode is active. Crop mode is intentionally separate
     * from TransformMode because TransformMode is only for element transforms.
     */
    private var isCropMode = false

    /** Crop selection stored in original image coordinates. */
    private var cropRectImage: RectF? = null

    /**
     * Crop sizing mode.
     *
     * FREE allows each corner to move independently.
     * Aspect-ratio modes will be added in the following crop phases.
     */
    private enum class CropAspectRatio {
        FREE,
        ONE_TO_ONE,
        FOUR_TO_THREE,
        SIXTEEN_TO_NINE,
        ORIGINAL_RATIO
    }

    /** Current crop sizing mode. Free Crop is the default. */
    private var cropAspectRatio = CropAspectRatio.FREE

    private enum class CropHandle {
        NONE,
        MOVE,
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT
    }

    private var activeCropHandle = CropHandle.NONE

    // ---------------------------------------------------------------------
    // CROP SESSION SNAPSHOT
    // ---------------------------------------------------------------------

    /**
     * State captured when crop mode starts. Cancel Crop restores this state
     * without affecting any edits that existed before the crop session.
     */
    private var cropOriginalBitmap: Bitmap? = null
    private var cropOriginalElements: List<EditorElement>? = null
    private var cropOriginalSelectedElement: EditorElement? = null

    /** Last touch position in original image coordinates while moving the crop. */
    private val lastCropTouchImage = PointF()

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

    /** Called whenever crop mode changes. */
    var onCropModeChanged:
            ((Boolean) -> Unit)? = null

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
                    if (
                        transformMode != TransformMode.NONE ||
                        isCropMode
                    ) {
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

                    if (isCropMode) {
                        Log.d(TAG, "Double tap ignored: crop mode active")
                        return true
                    }

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
        isCropMode = false
        cropRectImage = null
        clearCropSessionSnapshot()

        notifySelectionChanged()
        onCropModeChanged?.invoke(false)

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
        isCropMode = false
        activeCropHandle = CropHandle.NONE
        clearCropSessionSnapshot()

        notifySelectionChanged()
        onCropModeChanged?.invoke(false)

        invalidate()
    }

    fun getCurrentBitmap(): Bitmap? {

        return bitmap
    }

    // =========================================================================
    // CROP SELECTION
    // =========================================================================

    /** Creates a full-image crop selection in original image coordinates. */
    private fun initializeCropRect() {
        val currentBitmap = bitmap ?: return

        cropRectImage = RectF(
            0f,
            0f,
            currentBitmap.width.toFloat(),
            currentBitmap.height.toFloat()
        )

        Log.d(TAG, "Crop selection initialized: $cropRectImage")
    }

    /** Converts the crop selection from image coordinates to screen coordinates. */
    private fun getCropRectOnScreen(): RectF? {
        val cropRect = cropRectImage ?: return null

        val topLeft = imageToScreen(cropRect.left, cropRect.top) ?: return null
        val bottomRight = imageToScreen(cropRect.right, cropRect.bottom) ?: return null

        return RectF(
            minOf(topLeft.x, bottomRight.x),
            minOf(topLeft.y, bottomRight.y),
            maxOf(topLeft.x, bottomRight.x),
            maxOf(topLeft.y, bottomRight.y)
        )
    }

    /**
     * Draws the crop selection.
     * Corner resizing was added in Phase 5.3.
     * Moving the complete crop area was added in Phase 5.4.
     */
    private fun drawCropSelection(canvas: Canvas) {
        val cropRect = getCropRectOnScreen() ?: return
        val currentBitmap = bitmap ?: return

        // Darken the complete editor.
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), cropOverlayPaint)

        // Redraw only the selected area so it remains clear.
        //
        // IMPORTANT:
        // The editor elements (for example TextElement) are part of the
        // editor composition and must also be redrawn inside the crop area.
        // Previously only the bitmap was redrawn here, so entering crop mode
        // visually covered all text/elements with the overlay.
        canvas.save()
        canvas.clipRect(cropRect)

        // Redraw the image.
        canvas.drawBitmap(
            currentBitmap,
            imageToScreenMatrix,
            bitmapPaint
        )

        // Redraw all editor elements above the image.
        // This keeps text, stickers, shapes, etc. visible while cropping.
        elements.forEach { element ->
            element.draw(
                canvas,
                imageToScreenMatrix
            )
        }

        canvas.restore()

        // Rule-of-thirds grid.
        drawCropGrid(canvas, cropRect)

        // Selection border.
        canvas.drawRect(cropRect, cropBorderPaint)

        val handleLength = CROP_HANDLE_LENGTH

        // Draw the corner brackets slightly inward from each corner. The
        // actual touch target is larger than the visible bracket and is
        // handled by findCropHandle().
        //
        // Top-left.
        canvas.drawLine(
            cropRect.left,
            cropRect.top,
            cropRect.left + handleLength,
            cropRect.top,
            cropHandlePaint
        )
        canvas.drawLine(
            cropRect.left,
            cropRect.top,
            cropRect.left,
            cropRect.top + handleLength,
            cropHandlePaint
        )

        // Top-right.
        canvas.drawLine(
            cropRect.right,
            cropRect.top,
            cropRect.right - handleLength,
            cropRect.top,
            cropHandlePaint
        )
        canvas.drawLine(
            cropRect.right,
            cropRect.top,
            cropRect.right,
            cropRect.top + handleLength,
            cropHandlePaint
        )

        // Bottom-left.
        canvas.drawLine(
            cropRect.left,
            cropRect.bottom,
            cropRect.left + handleLength,
            cropRect.bottom,
            cropHandlePaint
        )
        canvas.drawLine(
            cropRect.left,
            cropRect.bottom,
            cropRect.left,
            cropRect.bottom - handleLength,
            cropHandlePaint
        )

        // Bottom-right.
        canvas.drawLine(
            cropRect.right,
            cropRect.bottom,
            cropRect.right - handleLength,
            cropRect.bottom,
            cropHandlePaint
        )
        canvas.drawLine(
            cropRect.right,
            cropRect.bottom,
            cropRect.right,
            cropRect.bottom - handleLength,
            cropHandlePaint
        )
    }


    /**
     * Draws a rule-of-thirds grid inside the current crop selection.
     * The grid is clipped to the crop rectangle so it never extends outside
     * the active crop area.
     */
    private fun drawCropGrid(
        canvas: Canvas,
        cropRect: RectF
    ) {
        val verticalOneThird =
            cropRect.left + cropRect.width() / 3f
        val verticalTwoThirds =
            cropRect.left + cropRect.width() * 2f / 3f

        val horizontalOneThird =
            cropRect.top + cropRect.height() / 3f
        val horizontalTwoThirds =
            cropRect.top + cropRect.height() * 2f / 3f

        canvas.save()
        canvas.clipRect(cropRect)

        canvas.drawLine(
            verticalOneThird,
            cropRect.top,
            verticalOneThird,
            cropRect.bottom,
            cropGridPaint
        )
        canvas.drawLine(
            verticalTwoThirds,
            cropRect.top,
            verticalTwoThirds,
            cropRect.bottom,
            cropGridPaint
        )
        canvas.drawLine(
            cropRect.left,
            horizontalOneThird,
            cropRect.right,
            horizontalOneThird,
            cropGridPaint
        )
        canvas.drawLine(
            cropRect.left,
            horizontalTwoThirds,
            cropRect.right,
            horizontalTwoThirds,
            cropGridPaint
        )

        canvas.restore()
    }

    // =========================================================================
    // CROP MODE
    // =========================================================================

    /**
     * Enters crop mode.
     *
     * Phase 5.1 only opens the mode. Crop rectangle interactions
     * are implemented in the following Phase 5 steps.
     */
    fun enterCropMode() {

        if (bitmap == null) {

            Log.d(
                TAG,
                "Cannot enter crop mode. No image selected."
            )

            return
        }

        // Capture the complete editor state before changing selection state.
        // This snapshot is used by Cancel Crop.
        createCropSessionSnapshot()

        // Deselect any active editor element while crop mode is active.
        selectedElement?.isSelected = false
        selectedElement = null

        // Reset element gesture state.
        transformMode = TransformMode.NONE
        isMovingElement = false
        isDragging = false

        // Start every new crop session in Free Crop mode.
        cropAspectRatio = CropAspectRatio.FREE

        // Start with the complete image selected.
        initializeCropRect()

        // Activate crop mode.
        isCropMode = true

        Log.d(
            TAG,
            "Crop mode entered"
        )

        notifySelectionChanged()
        onCropModeChanged?.invoke(true)

        invalidate()
    }

    /**
     * Applies the current crop selection to the image.
     *
     * The crop rectangle is stored in original image coordinates, so the
     * bitmap is cropped directly using those coordinates. Existing editor
     * elements are kept and their positions are shifted by the crop origin.
     * Elements whose position falls outside the new image are removed.
     *
     * This does not call setImage(), because setImage() intentionally clears
     * all editor elements when a completely new image is loaded.
     */
    fun applyCrop() {

        val currentBitmap = bitmap
        val cropRect = cropRectImage

        if (currentBitmap == null) {
            Log.w(
                TAG,
                "Cannot apply crop. No image selected."
            )
            return
        }

        if (!isCropMode || cropRect == null) {
            Log.w(
                TAG,
                "Cannot apply crop. Crop mode is not active."
            )
            return
        }

        val left = cropRect.left
            .coerceIn(0f, currentBitmap.width.toFloat())
            .toInt()

        val top = cropRect.top
            .coerceIn(0f, currentBitmap.height.toFloat())
            .toInt()

        val right = cropRect.right
            .coerceIn(0f, currentBitmap.width.toFloat())
            .toInt()

        val bottom = cropRect.bottom
            .coerceIn(0f, currentBitmap.height.toFloat())
            .toInt()

        val cropWidth = right - left
        val cropHeight = bottom - top

        if (
            cropWidth <= 0 ||
            cropHeight <= 0
        ) {
            Log.w(
                TAG,
                "Cannot apply crop. Invalid crop size: " +
                        "${cropWidth}x${cropHeight}"
            )
            return
        }

        if (
            cropWidth < MIN_CROP_SIZE.toInt() ||
            cropHeight < MIN_CROP_SIZE.toInt()
        ) {
            Log.w(
                TAG,
                "Cannot apply crop. Crop is smaller than minimum size: " +
                        "${cropWidth}x${cropHeight}"
            )
            return
        }

        val isFullImage =
            left == 0 &&
                    top == 0 &&
                    right == currentBitmap.width &&
                    bottom == currentBitmap.height

        if (!isFullImage) {
            val croppedBitmap = Bitmap.createBitmap(
                currentBitmap,
                left,
                top,
                cropWidth,
                cropHeight
            )

            bitmap = croppedBitmap
        }

        // Move editor elements into the new image coordinate system.
        // Elements that do not intersect the crop area are removed.
        if (!isFullImage) {
            val cropBounds = RectF(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat()
            )

            val iterator = elements.iterator()

            while (iterator.hasNext()) {
                val element = iterator.next()
                val elementBounds = element.getBounds()

                if (!RectF.intersects(
                        cropBounds,
                        elementBounds
                    )
                ) {
                    element.isSelected = false

                    if (selectedElement === element) {
                        selectedElement = null
                    }

                    iterator.remove()
                    continue
                }

                element.moveBy(
                    -left.toFloat(),
                    -top.toFloat()
                )
            }
        }

        selectedElement = null
        transformMode = TransformMode.NONE
        isMovingElement = false
        isDragging = false
        activeCropHandle = CropHandle.NONE

        cropRectImage = null
        cropAspectRatio = CropAspectRatio.FREE
        isCropMode = false

        clearCropSessionSnapshot()

        resetTransform()

        notifySelectionChanged()
        onCropModeChanged?.invoke(false)

        Log.d(
            TAG,
            "Crop applied: ${cropWidth}x${cropHeight}, " +
                    "origin=($left,$top), " +
                    "elements=${elements.size}"
        )

        invalidate()
    }

    /**
     * Resets the active crop selection to the full current image while
     * keeping the editor in Crop Mode.
     *
     * Reset is intentionally different from Cancel:
     * - Reset keeps the current crop session active.
     * - Cancel restores the complete state from before Crop Mode started.
     */
    fun resetCrop() {

        if (!isCropMode) {
            Log.d(
                TAG,
                "Reset crop ignored: crop mode is not active"
            )
            return
        }

        val currentBitmap = bitmap

        if (currentBitmap == null) {
            Log.w(
                TAG,
                "Reset crop ignored: bitmap is missing"
            )
            return
        }

        cropAspectRatio = CropAspectRatio.FREE

        cropRectImage = RectF(
            0f,
            0f,
            currentBitmap.width.toFloat(),
            currentBitmap.height.toFloat()
        )

        activeCropHandle = CropHandle.NONE
        isDragging = false
        isMovingElement = false
        transformMode = TransformMode.NONE

        resetTransform()

        Log.d(
            TAG,
            "Crop reset to full image: " +
                    "${currentBitmap.width}x${currentBitmap.height}"
        )

        invalidate()
    }

    /**
     * Cancels the current crop session and restores the state captured when
     * crop mode was entered. This can be called at any time while crop mode
     * is active.
     */
    fun cancelCrop() {

        if (!isCropMode) {
            Log.d(
                TAG,
                "Cancel crop ignored: crop mode is not active"
            )
            return
        }

        val originalBitmap = cropOriginalBitmap
        val originalElements = cropOriginalElements

        if (originalBitmap == null || originalElements == null) {
            Log.w(
                TAG,
                "Cancel crop failed: crop session snapshot is missing"
            )

            exitCropModeWithoutRestore()
            return
        }

        bitmap = originalBitmap

        elements.clear()
        elements.addAll(originalElements)

        selectedElement = cropOriginalSelectedElement

        // Re-apply selection state exactly as it was before crop mode.
        elements.forEach { element ->
            element.isSelected = element === selectedElement
        }

        cropRectImage = null
        cropAspectRatio = CropAspectRatio.FREE
        isCropMode = false

        transformMode = TransformMode.NONE
        isMovingElement = false
        isDragging = false
        activeCropHandle = CropHandle.NONE

        clearCropSessionSnapshot()
        resetTransform()

        notifySelectionChanged()
        onCropModeChanged?.invoke(false)

        Log.d(
            TAG,
            "Crop cancelled. Original image restored: " +
                    "${originalBitmap.width}x${originalBitmap.height}, " +
                    "elements=${elements.size}"
        )

        invalidate()
    }

    /**
     * Keeps the existing public exit API but makes exiting crop mode safe: an
     * exit from an active crop session is treated as Cancel Crop.
     */
    fun exitCropMode() {
        cancelCrop()
    }

    /**
     * Restores only the crop interaction state when a snapshot is unavailable.
     */
    private fun exitCropModeWithoutRestore() {
        cropRectImage = null
        cropAspectRatio = CropAspectRatio.FREE
        isCropMode = false

        transformMode = TransformMode.NONE
        isMovingElement = false
        isDragging = false
        activeCropHandle = CropHandle.NONE

        clearCropSessionSnapshot()

        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        invalidate()
    }

    /**
     * Captures the editor state before crop mode changes anything.
     */
    private fun createCropSessionSnapshot() {
        cropOriginalBitmap = bitmap

        val selected = selectedElement
        val snapshots = elements.map { element ->
            when (element) {
                is TextElement -> element.copyForCropSession()
                else -> element
            }
        }

        cropOriginalElements = snapshots

        cropOriginalSelectedElement = when (selected) {
            is TextElement -> snapshots
                .firstOrNull {
                    it is TextElement &&
                            it.text == selected.text &&
                            it.getBounds() == selected.getBounds()
                }
            else -> null
        }

        // The selection object lookup above can be ambiguous for identical
        // text elements. Resolve by list index for the current editor model.
        if (selected != null) {
            val selectedIndex = elements.indexOf(selected)
            if (selectedIndex >= 0) {
                cropOriginalSelectedElement =
                    snapshots.getOrNull(selectedIndex)
            }
        }

        Log.d(
            TAG,
            "Crop session snapshot created: " +
                    "bitmap=${bitmap?.width}x${bitmap?.height}, " +
                    "elements=${snapshots.size}, " +
                    "selectedIndex=${elements.indexOf(selected)}"
        )
    }

    /** Clears the crop-session snapshot after Apply or Cancel. */
    private fun clearCropSessionSnapshot() {
        cropOriginalBitmap = null
        cropOriginalElements = null
        cropOriginalSelectedElement = null
    }

    /**
     * Returns true when crop mode is currently active.
     */
    fun isCropMode(): Boolean {

        return isCropMode
    }

    /**
     * Rotates the current crop session 90 degrees clockwise.
     *
     * The bitmap is physically rotated so the change becomes part of the
     * current crop session. The active crop rectangle and all editor
     * elements are transformed into the rotated image coordinate system.
     *
     * The crop aspect-ratio mode itself is preserved. For fixed aspect-ratio
     * modes the crop rectangle is normalized again against the rotated image.
     * Cancel Crop can still restore the exact state that existed before the
     * crop session started because the original bitmap/elements remain in the
     * crop-session snapshot.
     */
    fun rotateCrop90Degrees() {

        if (!isCropMode) {
            Log.d(
                TAG,
                "Rotate crop ignored: crop mode is not active"
            )
            return
        }

        val currentBitmap = bitmap
        val currentCropRect = cropRectImage

        if (currentBitmap == null || currentCropRect == null) {
            Log.w(
                TAG,
                "Rotate crop ignored: bitmap or crop rectangle is missing"
            )
            return
        }

        val oldHeight = currentBitmap.height.toFloat()

        try {
            // Android's positive 90 degree rotation is clockwise.
            val rotationMatrix = Matrix().apply {
                postRotate(90f)
            }

            val rotatedBitmap = Bitmap.createBitmap(
                currentBitmap,
                0,
                0,
                currentBitmap.width,
                currentBitmap.height,
                rotationMatrix,
                true
            )

            // -------------------------------------------------------------
            // ROTATE CROP RECTANGLE
            // -------------------------------------------------------------
            // For a clockwise rotation: (x, y) -> (H - y, x).
            val rotatedCropLeft =
                oldHeight - currentCropRect.bottom
            val rotatedCropTop =
                currentCropRect.left
            val rotatedCropRight =
                oldHeight - currentCropRect.top
            val rotatedCropBottom =
                currentCropRect.right

            // -------------------------------------------------------------
            // ROTATE EDITOR ELEMENTS
            // -------------------------------------------------------------
            // TextElement position is its local/world origin. Move that origin
            // using the same image-coordinate transform and add 90 degrees to
            // its existing rotation so the text remains aligned with the image.
            elements.forEach { element ->
                when (element) {
                    is TextElement -> {
                        val oldX = element.position.x
                        val oldY = element.position.y

                        element.position.x = oldHeight - oldY
                        element.position.y = oldX

                        element.rotation = normalizeRotation(
                            element.rotation + 90f
                        )
                    }

                    else -> Unit
                }
            }

            bitmap = rotatedBitmap

            currentCropRect.set(
                rotatedCropLeft,
                rotatedCropTop,
                rotatedCropRight,
                rotatedCropBottom
            )

            // Fixed aspect-ratio modes stay selected after rotation.
            // Re-normalize the selection using the rotated bitmap dimensions.
            when (cropAspectRatio) {
                CropAspectRatio.FREE -> Unit

                CropAspectRatio.ONE_TO_ONE -> {
                    normalizeCropToOneToOne()
                }

                CropAspectRatio.FOUR_TO_THREE -> {
                    normalizeCropToAspectRatio(
                        aspectRatio = 4f / 3f
                    )
                }

                CropAspectRatio.SIXTEEN_TO_NINE -> {
                    normalizeCropToAspectRatio(
                        aspectRatio = 16f / 9f
                    )
                }

                CropAspectRatio.ORIGINAL_RATIO -> {
                    normalizeCropToAspectRatio(
                        aspectRatio =
                            rotatedBitmap.width.toFloat() /
                                    rotatedBitmap.height.toFloat()
                    )
                }
            }

            // A rotation changes the image dimensions, so any previous image
            // transform may no longer be appropriate. Keep the crop session
            // stable by fitting the rotated image back into the editor.
            resetTransform()

            activeCropHandle = CropHandle.NONE
            isDragging = false
            isMovingElement = false
            transformMode = TransformMode.NONE

            Log.d(
                TAG,
                "Crop rotated 90 degrees clockwise: " +
                        "${currentBitmap.width}x${currentBitmap.height} -> " +
                        "${rotatedBitmap.width}x${rotatedBitmap.height}, " +
                        "cropAspect=$cropAspectRatio, " +
                        "cropRect=$currentCropRect, " +
                        "elements=${elements.size}"
            )

            invalidate()

        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Failed to rotate crop 90 degrees",
                exception
            )
        }
    }

    /**
     * Normalizes an element rotation to the 0..360 degree range.
     */
    private fun normalizeRotation(rotation: Float): Float {
        var normalized = rotation

        while (normalized < 0f) {
            normalized += 360f
        }

        while (normalized >= 360f) {
            normalized -= 360f
        }

        return normalized
    }

    /**
     * Selects Free Crop mode.
     *
     * In Free Crop mode, width and height are independent and each crop
     * corner can be moved without maintaining an aspect ratio.
     */
    fun setFreeCropMode() {

        cropAspectRatio = CropAspectRatio.FREE

        Log.d(
            TAG,
            "Free Crop mode selected"
        )

        invalidate()
    }

    /**
     * Returns true when Free Crop mode is active.
     */
    fun isFreeCropMode(): Boolean {

        return cropAspectRatio == CropAspectRatio.FREE
    }

    /**
     * Selects 1:1 (square) crop mode.
     */
    fun setOneToOneCropMode() {

        cropAspectRatio = CropAspectRatio.ONE_TO_ONE
        normalizeCropToOneToOne()

        Log.d(TAG, "1:1 Crop mode selected")
        invalidate()
    }

    /**
     * Returns true when 1:1 crop mode is active.
     */
    fun isOneToOneCropMode(): Boolean {

        return cropAspectRatio == CropAspectRatio.ONE_TO_ONE
    }

    /**
     * Enables 4:3 aspect-ratio crop mode.
     */
    fun setFourToThreeCropMode() {
        cropAspectRatio = CropAspectRatio.FOUR_TO_THREE

        normalizeCropToAspectRatio(
            aspectRatio = 4f / 3f
        )

        Log.d(
            TAG,
            "4:3 Crop mode selected"
        )

        invalidate()
    }

    /**
     * Returns true when 4:3 crop mode is active.
     */
    fun isFourToThreeCropMode(): Boolean {
        return cropAspectRatio == CropAspectRatio.FOUR_TO_THREE
    }

    /**
     * Enables 16:9 aspect-ratio crop mode.
     */
    fun setSixteenToNineCropMode() {
        cropAspectRatio = CropAspectRatio.SIXTEEN_TO_NINE

        normalizeCropToAspectRatio(
            aspectRatio = 16f / 9f
        )

        Log.d(
            TAG,
            "16:9 Crop mode selected"
        )

        invalidate()
    }

    /**
     * Returns true when 16:9 crop mode is active.
     */
    fun isSixteenToNineCropMode(): Boolean {
        return cropAspectRatio == CropAspectRatio.SIXTEEN_TO_NINE
    }

    /**
     * Enables Original Ratio crop mode.
     *
     * The crop selection keeps the same aspect ratio as the original image.
     */
    fun setOriginalRatioCropMode() {
        val currentBitmap = bitmap ?: return

        cropAspectRatio = CropAspectRatio.ORIGINAL_RATIO

        normalizeCropToAspectRatio(
            aspectRatio =
                currentBitmap.width.toFloat() /
                        currentBitmap.height.toFloat()
        )

        Log.d(
            TAG,
            "Original Ratio Crop mode selected"
        )

        invalidate()
    }

    /**
     * Returns true when Original Ratio crop mode is active.
     */
    fun isOriginalRatioCropMode(): Boolean {
        return cropAspectRatio == CropAspectRatio.ORIGINAL_RATIO
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

        if (isCropMode) {
            drawCropSelection(canvas)
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
    // CROP TOUCH
    // =========================================================================

    /**
     * Owns all touch events while crop mode is active.
     *
     * Phase 5.3 added corner resizing.
     * Phase 5.4 adds moving the complete crop rectangle.
     * Aspect-ratio constraints are intentionally left for the following
     * phases.
     */
    private fun handleCropTouch(event: MotionEvent) {

        val cropRect = cropRectImage
        val currentBitmap = bitmap

        if (cropRect == null || currentBitmap == null) {
            return
        }

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                lastTouchX = event.x
                lastTouchY = event.y

                isDragging = true
                isMovingElement = false
                transformMode = TransformMode.NONE

                // Corner handles always get priority.
                activeCropHandle =
                    findCropHandle(
                        event.x,
                        event.y
                    )

                // If no corner was touched, check the actual visible crop
                // rectangle in SCREEN coordinates. This is more reliable for
                // moving because the user is interacting with what they see.
                if (activeCropHandle == CropHandle.NONE) {

                    val cropRectOnScreen =
                        getCropRectOnScreen()

                    if (
                        cropRectOnScreen != null &&
                        cropRectOnScreen.contains(
                            event.x,
                            event.y
                        )
                    ) {
                        activeCropHandle = CropHandle.MOVE
                    }
                }

                Log.d(
                    TAG,
                    "Crop touch started: " +
                            "x=${event.x}, " +
                            "y=${event.y}, " +
                            "handle=$activeCropHandle"
                )
            }

            MotionEvent.ACTION_MOVE -> {

                when (activeCropHandle) {

                    CropHandle.MOVE -> {
                        moveCropArea(
                            previousScreenX = lastTouchX,
                            previousScreenY = lastTouchY,
                            currentScreenX = event.x,
                            currentScreenY = event.y
                        )
                    }

                    CropHandle.TOP_LEFT,
                    CropHandle.TOP_RIGHT,
                    CropHandle.BOTTOM_LEFT,
                    CropHandle.BOTTOM_RIGHT -> {

                        val imagePoint =
                            screenToImage(
                                event.x,
                                event.y
                            ) ?: return

                        resizeCropFromHandle(
                            imagePoint.x,
                            imagePoint.y,
                            activeCropHandle
                        )
                    }

                    CropHandle.NONE -> {
                        return
                    }
                }

                lastTouchX = event.x
                lastTouchY = event.y

                invalidate()
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {

                isDragging = false
                isMovingElement = false
                transformMode = TransformMode.NONE

                Log.d(
                    TAG,
                    "Crop touch ended. " +
                            "handle=$activeCropHandle, " +
                            "cropRect=$cropRectImage"
                )

                activeCropHandle = CropHandle.NONE
            }
        }
    }

    /**
     * Finds which crop corner is being touched.
     *
     * Hit testing is performed in screen coordinates because the visible
     * handles are drawn there and the user interacts with the screen.
     */
    private fun findCropHandle(
        screenX: Float,
        screenY: Float
    ): CropHandle {

        val cropRect =
            getCropRectOnScreen()
                ?: return CropHandle.NONE

        val touchRadius =
            CROP_HANDLE_TOUCH_RADIUS

        if (
            hypot(
                screenX - cropRect.left,
                screenY - cropRect.top
            ) <= touchRadius
        ) {
            return CropHandle.TOP_LEFT
        }

        if (
            hypot(
                screenX - cropRect.right,
                screenY - cropRect.top
            ) <= touchRadius
        ) {
            return CropHandle.TOP_RIGHT
        }

        if (
            hypot(
                screenX - cropRect.left,
                screenY - cropRect.bottom
            ) <= touchRadius
        ) {
            return CropHandle.BOTTOM_LEFT
        }

        if (
            hypot(
                screenX - cropRect.right,
                screenY - cropRect.bottom
            ) <= touchRadius
        ) {
            return CropHandle.BOTTOM_RIGHT
        }

        return CropHandle.NONE
    }

    /**
     * Moves the complete crop selection without changing its size.
     *
     * Movement is calculated in original image coordinates so it remains
     * correct regardless of the current image scale or pan.
     */
    private fun moveCropArea(
        previousScreenX: Float,
        previousScreenY: Float,
        currentScreenX: Float,
        currentScreenY: Float
    ) {

        val cropRect =
            cropRectImage
                ?: return

        val currentBitmap =
            bitmap
                ?: return

        val previousImagePoint =
            screenToImage(
                previousScreenX,
                previousScreenY
            )

        val currentImagePoint =
            screenToImage(
                currentScreenX,
                currentScreenY
            )

        if (
            previousImagePoint == null ||
            currentImagePoint == null
        ) {
            Log.d(
                TAG,
                "Crop move ignored: unable to convert touch to image coordinates"
            )
            return
        }

        // Convert the user's screen movement into ORIGINAL IMAGE movement.
        // This keeps crop movement correct regardless of image scaling.
        val deltaX =
            currentImagePoint.x -
                    previousImagePoint.x

        val deltaY =
            currentImagePoint.y -
                    previousImagePoint.y

        val imageWidth =
            currentBitmap.width.toFloat()

        val imageHeight =
            currentBitmap.height.toFloat()

        val cropWidth =
            cropRect.width()

        val cropHeight =
            cropRect.height()

        val maxLeft =
            (imageWidth - cropWidth)
                .coerceAtLeast(0f)

        val maxTop =
            (imageHeight - cropHeight)
                .coerceAtLeast(0f)

        val newLeft =
            (cropRect.left + deltaX)
                .coerceIn(
                    0f,
                    maxLeft
                )

        val newTop =
            (cropRect.top + deltaY)
                .coerceIn(
                    0f,
                    maxTop
                )

        cropRect.offsetTo(
            newLeft,
            newTop
        )

        Log.d(
            TAG,
            "Crop area moved: " +
                    "deltaX=$deltaX " +
                    "deltaY=$deltaY " +
                    "rect=$cropRect"
        )
    }


    /**
     * Resizes the crop rectangle from one corner.
     *
     * The opposite corner remains fixed.
     * The rectangle is always constrained to the original bitmap bounds and
     * cannot become smaller than MIN_CROP_SIZE.
     */
    private fun resizeCropFromHandle(
        imageX: Float,
        imageY: Float,
        handle: CropHandle
    ) {

        val cropRect = cropRectImage ?: return
        val currentBitmap = bitmap ?: return

        val imageWidth = currentBitmap.width.toFloat()
        val imageHeight = currentBitmap.height.toFloat()

        val minSize = MIN_CROP_SIZE.toFloat()

        when (cropAspectRatio) {

            CropAspectRatio.FREE -> {
                resizeCropFree(
                    imageX = imageX,
                    imageY = imageY,
                    handle = handle,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    minSize = minSize
                )
            }

            CropAspectRatio.ONE_TO_ONE -> {
                resizeCropWithAspectRatio(
                    imageX = imageX,
                    imageY = imageY,
                    handle = handle,
                    aspectRatio = 1f,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    minSize = minSize
                )
            }

            CropAspectRatio.FOUR_TO_THREE -> {
                resizeCropWithAspectRatio(
                    imageX = imageX,
                    imageY = imageY,
                    handle = handle,
                    aspectRatio = 4f / 3f,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    minSize = minSize
                )
            }

            CropAspectRatio.SIXTEEN_TO_NINE -> {
                resizeCropWithAspectRatio(
                    imageX = imageX,
                    imageY = imageY,
                    handle = handle,
                    aspectRatio = 16f / 9f,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    minSize = minSize
                )
            }

            CropAspectRatio.ORIGINAL_RATIO -> {
                resizeCropWithAspectRatio(
                    imageX = imageX,
                    imageY = imageY,
                    handle = handle,
                    aspectRatio = imageWidth / imageHeight,
                    imageWidth = imageWidth,
                    imageHeight = imageHeight,
                    minSize = minSize
                )
            }
        }

        Log.d(
            TAG,
            "Crop resized: handle=$handle rect=$cropRect"
        )
    }

    private fun resizeCropFree(
        imageX: Float,
        imageY: Float,
        handle: CropHandle,
        imageWidth: Float,
        imageHeight: Float,
        minSize: Float
    ) {

        val cropRect = cropRectImage ?: return

        when (handle) {

            CropHandle.TOP_LEFT -> {
                cropRect.left = imageX.coerceIn(
                    0f,
                    cropRect.right - minSize
                )
                cropRect.top = imageY.coerceIn(
                    0f,
                    cropRect.bottom - minSize
                )
            }

            CropHandle.TOP_RIGHT -> {
                cropRect.right = imageX.coerceIn(
                    cropRect.left + minSize,
                    imageWidth
                )
                cropRect.top = imageY.coerceIn(
                    0f,
                    cropRect.bottom - minSize
                )
            }

            CropHandle.BOTTOM_LEFT -> {
                cropRect.left = imageX.coerceIn(
                    0f,
                    cropRect.right - minSize
                )
                cropRect.bottom = imageY.coerceIn(
                    cropRect.top + minSize,
                    imageHeight
                )
            }

            CropHandle.BOTTOM_RIGHT -> {
                cropRect.right = imageX.coerceIn(
                    cropRect.left + minSize,
                    imageWidth
                )
                cropRect.bottom = imageY.coerceIn(
                    cropRect.top + minSize,
                    imageHeight
                )
            }

            CropHandle.MOVE,
            CropHandle.NONE -> Unit
        }
    }

    /**
     * Resizes the crop rectangle while keeping width / height at
     * the requested aspect ratio.
     *
     * aspectRatio = width / height.
     */
    private fun resizeCropWithAspectRatio(
        imageX: Float,
        imageY: Float,
        handle: CropHandle,
        aspectRatio: Float,
        imageWidth: Float,
        imageHeight: Float,
        minSize: Float
    ) {

        val cropRect = cropRectImage ?: return

        if (aspectRatio <= 0f) {
            return
        }

        val minWidth = max(
            minSize,
            minSize * aspectRatio
        )

        val minHeight = max(
            minSize,
            minSize / aspectRatio
        )

        when (handle) {

            CropHandle.TOP_LEFT -> {

                val anchorX = cropRect.right
                val anchorY = cropRect.bottom

                val requestedWidth = max(
                    anchorX - imageX,
                    (anchorY - imageY) * aspectRatio
                )

                val maxWidth = min(
                    anchorX,
                    anchorY * aspectRatio
                )

                val width = requestedWidth.coerceIn(
                    minWidth,
                    maxWidth.coerceAtLeast(minWidth)
                )

                val height = width / aspectRatio

                cropRect.set(
                    anchorX - width,
                    anchorY - height,
                    anchorX,
                    anchorY
                )
            }

            CropHandle.TOP_RIGHT -> {

                val anchorX = cropRect.left
                val anchorY = cropRect.bottom

                val requestedWidth = max(
                    imageX - anchorX,
                    (anchorY - imageY) * aspectRatio
                )

                val maxWidth = min(
                    imageWidth - anchorX,
                    anchorY * aspectRatio
                )

                val width = requestedWidth.coerceIn(
                    minWidth,
                    maxWidth.coerceAtLeast(minWidth)
                )

                val height = width / aspectRatio

                cropRect.set(
                    anchorX,
                    anchorY - height,
                    anchorX + width,
                    anchorY
                )
            }

            CropHandle.BOTTOM_LEFT -> {

                val anchorX = cropRect.right
                val anchorY = cropRect.top

                val requestedWidth = max(
                    anchorX - imageX,
                    (imageY - anchorY) * aspectRatio
                )

                val maxWidth = min(
                    anchorX,
                    (imageHeight - anchorY) * aspectRatio
                )

                val width = requestedWidth.coerceIn(
                    minWidth,
                    maxWidth.coerceAtLeast(minWidth)
                )

                val height = width / aspectRatio

                cropRect.set(
                    anchorX - width,
                    anchorY,
                    anchorX,
                    anchorY + height
                )
            }

            CropHandle.BOTTOM_RIGHT -> {

                val anchorX = cropRect.left
                val anchorY = cropRect.top

                val requestedWidth = max(
                    imageX - anchorX,
                    (imageY - anchorY) * aspectRatio
                )

                val maxWidth = min(
                    imageWidth - anchorX,
                    (imageHeight - anchorY) * aspectRatio
                )

                val width = requestedWidth.coerceIn(
                    minWidth,
                    maxWidth.coerceAtLeast(minWidth)
                )

                val height = width / aspectRatio

                cropRect.set(
                    anchorX,
                    anchorY,
                    anchorX + width,
                    anchorY + height
                )
            }

            CropHandle.MOVE,
            CropHandle.NONE -> Unit
        }
    }

    /**
     * Changes the current crop rectangle to the requested aspect ratio
     * while preserving its center as much as possible.
     */
    private fun normalizeCropToAspectRatio(
        aspectRatio: Float
    ) {

        val cropRect = cropRectImage ?: return
        val currentBitmap = bitmap ?: return

        if (aspectRatio <= 0f) {
            return
        }

        val imageWidth = currentBitmap.width.toFloat()
        val imageHeight = currentBitmap.height.toFloat()

        var width = cropRect.width()
        var height = cropRect.height()

        if (width <= 0f || height <= 0f) {
            return
        }

        if (width / height > aspectRatio) {
            width = height * aspectRatio
        } else {
            height = width / aspectRatio
        }

        if (width > imageWidth) {
            width = imageWidth
            height = width / aspectRatio
        }

        if (height > imageHeight) {
            height = imageHeight
            width = height * aspectRatio
        }

        val left = (
                cropRect.centerX() - width / 2f
                ).coerceIn(
                0f,
                (imageWidth - width).coerceAtLeast(0f)
            )

        val top = (
                cropRect.centerY() - height / 2f
                ).coerceIn(
                0f,
                (imageHeight - height).coerceAtLeast(0f)
            )

        cropRect.set(
            left,
            top,
            left + width,
            top + height
        )

        Log.d(
            TAG,
            "Crop normalized to ratio=$aspectRatio rect=$cropRect"
        )
    }

    /**
     * Keeps compatibility with the existing 1:1 crop implementation.
     */
    private fun normalizeCropToOneToOne() {
        normalizeCropToAspectRatio(1f)
    }


    /**
     * Resizes a crop corner while keeping width and height equal.
     * The opposite corner remains fixed.
     */
    private fun resizeCropOneToOne(
        imageX: Float,
        imageY: Float,
        handle: CropHandle,
        minCropSize: Float,
        imageWidth: Float,
        imageHeight: Float
    ) {

        val cropRect = cropRectImage ?: return

        when (handle) {

            CropHandle.TOP_LEFT -> {
                val anchorX = cropRect.right
                val anchorY = cropRect.bottom
                val maxSize = min(anchorX, anchorY)
                val requestedSize = max(abs(anchorX - imageX), abs(anchorY - imageY))
                val size = requestedSize.coerceIn(minCropSize, maxSize)
                cropRect.set(anchorX - size, anchorY - size, anchorX, anchorY)
            }

            CropHandle.TOP_RIGHT -> {
                val anchorX = cropRect.left
                val anchorY = cropRect.bottom
                val maxSize = min(imageWidth - anchorX, anchorY)
                val requestedSize = max(abs(imageX - anchorX), abs(anchorY - imageY))
                val size = requestedSize.coerceIn(minCropSize, maxSize)
                cropRect.set(anchorX, anchorY - size, anchorX + size, anchorY)
            }

            CropHandle.BOTTOM_LEFT -> {
                val anchorX = cropRect.right
                val anchorY = cropRect.top
                val maxSize = min(anchorX, imageHeight - anchorY)
                val requestedSize = max(abs(anchorX - imageX), abs(imageY - anchorY))
                val size = requestedSize.coerceIn(minCropSize, maxSize)
                cropRect.set(anchorX - size, anchorY, anchorX, anchorY + size)
            }

            CropHandle.BOTTOM_RIGHT -> {
                val anchorX = cropRect.left
                val anchorY = cropRect.top
                val maxSize = min(imageWidth - anchorX, imageHeight - anchorY)
                val requestedSize = max(abs(imageX - anchorX), abs(imageY - anchorY))
                val size = requestedSize.coerceIn(minCropSize, maxSize)
                cropRect.set(anchorX, anchorY, anchorX + size, anchorY + size)
            }

            CropHandle.MOVE,
            CropHandle.NONE -> Unit
        }
    }

    // =========================================================================
    // TOUCH
    // =========================================================================

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        if (isCropMode) {
            handleCropTouch(event)
            return true
        }

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

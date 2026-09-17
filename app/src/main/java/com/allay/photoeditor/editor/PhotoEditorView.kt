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
import com.allay.photoeditor.model.AdjustmentState
import com.allay.photoeditor.editor.filter.FilterController
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.FilterType
import com.allay.photoeditor.model.TextElement
import com.allay.photoeditor.model.ShapeElement
import com.allay.photoeditor.model.ShapeType
import com.allay.photoeditor.editor.shape.ShapeController
import com.allay.photoeditor.editor.adjustment.AdjustmentController
import com.allay.photoeditor.editor.crop.CropController
import com.allay.photoeditor.editor.drawing.EditorRenderer
import com.allay.photoeditor.editor.transform.TransformController
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
private typealias CropAspectRatio = CropController.AspectRatio
private typealias CropHandle = CropController.Handle
class PhotoEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    companion object {
        private const val TAG = "PhotoEditor"
        // IMAGE SCALE
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 5.0f
        // ELEMENT SCALE
        private const val MIN_ELEMENT_SCALE = 0.2f
        private const val MAX_ELEMENT_SCALE = 5.0f
        // TRANSFORM HANDLES
        private const val HANDLE_TOUCH_RADIUS = 40f
        private const val ROTATION_HANDLE_DISTANCE = 70f
        // Shape delete handle is placed outside the top-right corner.
        private const val SHAPE_DELETE_HANDLE_DISTANCE = 56f
        // Larger visible target for the floating delete action.
        private const val SHAPE_DELETE_BUTTON_RADIUS = 25f
        private const val SHAPE_DELETE_BUTTON_TOUCH_RADIUS = 36f
        // Text uses the same prominent floating delete action as shapes.
        private const val TEXT_DELETE_HANDLE_DISTANCE = 56f
        private const val TEXT_DELETE_BUTTON_RADIUS = 25f
        private const val TEXT_DELETE_BUTTON_TOUCH_RADIUS = 36f
        // CROP
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
    private val editorRenderer = EditorRenderer(
        bitmapPaint = bitmapPaint,
        cropOverlayPaint = cropOverlayPaint,
        cropBorderPaint = cropBorderPaint,
        cropGridPaint = cropGridPaint,
        cropHandlePaint = cropHandlePaint,
        cropHandleLength = CROP_HANDLE_LENGTH
    )
    private var bitmap: Bitmap? = null
    private var scaleFactor = MIN_SCALE
    private var translationX = 0f
    private var translationY = 0f
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
    private enum class TransformMode {
        NONE,
        ROTATE,
        RESIZE
    }
    private var transformMode = TransformMode.NONE
    /** Clears only transient touch/element-transform state. */
    private fun resetGestureState() {
        lastTouchX = 0f
        lastTouchY = 0f
        isDragging = false
        isMovingElement = false
        transformMode = TransformMode.NONE
        activeCropHandle = CropHandle.NONE
    }
    /** Resets only the element gesture flags without affecting crop handle state. */
    private fun resetElementGestureState() {
        isDragging = false
        isMovingElement = false
        transformMode = TransformMode.NONE
    }
    /** Owns the crop interaction state while PhotoEditorView keeps the existing behavior. */
    private val cropController = CropController(
        getBitmap = { bitmap },
        getCropRectOnScreen = ::getCropRectOnScreen,
        screenToImage = ::screenToImage,
        captureSessionElements = ::createCropSessionElementsSnapshot,
        captureSelectedIndex = ::getSelectedElementIndexForCropSession,
        invalidate = ::invalidate,
        onModeChanged = { active -> onCropModeChanged?.invoke(active) }
    )
    private var cropModeActive: Boolean
        get() = cropController.isActive
        set(value) = cropController.setActiveState(value)
    /** Crop selection stored in original image coordinates. */
    private var cropRectImage: RectF?
        get() = cropController.currentCropRect
        set(value) = cropController.setCropRectState(value)
    /** Current crop sizing mode. Free Crop is the default. */
    private var cropAspectRatio: CropAspectRatio
        get() = cropController.currentAspectRatio
        set(value) = cropController.setAspectRatioState(value)
    private var activeCropHandle: CropHandle
        get() = cropController.currentHandle
        set(value) = cropController.setActiveHandleState(value)
    /** Owns the temporary transform session. */
    private val transformController = TransformController(
        getBitmap = { bitmap },
        captureElements = {
            elements.map { element ->
                when (element) {
                    is TextElement -> element.copyForCropSession()
                    else -> element
                }
            }
        },
        getSelectedElementIndex = { selectedElement?.let(elements::indexOf) ?: -1 },
        getScaleFactor = { scaleFactor },
        getTranslationX = { translationX },
        getTranslationY = { translationY },
        resetGestureState = ::resetGestureState,
        onModeChanged = { active -> onRotationModeChanged?.invoke(active) },
        invalidate = ::invalidate
    )
    private val rotationModeActive: Boolean
        get() = transformController.isActive
    /** Owns the temporary filter-selection session. */
    private val filterController = FilterController(
        getCurrentBitmap = { bitmap },
        setCurrentBitmap = { bitmap = it },
        canEnter = {
            !cropModeActive &&
                    !rotationModeActive &&
                    !adjustmentModeActive
        },
        resetGestureState = ::resetGestureState,
        onModeChanged = { active -> onFilterModeChanged?.invoke(active) },
        invalidate = ::invalidate
    )
    private val filterModeActive: Boolean
        get() = filterController.isActive
    private val filterPreviewBitmap: Bitmap?
        get() = filterController.currentPreviewBitmap
    fun enterFilterMode() {
        filterController.enter()
    }
    fun isFilterMode(): Boolean = filterController.isActive
    /** Returns the filter currently selected in the temporary session. */
    fun getFilterType(): FilterType = filterController.currentFilterType
    /** Applies a filter to the temporary session and refreshes the preview. */
    fun setFilter(type: FilterType) {
        filterController.selectFilter(type)
    }
    /** Commits the current filter preview to the editor bitmap. */
    fun applyFilter() {
        filterController.apply()
    }
    /** Discards the temporary filter preview and restores the session source. */
    fun cancelFilterMode() {
        filterController.cancel()
    }
    /** Owns creation of editor shapes while PhotoEditorView keeps the common
     * element selection, movement and transform pipeline. */
    private val shapeController = ShapeController()
    private var initialRotation = 0f
    private var initialRotationAngle = 0f
    private var initialElementScale = 1f
    private var initialResizeDistance = 1f
    /**
     * Creates and adds a shape at the center of the current image.
     *
     * The new shape is automatically selected through addElement(), so the
     * existing selection callback and Delete button continue to work.
     */
    fun addShape(shapeType: ShapeType): Boolean {
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            Log.d(TAG, "Cannot add shape. No image selected.")
            return false
        }
        if (cropModeActive || rotationModeActive || adjustmentModeActive || filterModeActive) {
            Log.d(TAG, "Cannot add shape. Editor mode is active.")
            return false
        }
        val position = PointF(currentBitmap.width / 2f, currentBitmap.height / 2f)
        val shape: ShapeElement = shapeController.createShape(
            shapeType = shapeType,
            position = position
        )
        addElement(shape)
        Log.d(TAG, "Shape added: $shapeType")
        return true
    }
    /**
     * Elements are stored in ORIGINAL IMAGE coordinates.
     */
    private val elements = mutableListOf<EditorElement>()
    private var selectedElement: EditorElement? = null
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
    /** Called when temporary rotation mode starts or ends. */
    var onRotationModeChanged:
            ((Boolean) -> Unit)? = null
    /** Called when temporary adjustment mode starts or ends. */
    var onAdjustmentModeChanged:
            ((Boolean) -> Unit)? = null
    /** Called when temporary filter-selection mode starts or ends. */
    var onFilterModeChanged:
            ((Boolean) -> Unit)? = null
    /** Owns temporary adjustment state and background preview processing. */
    private val adjustmentController = AdjustmentController(
        getSourceBitmap = { bitmap },
        setCommittedBitmap = { bitmap = it },
        resetGestureState = ::resetGestureState,
        onModeChanged = { active -> onAdjustmentModeChanged?.invoke(active) },
        invalidate = ::invalidate
    )
    private val adjustmentModeActive: Boolean
        get() = adjustmentController.isActive
    private val imageToScreenMatrix = Matrix()
    private val screenToImageMatrix = Matrix()
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
                        cropModeActive
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
                    if (cropModeActive) {
                        Log.d(TAG, "Double tap ignored: crop mode active")
                        return true
                    }
                    Log.d( TAG, "Double tap detected at " + "x=${e.x}, y=${e.y}" )
                    /*
                     * Handle double tap only on actual elements.
                     */
                    val imagePoint = screenToImage(
                        e.x,
                        e.y
                    )
                    if (imagePoint == null) {
                        Log.d( TAG, "Double tap ignored: no image" )
                        return true
                    }
                    val tappedElement = findElementAt(
                        imagePoint.x,
                        imagePoint.y
                    )
                    if (tappedElement == null) {
                        Log.d( TAG, "Double tap ignored: no element" )
                        return true
                    }
                    Log.d( TAG, "Double tapped element: $tappedElement" )
                    /*
                     * Only TextElement supports text editing.
                     */
                    if (tappedElement is TextElement) {
                        selectElement(
                            tappedElement
                        )
                        Log.d( TAG, "Opening text editor for: " + tappedElement.text )
                        onEditTextRequested?.invoke(
                            tappedElement
                        )
                    }
                    return true
                }
            }
        )
    init {
        setBackgroundColor(
            Color.BLACK
        )
        isClickable = true
        isFocusable = true
    }
    fun setImage(
        bitmap: Bitmap
    ) {
        this.bitmap = bitmap
        resetTransform()
        elements.clear()
        selectedElement = null
        transformMode = TransformMode.NONE
        cropModeActive = false
        cropRectImage = null
        cropController.clearSession()
        transformController.clearSession()
        filterController.clear()
        adjustmentController.clearSession()
        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        Log.d( TAG, "Image set: ${bitmap.width} x ${bitmap.height}" )
        invalidate()
    }
    fun clearImage() {
        bitmap = null
        elements.clear()
        selectedElement = null
        resetTransform()
        transformMode = TransformMode.NONE
        cropModeActive = false
        activeCropHandle = CropHandle.NONE
        cropController.clearSession()
        transformController.clearSession()
        adjustmentController.clearSession()
        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        invalidate()
    }
    fun getCurrentBitmap(): Bitmap? = bitmap
    /**
     * Flips the current image horizontally while preserving its dimensions.
     *
     * Editor elements are stored in image coordinates, so their horizontal
     * position must be mirrored together with the bitmap. Text rotation and
     * horizontal alignment are mirrored as well so the text remains visually
     * aligned with the flipped image without changing its size or styling.
     *
     * Horizontal flip is intentionally disabled while Crop Mode is active.
     * Crop Mode owns a temporary bitmap/crop coordinate system and has its own
     * Cancel Crop snapshot; allowing an image transform here would make that
     * session state unnecessarily unsafe.
     */
    fun flipHorizontal() {
        if (cropModeActive) {
            Log.d(TAG, "Horizontal flip ignored: crop mode is active")
            return
        }
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            Log.d( TAG, "Horizontal flip ignored: no image selected" )
            return
        }
        val imageWidth = currentBitmap.width.toFloat()
        if (currentBitmap.width <= 0 || currentBitmap.height <= 0) {
            Log.w( TAG, "Horizontal flip ignored: invalid bitmap dimensions" )
            return
        }
        beginTransformSession()
        try {
            // Draw into a new bitmap using a canvas centered on the image.
            // This keeps the exact width/height and avoids changing the
            // existing image-to-screen matrix architecture.
            val flippedBitmap = Bitmap.createBitmap(
                currentBitmap.width,
                currentBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val flipCanvas = Canvas(flippedBitmap)
            flipCanvas.save()
            flipCanvas.scale(
                -1f,
                1f,
                imageWidth / 2f,
                currentBitmap.height / 2f
            )
            flipCanvas.drawBitmap(
                currentBitmap,
                0f,
                0f,
                bitmapPaint
            )
            flipCanvas.restore()
            // Mirror every editor element in the same image coordinate space.
            // TextElement is currently the concrete editor element in the
            // project, so keep this localized instead of changing the
            // EditorElement contract.
            elements.forEach { element ->
                when (element) {
                    is TextElement -> {
                        transformTextForHorizontalFlip(
                            textElement = element,
                            imageWidth = imageWidth
                        )
                    }
                    else -> Unit
                }
            }
            bitmap = flippedBitmap
            // The bitmap dimensions did not change, so the existing zoom and
            // pan transform remains valid. Clear only transient gesture state.
            resetElementGestureState()
            Log.d(
                TAG,
                "Horizontal flip applied: " +
                        "${currentBitmap.width}x${currentBitmap.height}, " +
                        "elements=${elements.size}, " +
                        "scaleFactor=$scaleFactor, " +
                        "translation=($translationX,$translationY)"
            )
            invalidate()
        } catch (exception: Exception) {
            Log.e( TAG, "Failed to flip image horizontally", exception )
        }
    }
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
     * Draws the crop overlay. Geometry is calculated here; visual rendering is
     * delegated to EditorRenderer.
     */
    private fun drawCropSelection(canvas: Canvas) {
        val cropRect = getCropRectOnScreen() ?: return
        val currentBitmap = bitmap ?: return
        editorRenderer.drawCropOverlay(
            canvas = canvas,
            cropRect = cropRect,
            viewWidth = width.toFloat(),
            viewHeight = height.toFloat(),
            bitmap = currentBitmap,
            imageToScreenMatrix = imageToScreenMatrix,
            elements = elements
        )
    }
    /**
     * Enters crop mode.
     *
     * Phase 5.1 only opens the mode. Crop rectangle interactions
     * are implemented in the following Phase 5 steps.
     */
    fun enterCropMode() {
        if (rotationModeActive) {
            Log.d(TAG, "Cannot enter crop mode while rotation mode is active")
            return
        }
        if (bitmap == null) {
            Log.d( TAG, "Cannot enter crop mode. No image selected." )
            return
        }
        // Capture the complete editor state before changing selection state.
        // This snapshot is used by Cancel Crop.
        cropController.beginSession()
        // Deselect any active editor element while crop mode is active.
        selectedElement?.isSelected = false
        selectedElement = null
        // Reset element gesture state.
        resetElementGestureState()
        // Start every new crop session in Free Crop mode.
        cropAspectRatio = CropAspectRatio.FREE
        // Start with the complete image selected.
        initializeCropRect()
        // Activate crop mode.
        cropModeActive = true
        Log.d( TAG, "Crop mode entered" )
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
            Log.w( TAG, "Cannot apply crop. No image selected." )
            return
        }
        if (!cropModeActive || cropRect == null) {
            Log.w( TAG, "Cannot apply crop. Crop mode is not active." )
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
            Log.w( TAG, "Cannot apply crop. Invalid crop size: " + "${cropWidth}x${cropHeight}" )
            return
        }
        if (
            cropWidth < MIN_CROP_SIZE.toInt() ||
            cropHeight < MIN_CROP_SIZE.toInt()
        ) {
            Log.w( TAG, "Cannot apply crop. Crop is smaller than minimum size: " + "${cropWidth}x${cropHeight}" )
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
        resetElementGestureState()
        activeCropHandle = CropHandle.NONE
        cropRectImage = null
        cropAspectRatio = CropAspectRatio.FREE
        cropModeActive = false
        cropController.clearSession()
        resetTransform()
        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        Log.d( TAG, "Crop applied: ${cropWidth}x${cropHeight}, " + "origin=($left,$top), " + "elements=${elements.size}" )
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
        if (!cropModeActive) {
            Log.d( TAG, "Reset crop ignored: crop mode is not active" )
            return
        }
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            Log.w( TAG, "Reset crop ignored: bitmap is missing" )
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
        resetElementGestureState()
        resetTransform()
        Log.d( TAG, "Crop reset to full image: " + "${currentBitmap.width}x${currentBitmap.height}" )
        invalidate()
    }
    /**
     * Cancels the current crop session and restores the state captured when
     * crop mode was entered. This can be called at any time while crop mode
     * is active.
     */
    fun cancelCrop() {
        if (!cropModeActive) {
            Log.d( TAG, "Cancel crop ignored: crop mode is not active" )
            return
        }
        val session = cropController.getSessionSnapshot()
        if (session == null) {
            Log.w( TAG, "Cancel crop failed: crop session snapshot is missing" )
            exitCropModeWithoutRestore()
            return
        }
        bitmap = session.bitmap
        elements.clear()
        elements.addAll(session.elements)
        selectedElement = session.elements.getOrNull(session.selectedIndex)
        // Re-apply selection state exactly as it was before crop mode.
        elements.forEach { element ->
            element.isSelected = element === selectedElement
        }
        cropRectImage = null
        cropAspectRatio = CropAspectRatio.FREE
        cropModeActive = false
        resetElementGestureState()
        activeCropHandle = CropHandle.NONE
        cropController.clearSession()
        resetTransform()
        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        Log.d( TAG, "Crop cancelled. Original image restored: " + "${session.bitmap?.width}x${session.bitmap?.height}, " + "elements=${elements.size}" )
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
        cropModeActive = false
        resetElementGestureState()
        activeCropHandle = CropHandle.NONE
        cropController.clearSession()
        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        invalidate()
    }
    /**
     * Creates the element copies required by the CropController session
     * snapshot. The controller owns the snapshot lifecycle; the View owns
     * knowledge of how editor elements are copied.
     */
    private fun createCropSessionElementsSnapshot(): List<EditorElement> {
        return elements.map { element ->
            when (element) {
                is TextElement -> element.copyForCropSession()
                else -> element
            }
        }
    }
    /** Returns the selected element index for the current Crop session. */
    private fun getSelectedElementIndexForCropSession(): Int = elements.indexOf(selectedElement)
    /**
     * Returns true when crop mode is currently active.
     */
    fun isCropMode(): Boolean = cropModeActive
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
        if (!cropModeActive) {
            Log.d( TAG, "Rotate crop ignored: crop mode is not active" )
            return
        }
        val currentBitmap = bitmap
        val currentCropRect = cropRectImage
        if (currentBitmap == null || currentCropRect == null) {
            Log.w( TAG, "Rotate crop ignored: bitmap or crop rectangle is missing" )
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
                        transformTextForRotateRight90(
                            textElement = element,
                            oldImageHeight = oldHeight
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
                    cropController.normalizeToAspectRatio(1f)
                }
                CropAspectRatio.FOUR_TO_THREE -> {
                    cropController.normalizeToAspectRatio(4f / 3f)
                }
                CropAspectRatio.SIXTEEN_TO_NINE -> {
                    cropController.normalizeToAspectRatio(16f / 9f)
                }
                CropAspectRatio.ORIGINAL_RATIO -> {
                    cropController.normalizeToAspectRatio(
                        rotatedBitmap.width.toFloat() / rotatedBitmap.height.toFloat()
                    )
                }
            }
            // A rotation changes the image dimensions, so any previous image
            // transform may no longer be appropriate. Keep the crop session
            // stable by fitting the rotated image back into the editor.
            resetTransform()
            activeCropHandle = CropHandle.NONE
            resetElementGestureState()
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
            Log.e( TAG, "Failed to rotate crop 90 degrees", exception )
        }
    }
    /** Starts a temporary non-destructive adjustment session. */
    fun enterAdjustmentMode() {
        if (cropModeActive || rotationModeActive || filterModeActive) {
            Log.d(TAG, "Adjustment mode ignored: another editor mode is active")
            return
        }
        adjustmentController.enter()
    }
    /** Returns true when the temporary adjustment session is active. */
    fun isAdjustmentMode(): Boolean = adjustmentController.isActive
    /** Returns the current adjustment values. */
    fun getAdjustmentState(): AdjustmentState = adjustmentController.currentState
    /** Updates the temporary adjustment state and refreshes its preview. */
    fun setAdjustmentState(state: AdjustmentState) {
        adjustmentController.setState(state)
    }
    /** Resets temporary adjustment values while keeping the session active. */
    fun resetAdjustments() {
        adjustmentController.reset()
    }
    /** Commits the current adjustment preview. */
    fun applyAdjustments() {
        adjustmentController.apply()
    }
    /** Discards the temporary adjustment preview. */
    fun cancelAdjustments() {
        adjustmentController.cancel()
    }
    /**
     * Enters the temporary Transform Mode. All Flip/Rotate operations made
     * while this mode is active are previews until Apply is pressed. Cancel
     * restores the complete editor state captured at entry.
     */
    fun enterTransformMode() {
        if (cropModeActive) {
            Log.d(TAG, "Transform mode ignored: crop mode is active")
            return
        }
        if (bitmap == null) {
            Log.d(TAG, "Transform mode ignored: no image selected")
            return
        }
        if (rotationModeActive) {
            Log.d(TAG, "Transform mode ignored: already active")
            return
        }
        beginTransformSession()
    }
    fun isRotationMode(): Boolean = transformController.isActive
    /** Starts a temporary transform session and snapshots the complete editor state. */
    private fun beginTransformSession() {
        transformController.enter()
    }
    /** Commits the current transform preview. */
    fun applyRotation() {
        if (!rotationModeActive) {
            Log.d(TAG, "Apply rotation ignored: transform mode is not active")
            return
        }
        transformController.apply()
        Log.d(TAG, "Transform applied")
    }
    /** Restores the exact editor state captured before transform preview began. */
    fun cancelRotation() {
        if (!rotationModeActive) {
            Log.d(TAG, "Cancel rotation ignored: transform mode is not active")
            return
        }
        val snapshot = transformController.cancel()
            ?: return
        bitmap = snapshot.bitmap
        elements.clear()
        elements.addAll(snapshot.elements)
        selectedElement = snapshot.elements.getOrNull(snapshot.selectedElementIndex)
        elements.forEach { element ->
            element.isSelected = element === selectedElement
        }
        scaleFactor = snapshot.scaleFactor
        translationX = snapshot.translationX
        translationY = snapshot.translationY
        notifySelectionChanged()
        invalidate()
        Log.d( TAG, "Transform cancelled. Original image restored: " + "${snapshot.bitmap.width}x${snapshot.bitmap.height}" )
    }
    private fun exitRotationModeWithoutRestore() {
        transformController.clearSession()
        resetGestureState()
        onRotationModeChanged?.invoke(false)
        invalidate()
    }
    /**
     * Rotates the current image 90 degrees counter-clockwise.
     *
     * A 90-degree rotation swaps the bitmap dimensions, so editor elements
     * stored in image coordinates must also be transformed into the new
     * coordinate system. Text rotation is adjusted by -90 degrees so the
     * text remains aligned with the rotated image.
     *
     * Rotation is intentionally disabled while Crop Mode is active. Crop Mode
     * has its own rotation operation and session snapshot/state handling.
     */
    fun rotateLeft90() {
        if (cropModeActive) {
            Log.d( TAG, "Rotate left ignored: crop mode is active" )
            return
        }
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            Log.d( TAG, "Rotate left ignored: no image selected" )
            return
        }
        if (currentBitmap.width <= 0 || currentBitmap.height <= 0) {
            Log.w( TAG, "Rotate left ignored: invalid bitmap dimensions" )
            return
        }
        beginTransformSession()
        val oldWidth = currentBitmap.width.toFloat()
        val oldHeight = currentBitmap.height.toFloat()
        try {
            // Android's negative 90 degree rotation rotates the bitmap
            // counter-clockwise. Bitmap.createBitmap() also returns a bitmap
            // with swapped width/height for this quarter-turn.
            val rotationMatrix = Matrix().apply {
                postRotate(-90f)
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
            // For a 90-degree counter-clockwise rotation in Android image
            // coordinates (Y increases downward):
            //
            //     (x, y) -> (y, W - x)
            //
            // W is the width of the original image.
            elements.forEach { element ->
                when (element) {
                    is TextElement -> {
                        transformTextForRotateLeft90(
                            textElement = element,
                            oldImageWidth = oldWidth
                        )
                    }
                    else -> Unit
                }
            }
            bitmap = rotatedBitmap
            // The image dimensions changed, so the previous zoom/pan transform
            // is no longer guaranteed to be appropriate for the new aspect
            // ratio. Fit the rotated image back into the editor.
            resetTransform()
            // Clear only transient gesture state. Selection and editor
            // elements remain intact.
            resetElementGestureState()
            Log.d(
                TAG,
                "Image rotated 90 degrees counter-clockwise: " +
                        "${currentBitmap.width}x${currentBitmap.height} -> " +
                        "${rotatedBitmap.width}x${rotatedBitmap.height}, " +
                        "elements=${elements.size}"
            )
            invalidate()
        } catch (exception: Exception) {
            Log.e( TAG, "Failed to rotate image 90 degrees counter-clockwise", exception )
        }
    }
    /**
     * Rotates the current image 90 degrees clockwise.
     *
     * Editor elements are stored in image coordinates, so their positions
     * must be transformed with the bitmap. Rotation changes the bitmap
     * dimensions (width and height are swapped), therefore the editor
     * transform is reset after the rotation so the new image fits correctly.
     *
     * Rotate Right is intentionally disabled while Crop Mode is active.
     */
    fun rotateRight90() {
        if (cropModeActive) {
            Log.d( TAG, "Rotate right ignored: crop mode is active" )
            return
        }
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            Log.d( TAG, "Rotate right ignored: no image selected" )
            return
        }
        if (currentBitmap.width <= 0 || currentBitmap.height <= 0) {
            Log.w( TAG, "Rotate right ignored: invalid bitmap dimensions" )
            return
        }
        beginTransformSession()
        val oldHeight = currentBitmap.height.toFloat()
        try {
            // Android's positive 90 degree rotation rotates the bitmap
            // clockwise. Bitmap.createBitmap() returns a bitmap with swapped
            // width/height for this quarter-turn.
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
            // For a 90-degree clockwise rotation in Android image
            // coordinates (Y increases downward):
            //
            //     (x, y) -> (H - y, x)
            //
            // H is the height of the original image.
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
            // The image dimensions changed, so fit the rotated image back
            // into the editor using the existing transform logic.
            resetTransform()
            // Clear only transient gesture state. Selection and editor
            // elements remain intact.
            resetElementGestureState()
            Log.d(
                TAG,
                "Image rotated 90 degrees clockwise: " +
                        "${currentBitmap.width}x${currentBitmap.height} -> " +
                        "${rotatedBitmap.width}x${rotatedBitmap.height}, " +
                        "elements=${elements.size}"
            )
            invalidate()
        } catch (exception: Exception) {
            Log.e( TAG, "Failed to rotate image 90 degrees clockwise", exception )
        }
    }
    /**
     * Flips the current image vertically while preserving its dimensions.
     *
     * Editor elements are stored in image coordinates, so their vertical
     * position must be mirrored together with the bitmap. Text rotation is
     * mirrored as well. Horizontal text alignment remains unchanged because
     * a vertical flip does not change the left/center/right relationship.
     *
     * Vertical flip is intentionally disabled while Crop Mode is active for
     * the same crop-session safety reason as horizontal flip.
     */
    fun flipVertical() {
        if (cropModeActive) {
            Log.d(TAG, "Vertical flip ignored: crop mode is active")
            return
        }
        val currentBitmap = bitmap
        if (currentBitmap == null) {
            Log.d( TAG, "Vertical flip ignored: no image selected" )
            return
        }
        val imageHeight = currentBitmap.height.toFloat()
        if (currentBitmap.width <= 0 || currentBitmap.height <= 0) {
            Log.w( TAG, "Vertical flip ignored: invalid bitmap dimensions" )
            return
        }
        beginTransformSession()
        try {
            val flippedBitmap = Bitmap.createBitmap(
                currentBitmap.width,
                currentBitmap.height,
                Bitmap.Config.ARGB_8888
            )
            val flipCanvas = Canvas(flippedBitmap)
            flipCanvas.save()
            flipCanvas.scale(
                1f,
                -1f,
                currentBitmap.width / 2f,
                imageHeight / 2f
            )
            flipCanvas.drawBitmap(
                currentBitmap,
                0f,
                0f,
                bitmapPaint
            )
            flipCanvas.restore()
            elements.forEach { element ->
                when (element) {
                    is TextElement -> {
                        transformTextForVerticalFlip(
                            textElement = element,
                            imageHeight = imageHeight
                        )
                    }
                    else -> Unit
                }
            }
            bitmap = flippedBitmap
            resetElementGestureState()
            Log.d(
                TAG,
                "Vertical flip applied: " +
                        "${currentBitmap.width}x${currentBitmap.height}, " +
                        "elements=${elements.size}, " +
                        "scaleFactor=$scaleFactor, " +
                        "translation=($translationX,$translationY)"
            )
            invalidate()
        } catch (exception: Exception) {
            Log.e( TAG, "Failed to flip image vertically", exception )
        }
    }
    /**
     * Mirrors a text element across the vertical center line of the image.
     *
     * TextElement.position is the local text anchor, not the left edge in all
     * alignment modes. Therefore the anchor itself is mirrored and LEFT/RIGHT
     * alignment is swapped. This keeps the rendered text bounds mirrored
     * exactly with the bitmap.
     */
    private fun transformTextForHorizontalFlip(
        textElement: TextElement,
        imageWidth: Float
    ) {
        textElement.position.x = imageWidth - textElement.position.x
        textElement.rotation = normalizeRotation(-textElement.rotation)
        textElement.alignment = when (textElement.alignment) {
            TextElement.TextAlignment.LEFT ->
                TextElement.TextAlignment.RIGHT
            TextElement.TextAlignment.CENTER ->
                TextElement.TextAlignment.CENTER
            TextElement.TextAlignment.RIGHT ->
                TextElement.TextAlignment.LEFT
        }
    }
    /**
     * Mirrors a text element across the horizontal center line of the image.
     * Horizontal alignment does not change because the reflection is vertical.
     */
    private fun transformTextForVerticalFlip(
        textElement: TextElement,
        imageHeight: Float
    ) {
        textElement.position.y = imageHeight - textElement.position.y
        textElement.rotation = normalizeRotation(-textElement.rotation)
    }
    /**
     * Maps a text anchor from the old image coordinate system into the new
     * coordinate system after a 90-degree counter-clockwise bitmap rotation.
     *
     * Old (W x H) -> New (H x W): (x, y) -> (y, W - x)
     */
    private fun transformTextForRotateLeft90(
        textElement: TextElement,
        oldImageWidth: Float
    ) {
        val oldX = textElement.position.x
        val oldY = textElement.position.y
        textElement.position.x = oldY
        textElement.position.y = oldImageWidth - oldX
        textElement.rotation = normalizeRotation(textElement.rotation - 90f)
    }
    /**
     * Maps a text anchor from the old image coordinate system into the new
     * coordinate system after a 90-degree clockwise bitmap rotation.
     *
     * Old (W x H) -> New (H x W): (x, y) -> (H - y, x)
     */
    private fun transformTextForRotateRight90(
        textElement: TextElement,
        oldImageHeight: Float
    ) {
        val oldX = textElement.position.x
        val oldY = textElement.position.y
        textElement.position.x = oldImageHeight - oldY
        textElement.position.y = oldX
        textElement.rotation = normalizeRotation(textElement.rotation + 90f)
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
        Log.d( TAG, "Free Crop mode selected" )
        invalidate()
    }
    /**
     * Returns true when Free Crop mode is active.
     */
    fun isFreeCropMode(): Boolean = cropAspectRatio == CropAspectRatio.FREE
    /**
     * Selects 1:1 (square) crop mode.
     */
    fun setOneToOneCropMode() {
        cropAspectRatio = CropAspectRatio.ONE_TO_ONE
        cropController.normalizeToAspectRatio(1f)
        Log.d(TAG, "1:1 Crop mode selected")
        invalidate()
    }
    /**
     * Returns true when 1:1 crop mode is active.
     */
    fun isOneToOneCropMode(): Boolean = cropAspectRatio == CropAspectRatio.ONE_TO_ONE
    /**
     * Enables 4:3 aspect-ratio crop mode.
     */
    fun setFourToThreeCropMode() {
        cropAspectRatio = CropAspectRatio.FOUR_TO_THREE
        cropController.normalizeToAspectRatio(4f / 3f)
        Log.d( TAG, "4:3 Crop mode selected" )
        invalidate()
    }
    /**
     * Returns true when 4:3 crop mode is active.
     */
    fun isFourToThreeCropMode(): Boolean = cropAspectRatio == CropAspectRatio.FOUR_TO_THREE
    /**
     * Enables 16:9 aspect-ratio crop mode.
     */
    fun setSixteenToNineCropMode() {
        cropAspectRatio = CropAspectRatio.SIXTEEN_TO_NINE
        cropController.normalizeToAspectRatio(16f / 9f)
        Log.d( TAG, "16:9 Crop mode selected" )
        invalidate()
    }
    /**
     * Returns true when 16:9 crop mode is active.
     */
    fun isSixteenToNineCropMode(): Boolean = cropAspectRatio == CropAspectRatio.SIXTEEN_TO_NINE
    /**
     * Enables Original Ratio crop mode.
     *
     * The crop selection keeps the same aspect ratio as the original image.
     */
    fun setOriginalRatioCropMode() {
        val currentBitmap = bitmap ?: return
        cropAspectRatio = CropAspectRatio.ORIGINAL_RATIO
        cropController.normalizeToAspectRatio(
            currentBitmap.width.toFloat() / currentBitmap.height.toFloat()
        )
        Log.d( TAG, "Original Ratio Crop mode selected" )
        invalidate()
    }
    /**
     * Returns true when Original Ratio crop mode is active.
     */
    fun isOriginalRatioCropMode(): Boolean = cropAspectRatio == CropAspectRatio.ORIGINAL_RATIO
    fun getImageWidth(): Int = bitmap?.width ?: 0
    fun getImageHeight(): Int = bitmap?.height ?: 0
    private fun resetTransform() {
        scaleFactor = MIN_SCALE
        translationX = 0f
        translationY = 0f
    }
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
        // IMAGE -> SCREEN
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
        // SCREEN -> IMAGE
        imageToScreenMatrix.invert(
            screenToImageMatrix
        )
    }
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
        return PointF(points[0], points[1])
    }

    private fun imageToScreenOrOrigin(imageX: Float, imageY: Float): PointF =
        imageToScreen(imageX, imageY) ?: PointF()
    fun imageToScreen(imageX: Float, imageY: Float): PointF? {
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
        return PointF(points[0], points[1])
    }
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
        Log.d( TAG, "Element added. Total elements: " + elements.size )
        notifySelectionChanged()
        invalidate()
    }
    fun getElements(): List<EditorElement> = elements
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
            Log.w( TAG, "Cannot update text. Element not found." )
            return
        }
        textElement.updateText(
            newText
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text element updated: $newText" )
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
            Log.w( TAG, "Cannot update text color. " + "Element not found." )
            return
        }
        textElement.updateColor(
            newColor
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text color updated: $newColor" )
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
            Log.w( TAG, "Cannot update text color range. Element not found." )
            return
        }
        textElement.updateColorRange(
            start,
            end,
            newColor
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text color range updated: " + "start=$start end=$end color=$newColor" )
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
            Log.w( TAG, "Cannot restore text color ranges. Element not found." )
            return
        }
        textElement.setColorRanges(ranges)
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text color ranges restored: ${ranges.size} ranges" )
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
            Log.w( TAG, "Cannot update text size. " + "Element not found." )
            return
        }
        textElement.updateTextSize(
            newSize
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text size updated: " + textElement.textSize )
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
            Log.w( TAG, "Cannot update text bold. " + "Element not found." )
            return
        }
        textElement.updateBold(
            enabled
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text bold updated: " + textElement.bold )
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
            Log.w( TAG, "Cannot update text italic. " + "Element not found." )
            return
        }
        textElement.updateItalic(
            enabled
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text italic updated: " + textElement.italic )
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
            Log.w( TAG, "Cannot update text alignment. " + "Element not found." )
            return
        }
        textElement.updateAlignment(
            alignment
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text alignment updated: " + textElement.alignment )
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
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text font updated: ${textElement.getFontDisplayName()}" )
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
            Log.w( TAG, "Cannot update text background. " + "Element not found." )
            return
        }
        textElement.updateBackground(
            enabled
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text background updated: " + enabled )
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
            Log.w( TAG, "Cannot update text background color. " + "Element not found." )
            return
        }
        textElement.updateBackgroundColor(
            newColor
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text background color updated: " + newColor )
        notifySelectionChanged()
        invalidate()
    }
    /** Marks an existing text element as the active selection after an update. */
    private fun selectUpdatedTextElement(textElement: TextElement) {
        textElement.isSelected = true
        selectedElement = textElement
    }

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
        Log.d( TAG, "Selected element: $selectedElement" )
        notifySelectionChanged()
        invalidate()
    }
    private fun notifySelectionChanged() {
        onSelectionChanged?.invoke(
            selectedElement
        )
    }
    fun getSelectedElement(): EditorElement? = selectedElement
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
    private fun getShapeResizeHandlePosition(shape: ShapeElement): PointF {
        return transformShapePoint(
            shape,
            PointF(shape.width / 2f, shape.height / 2f)
        )
    }
    private fun getShapeRotationHandlePosition(shape: ShapeElement): PointF {
        return transformShapePoint(
            shape,
            PointF(0f, -shape.height / 2f - ROTATION_HANDLE_DISTANCE)
        )
    }
    private fun transformShapePoint(shape: ShapeElement, localPoint: PointF): PointF {
        val radians = Math.toRadians(shape.rotation.toDouble())
        val cosValue = cos(radians).toFloat()
        val sinValue = sin(radians).toFloat()
        val scaledX = localPoint.x * shape.scale
        val scaledY = localPoint.y * shape.scale
        val rotatedX = scaledX * cosValue - scaledY * sinValue
        val rotatedY = scaledX * sinValue + scaledY * cosValue
        return imageToScreenOrOrigin(
            shape.position.x + rotatedX,
            shape.position.y + rotatedY
        )
    }
    private fun isOnShapeRotationHandle(eventX: Float, eventY: Float): Boolean {
        val shape = selectedElement as? ShapeElement ?: return false
        val handle = getShapeRotationHandlePosition(shape)
        return distance(eventX, eventY, handle.x, handle.y) <= SHAPE_DELETE_BUTTON_TOUCH_RADIUS
    }
    private fun getShapeDeleteHandlePosition(shape: ShapeElement): PointF {
        return transformShapePoint(
            shape,
            PointF(shape.width / 2f + SHAPE_DELETE_HANDLE_DISTANCE, -shape.height / 2f - SHAPE_DELETE_HANDLE_DISTANCE)
        )
    }
    private fun isOnShapeDeleteHandle(eventX: Float, eventY: Float): Boolean {
        val shape = selectedElement as? ShapeElement ?: return false
        val handle = getShapeDeleteHandlePosition(shape)
        return distance(eventX, eventY, handle.x, handle.y) <= HANDLE_TOUCH_RADIUS
    }
    private fun isOnShapeResizeHandle(eventX: Float, eventY: Float): Boolean {
        val shape = selectedElement as? ShapeElement ?: return false
        val handle = getShapeResizeHandlePosition(shape)
        return distance(eventX, eventY, handle.x, handle.y) <= HANDLE_TOUCH_RADIUS
    }
    private fun startShapeRotation(touchX: Float, touchY: Float) {
        val shape = selectedElement as? ShapeElement ?: return
        transformMode = TransformMode.ROTATE
        initialRotation = shape.rotation
        val center = imageToScreen(shape.position.x, shape.position.y) ?: return
        initialRotationAngle = Math.toDegrees(
            atan2((touchY - center.y).toDouble(), (touchX - center.x).toDouble())
        ).toFloat()
        Log.d(TAG, "Shape rotation started: $initialRotation")
    }
    private fun updateShapeRotation(touchX: Float, touchY: Float) {
        val shape = selectedElement as? ShapeElement ?: return
        val center = imageToScreen(shape.position.x, shape.position.y) ?: return
        val currentAngle = Math.toDegrees(
            atan2((touchY - center.y).toDouble(), (touchX - center.x).toDouble())
        ).toFloat()
        var delta = currentAngle - initialRotationAngle
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        var newRotation = initialRotation + delta
        while (newRotation < 0f) newRotation += 360f
        while (newRotation >= 360f) newRotation -= 360f
        shape.rotation = newRotation
        invalidate()
    }
    private fun startShapeResize(touchX: Float, touchY: Float) {
        val shape = selectedElement as? ShapeElement ?: return
        transformMode = TransformMode.RESIZE
        initialElementScale = shape.scale
        val center = imageToScreen(shape.position.x, shape.position.y) ?: return
        initialResizeDistance = distance(center.x, center.y, touchX, touchY).coerceAtLeast(1f)
        Log.d(TAG, "Shape resize started: $initialElementScale")
    }
    private fun updateShapeResize(touchX: Float, touchY: Float) {
        val shape = selectedElement as? ShapeElement ?: return
        val center = imageToScreen(shape.position.x, shape.position.y) ?: return
        val currentDistance = distance(center.x, center.y, touchX, touchY)
        if (initialResizeDistance <= 0f) return
        shape.scale = (initialElementScale * currentDistance / initialResizeDistance)
            .coerceIn(MIN_ELEMENT_SCALE, MAX_ELEMENT_SCALE)
        invalidate()
    }
    private fun drawShapeSelectionHandles(canvas: Canvas, shape: ShapeElement) {
        val halfWidth = shape.width / 2f
        val halfHeight = shape.height / 2f
        val topLeft = transformShapePoint(shape, PointF(-halfWidth, -halfHeight))
        val topRight = transformShapePoint(shape, PointF(halfWidth, -halfHeight))
        val bottomLeft = transformShapePoint(shape, PointF(-halfWidth, halfHeight))
        val bottomRight = transformShapePoint(shape, PointF(halfWidth, halfHeight))
        val rotationHandle = getShapeRotationHandlePosition(shape)
        val resizeHandle = getShapeResizeHandlePosition(shape)
        val deleteHandle = getShapeDeleteHandlePosition(shape)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
        }
        val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.BLACK
        }
        canvas.drawLine(topLeft.x, topLeft.y, topRight.x, topRight.y, paint)
        canvas.drawLine(topRight.x, topRight.y, bottomRight.x, bottomRight.y, paint)
        canvas.drawLine(bottomRight.x, bottomRight.y, bottomLeft.x, bottomLeft.y, paint)
        canvas.drawLine(bottomLeft.x, bottomLeft.y, topLeft.x, topLeft.y, paint)
        canvas.drawLine(
            (topLeft.x + topRight.x) / 2f,
            (topLeft.y + topRight.y) / 2f,
            rotationHandle.x,
            rotationHandle.y,
            paint
        )
        // Connector from the top-right corner to the delete handle.
        canvas.drawLine(
            topRight.x,
            topRight.y,
            deleteHandle.x,
            deleteHandle.y,
            paint
        )
        val radius = 11f
        listOf(topLeft, topRight, bottomLeft, bottomRight, rotationHandle, resizeHandle).forEach { point ->
            canvas.drawCircle(point.x, point.y, radius, handleFill)
            canvas.drawCircle(point.x, point.y, radius, handleStroke)
        }
        // FLOATING DELETE ACTION
        // Delete is intentionally different from the transform handles.
        // A prominent red floating button makes the destructive action obvious
        // and prevents it from being confused with resize/rotation handles.
        val deleteShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            alpha = 150
        }
        val deleteButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(220, 45, 45)
        }
        val deleteButtonStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.WHITE
        }
        // Small offset gives the floating button visual separation.
        canvas.drawCircle(
            deleteHandle.x,
            deleteHandle.y + 3f,
            SHAPE_DELETE_BUTTON_RADIUS + 2f,
            deleteShadowPaint
        )
        canvas.drawCircle(
            deleteHandle.x,
            deleteHandle.y,
            SHAPE_DELETE_BUTTON_RADIUS,
            deleteButtonPaint
        )
        canvas.drawCircle(
            deleteHandle.x,
            deleteHandle.y,
            SHAPE_DELETE_BUTTON_RADIUS,
            deleteButtonStroke
        )
        // Bold white trash-can icon.
        val trashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val trashBody = RectF(
            deleteHandle.x - 8f,
            deleteHandle.y - 6f,
            deleteHandle.x + 8f,
            deleteHandle.y + 9f
        )
        canvas.drawRoundRect(
            trashBody,
            2f,
            2f,
            trashPaint
        )
        canvas.drawRect(
            deleteHandle.x - 10f,
            deleteHandle.y - 10f,
            deleteHandle.x + 10f,
            deleteHandle.y - 6f,
            trashPaint
        )
        canvas.drawRoundRect(
            RectF(
                deleteHandle.x - 4f,
                deleteHandle.y - 13f,
                deleteHandle.x + 4f,
                deleteHandle.y - 9f
            ),
            1.5f,
            1.5f,
            trashPaint
        )
        // Cut two narrow slots into the icon to make the trash can
        // immediately recognizable even on small screens.
        val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            color = Color.rgb(220, 45, 45)
        }
        canvas.drawLine(
            deleteHandle.x - 3f,
            deleteHandle.y - 3f,
            deleteHandle.x - 3f,
            deleteHandle.y + 5f,
            slotPaint
        )
        canvas.drawLine(
            deleteHandle.x + 3f,
            deleteHandle.y - 3f,
            deleteHandle.x + 3f,
            deleteHandle.y + 5f,
            slotPaint
        )
    }
    /**
     * Returns the screen position of the floating delete action for the
     * currently selected text element.
     *
     * The button is deliberately placed outside the top-right corner so it
     * remains visually separate from the resize and rotation handles.
     */
    private fun getTextDeleteHandlePosition(
        textElement: TextElement
    ): PointF {
        /*
         * IMPORTANT:
         *
         * TextElement.getBounds() already returns the element bounds in
         * IMAGE/WORLD coordinates, including its current scale and rotation.
         *
         * The previous implementation passed those coordinates through
         * transformElementPoint(), which applies the text position/scale/
         * rotation a second time. That caused the delete button to appear
         * far away from the selected text.
         *
         * Keep the delete action close to the actual selected bounds, just
         * like the ShapeElement delete action.
         */
        val bounds = textElement.getBounds()
        val topRight = imageToScreen(bounds.right, bounds.top) ?: return PointF()
        return PointF(topRight.x + TEXT_DELETE_HANDLE_DISTANCE, topRight.y - TEXT_DELETE_HANDLE_DISTANCE)
    }
    private fun isOnTextDeleteHandle(
        eventX: Float,
        eventY: Float
    ): Boolean {
        val textElement =
            selectedElement as? TextElement
                ?: return false
        val handle =
            getTextDeleteHandlePosition(
                textElement
            )
        return distance(
            eventX,
            eventY,
            handle.x,
            handle.y
        ) <= TEXT_DELETE_BUTTON_TOUCH_RADIUS
    }
    /**
     * Draws the same prominent floating delete action used for shapes.
     * Keeping the visual treatment identical makes deletion predictable for
     * every editor element without confusing it with transform handles.
     */
    private fun drawTextDeleteButton(
        canvas: Canvas,
        textElement: TextElement
    ) {
        val deleteHandle =
            getTextDeleteHandlePosition(
                textElement
            )
        val bounds = textElement.getBounds()
        // getBounds() already returns transformed IMAGE/WORLD coordinates.
        // Convert that corner directly to screen coordinates. Do not call
        // transformElementPoint() here because that would apply the text
        // transform a second time and send the connector away from the text.
        val topRight = imageToScreen(bounds.right, bounds.top) ?: return
        val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.WHITE
            alpha = 180
        }
        canvas.drawLine(
            topRight.x,
            topRight.y,
            deleteHandle.x,
            deleteHandle.y,
            connectorPaint
        )
        val deleteShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            alpha = 150
        }
        val deleteButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(220, 45, 45)
        }
        val deleteButtonStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.WHITE
        }
        canvas.drawCircle(
            deleteHandle.x,
            deleteHandle.y + 3f,
            TEXT_DELETE_BUTTON_RADIUS + 2f,
            deleteShadowPaint
        )
        canvas.drawCircle(
            deleteHandle.x,
            deleteHandle.y,
            TEXT_DELETE_BUTTON_RADIUS,
            deleteButtonPaint
        )
        canvas.drawCircle(
            deleteHandle.x,
            deleteHandle.y,
            TEXT_DELETE_BUTTON_RADIUS,
            deleteButtonStroke
        )
        val trashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val trashBody = RectF(
            deleteHandle.x - 8f,
            deleteHandle.y - 6f,
            deleteHandle.x + 8f,
            deleteHandle.y + 9f
        )
        canvas.drawRoundRect(
            trashBody,
            2f,
            2f,
            trashPaint
        )
        canvas.drawRect(
            deleteHandle.x - 10f,
            deleteHandle.y - 10f,
            deleteHandle.x + 10f,
            deleteHandle.y - 6f,
            trashPaint
        )
        canvas.drawRoundRect(
            RectF(
                deleteHandle.x - 4f,
                deleteHandle.y - 13f,
                deleteHandle.x + 4f,
                deleteHandle.y - 9f
            ),
            1.5f,
            1.5f,
            trashPaint
        )
        val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            color = Color.rgb(220, 45, 45)
        }
        canvas.drawLine(
            deleteHandle.x - 3f,
            deleteHandle.y - 3f,
            deleteHandle.x - 3f,
            deleteHandle.y + 5f,
            slotPaint
        )
        canvas.drawLine(
            deleteHandle.x + 3f,
            deleteHandle.y - 3f,
            deleteHandle.x + 3f,
            deleteHandle.y + 5f,
            slotPaint
        )
    }
    private fun getRotationHandlePosition(
        textElement: TextElement
    ): PointF {
        /*
         * TextElement.getBounds() already contains the transformed bounds
         * in image/world coordinates. Do not pass these values through
         * transformElementPoint(), otherwise the element transform is applied
         * twice and the handle moves away from the text.
         */
        val bounds = textElement.getBounds()
        val topCenter = imageToScreen((bounds.left + bounds.right) / 2f, bounds.top) ?: return PointF()
        return PointF(topCenter.x, topCenter.y - ROTATION_HANDLE_DISTANCE)
    }
    private fun getResizeHandlePosition(
        textElement: TextElement
    ): PointF {
        /*
         * getBounds() is already in image/world coordinates, so convert the
         * bottom-right corner directly to screen coordinates. This keeps the
         * resize handle attached to the selection box at every text scale.
         */
        val bounds = textElement.getBounds()
        return imageToScreenOrOrigin(
            bounds.right,
            bounds.bottom
        )
    }
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
        return imageToScreenOrOrigin(
            textElement.position.x + rotatedX,
            textElement.position.y + rotatedY
        )
    }
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
            imageToScreen(element.position.x, element.position.y) ?: return
        initialRotationAngle =
            Math.toDegrees(
                atan2(
                    (touchY - center.y).toDouble(),
                    (touchX - center.x).toDouble()
                )
            ).toFloat()
        Log.d( TAG, "Rotation started. " + "initialRotation=$initialRotation " + "initialAngle=$initialRotationAngle" )
    }
    private fun updateRotation(
        touchX: Float,
        touchY: Float
    ) {
        val element =
            selectedElement as? TextElement
                ?: return
        val center =
            imageToScreen(element.position.x, element.position.y) ?: return
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
        Log.d( TAG, "Element rotation=$newRotation" )
        invalidate()
    }
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
            imageToScreen(element.position.x, element.position.y) ?: return
        initialResizeDistance =
            distance(
                center.x,
                center.y,
                touchX,
                touchY
            ).coerceAtLeast(
                1f
            )
        Log.d( TAG, "Resize started. " + "initialScale=$initialElementScale " + "initialDistance=$initialResizeDistance" )
    }
    private fun updateResize(
        touchX: Float,
        touchY: Float
    ) {
        val element =
            selectedElement as? TextElement
                ?: return
        val center =
            imageToScreen(element.position.x, element.position.y) ?: return
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
        Log.d( TAG, "Element scale=$newScale" )
        invalidate()
    }
    fun deleteSelectedElement() {
        if (rotationModeActive || cropModeActive) {
            Log.d(TAG, "Delete ignored: editor mode is active")
            return
        }
        val element =
            selectedElement
                ?: return
        Log.d( TAG, "Deleting selected element" )
        elements.remove(
            element
        )
        element.isSelected = false
        selectedElement = null
        transformMode = TransformMode.NONE
        notifySelectionChanged()
        invalidate()
    }
    fun addTestText() {
        if (
            bitmap == null
        ) {
            Log.d( TAG, "Cannot add text. No image selected." )
            return
        }
        val imageWidth =
            bitmap!!.width.toFloat()
        val imageHeight =
            bitmap!!.height.toFloat()
        val position =
            PointF(imageWidth / 2f, imageHeight / 2f)
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
    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )
        val currentBitmap =
            when {
                adjustmentModeActive -> adjustmentController.currentPreviewBitmap
                filterModeActive -> filterPreviewBitmap
                else -> bitmap
            } ?: return
        if (
            width <= 0 ||
            height <= 0
        ) {
            return
        }
        updateMatrices()
        // DRAW IMAGE
        editorRenderer.drawBitmap(
            canvas = canvas,
            bitmap = currentBitmap,
            imageToScreenMatrix = imageToScreenMatrix
        )
        // DRAW ELEMENTS
        editorRenderer.drawElements(
            canvas = canvas,
            elements = elements,
            imageToScreenMatrix = imageToScreenMatrix
        )
        // DRAW SELECTION HANDLES
        val selectedText =
            selectedElement as? TextElement
        if (
            selectedText != null &&
            selectedText.isSelected
        ) {
            val bounds = selectedText.getBounds()
            /*
             * TextElement.getBounds() already returns transformed image/world
             * coordinates. Convert those four corners directly to screen
             * coordinates. Previously transformElementPoint() was used here,
             * which transformed an already-transformed rectangle a second
             * time and caused the selection handles to appear far from the
             * actual text.
             */
            val topLeft = imageToScreenOrOrigin(bounds.left, bounds.top)
            val topRight = imageToScreenOrOrigin(bounds.right, bounds.top)
            val bottomLeft = imageToScreenOrOrigin(bounds.left, bounds.bottom)
            val bottomRight = imageToScreenOrOrigin(bounds.right, bounds.bottom)
            val rotationHandle = getRotationHandlePosition(selectedText)
            val resizeHandle = getResizeHandlePosition(selectedText)
            editorRenderer.drawTextSelectionHandles(
                canvas = canvas,
                topLeft = topLeft,
                topRight = topRight,
                bottomLeft = bottomLeft,
                bottomRight = bottomRight,
                rotationHandle = rotationHandle,
                resizeHandle = resizeHandle
            )
            drawTextDeleteButton(
                canvas = canvas,
                textElement = selectedText
            )
        }
        val selectedShape = selectedElement as? ShapeElement
        if (selectedShape != null && selectedShape.isSelected) {
            drawShapeSelectionHandles(canvas, selectedShape)
        }
        if (cropModeActive) {
            drawCropSelection(canvas)
        }
    }
    /** Releases temporary adjustment processing when the view leaves the window. */
    override fun onDetachedFromWindow() {
        adjustmentController.close()
        super.onDetachedFromWindow()
    }
    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {
        if (cropModeActive) {
            cropController.handleTouch(event)
            return true
        }
        // Rotation mode is a button-driven preview session. Ignore all canvas
        // gestures while it is active so pan, zoom, text movement and element
        // transforms cannot mutate the temporary rotation state.
        if (rotationModeActive) {
            return true
        }
        // Adjustment mode is controlled by the adjustment toolbar.
        // Canvas gestures must not mutate the editor during this session.
        if (adjustmentModeActive) {
            return true
        }
        // Filter mode is controlled by the filter toolbar.
        if (filterModeActive) {
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
                 * CHECK FLOATING DELETE ACTIONS FIRST
                 * -------------------------------------------------------------
                 */
                if (
                    event.pointerCount == 1 &&
                    selectedElement is ShapeElement &&
                    isOnShapeDeleteHandle(event.x, event.y)
                ) {
                    Log.d(TAG, "Shape delete handle touched")
                    deleteSelectedElement()
                    isMovingElement = false
                    transformMode = TransformMode.NONE
                    return true
                }
                if (
                    event.pointerCount == 1 &&
                    selectedElement is ShapeElement &&
                    isOnShapeRotationHandle(event.x, event.y)
                ) {
                    startShapeRotation(event.x, event.y)
                    isMovingElement = false
                    Log.d(TAG, "Shape rotation handle touched")
                    return true
                }
                if (
                    event.pointerCount == 1 &&
                    selectedElement is ShapeElement &&
                    isOnShapeResizeHandle(event.x, event.y)
                ) {
                    startShapeResize(event.x, event.y)
                    isMovingElement = false
                    Log.d(TAG, "Shape resize handle touched")
                    return true
                }
                if (
                    event.pointerCount == 1 &&
                    selectedElement is TextElement &&
                    isOnTextDeleteHandle(
                        event.x,
                        event.y
                    )
                ) {
                    Log.d(TAG, "Text delete button touched")
                    deleteSelectedElement()
                    isMovingElement = false
                    transformMode = TransformMode.NONE
                    return true
                }
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
                    Log.d( TAG, "Rotation handle touched" )
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
                    Log.d( TAG, "Resize handle touched" )
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
                        Log.d( TAG, "Element touched" )
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
                        Log.d( TAG, "Empty canvas touched" )
                    }
                }
                return true
            }
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
                    if (selectedElement is ShapeElement) {
                        updateShapeRotation(event.x, event.y)
                    } else {
                        updateRotation(event.x, event.y)
                    }
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
                    if (selectedElement is ShapeElement) {
                        updateShapeResize(event.x, event.y)
                    } else {
                        updateResize(event.x, event.y)
                    }
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
                            Log.d( TAG, "Moving element " + "dx=$dx dy=$dy" )
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
                        Log.d( TAG, "Panning image " + "dx=$dx dy=$dy" )
                    }
                    lastTouchX =
                        event.x
                    lastTouchY =
                        event.y
                    invalidate()
                }
                return true
            }
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

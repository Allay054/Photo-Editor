package com.allay.photoeditor.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
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
import com.allay.photoeditor.editor.annotation.AnnotationController
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.AnnotationType
import com.allay.photoeditor.editor.adjustment.AdjustmentController
import com.allay.photoeditor.editor.annotation.AnnotationHistoryController
import com.allay.photoeditor.editor.history.EditorHistoryController
import com.allay.photoeditor.editor.history.EditorStateSnapshot
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
        private const val ANNOTATION_DELETE_HANDLE_DISTANCE = 44f
        private const val ANNOTATION_DELETE_BUTTON_RADIUS = 28f
        private const val ANNOTATION_DELETE_BUTTON_TOUCH_RADIUS = 52f
        private const val ANNOTATION_SELECTION_TOUCH_PADDING = 24f
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
        if (!filterModeActive) {
            Log.d(TAG, "Apply filter ignored: filter mode is not active")
            return
        }

        // Selecting Original leaves the committed bitmap unchanged, so there
        // is no meaningful editor operation to add to global history.
        if (getFilterType() == FilterType.ORIGINAL) {
            filterController.apply()
            return
        }

        val before = captureEditorState(includeBitmap = true)
        filterController.apply()
        recordEditorHistory(
            before = before,
            after = captureEditorState(includeBitmap = true)
        )
    }
    /** Discards the temporary filter preview and restores the session source. */
    fun cancelFilterMode() {
        filterController.cancel()
    }
    /** Owns creation of editor shapes while PhotoEditorView keeps the common
     * element selection, movement and transform pipeline. */
    private val shapeController = ShapeController()

    /**
     * Owns annotation tool configuration while PhotoEditorView keeps
     * the common editor element list and rendering pipeline.
     */
    private val annotationController = AnnotationController()

    /**
     * Undo/Redo history for annotation operations only.
     *
     * Text, shapes and other editor elements are intentionally excluded.
     */
    private val annotationHistoryController =
        AnnotationHistoryController()

    /**
     * Global editor Undo / Redo history.
     *
     * Phase 11.1:
     * - owns editor-level history stacks
     * - remains separate from the existing annotation-only history
     * - does not yet automatically record every editor operation
     *
     * Operation recording is integrated incrementally in Phase 11.2+.
     */
    private val editorHistoryController =
        EditorHistoryController()

    /**
     * Snapshot captured when an annotation gesture starts.
     *
     * Used to store one Undo operation per complete drawing/eraser gesture.
     */
    private var annotationHistoryBeforeGesture:
            AnnotationHistoryController.State? = null

    /** True while the freehand annotation tool owns canvas touch input. */
    private var freehandModeActive = false

    /** Controls whether annotation selection handles are visible. */
    private var annotationSelectionVisible = false

    /** True only while the eraser owns canvas touch input. */
    private var eraserModeActive = false

    /** Path currently being created by the active freehand gesture. */
    private var activeAnnotationPath: Path? = null

    /** Prevents a single tap from creating an empty annotation. */
    private var activeAnnotationPointCount = 0

    /** Annotation type used by the currently active drawing gesture. */
    private var activeAnnotationType: AnnotationType = AnnotationType.FREEHAND

    /** Current eraser cursor in screen coordinates while the eraser is active. */
    private var activeEraserPoint: PointF? = null
    private var lastEraserImagePoint: PointF? = null

    /** Soft translucent fill for the eraser cursor. */
    private val eraserPreviewFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = 32
    }

    /** High-contrast outline for the eraser cursor. */
    private val eraserPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
        alpha = 220
    }

    /** Cached blurred version of the current base bitmap for blur annotations. */
    private var blurredBitmap: Bitmap? = null
    private var blurredBitmapSource: Bitmap? = null

    /** Cached pixelated version of the current base bitmap for pixelate annotations. */
    private var pixelatedBitmap: Bitmap? = null
    private var pixelatedBitmapSource: Bitmap? = null

    private val blurMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val blurPreviewPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 2f
        color = Color.WHITE
        alpha = 180
    }

    /**
     * Semi-transparent tint applied over pixelated regions. The selected
     * annotation color controls the tint while the mosaic detail remains
     * visible underneath.
     */
    private val pixelateColorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        alpha = 120
    }

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
    /**
     * Enters the Freehand Drawing mode.
     *
     * While active, single-finger canvas gestures create an annotation path
     * instead of selecting/moving editor elements or panning the image.
     */
    fun enterFreehandMode(): Boolean =
        enterAnnotationMode(
            annotationType = AnnotationType.FREEHAND,
            modeName = "Freehand"
        )

    /**
     * Enters Pen annotation mode.
     *
     * Pen uses the same image-space path and gesture pipeline as Freehand,
     * but the created element is stored as a PEN annotation.
     */
    fun enterPenMode(): Boolean =
        enterAnnotationMode(
            annotationType = AnnotationType.PEN,
            modeName = "Pen"
        )

    /**
     * Enters Highlighter annotation mode.
     *
     * Highlighter uses the same image-space drawing pipeline as Freehand and
     * Pen, while AnnotationElement renders it with a translucent stroke.
     */
    fun enterHighlighterMode(): Boolean =
        enterAnnotationMode(
            annotationType = AnnotationType.HIGHLIGHTER,
            modeName = "Highlighter"
        )

    /**
     * Enters Blur annotation mode.
     *
     * Blur uses the same freehand path interaction as the other drawing tools,
     * but the path is used as a mask over a blurred copy of the image.
     */
    fun enterBlurMode(): Boolean =
        enterAnnotationMode(
            annotationType = AnnotationType.BLUR,
            modeName = "Blur"
        )

    /**
     * Enters Pixelate annotation mode.
     *
     * Pixelate uses the same image-space freehand path interaction as the
     * other drawing tools, but the path is used as a mask over a cached
     * pixelated copy of the current image.
     */
    fun enterPixelateMode(): Boolean =
        enterAnnotationMode(
            annotationType = AnnotationType.PIXELATE,
            modeName = "Pixelate"
        )

    /**
     * Enters Eraser annotation mode.
     *
     * The eraser operates only on AnnotationElement instances and never
     * modifies the original bitmap, text, or shapes.
     */
    fun enterEraserMode(): Boolean =
        enterAnnotationMode(
            annotationType = AnnotationType.ERASER,
            modeName = "Eraser"
        )

    /**
     * Shared annotation-mode entry point.
     *
     * All annotation tools use the same transient interaction state. Keeping
     * that state reset in one place prevents the individual tool entry points
     * from drifting apart while preserving their existing public API.
     */
    private fun enterAnnotationMode(
        annotationType: AnnotationType,
        modeName: String
    ): Boolean {
        if (bitmap == null) {
            Log.d(TAG, "Cannot enter $modeName mode. No image selected.")
            return false
        }

        if (isAnotherEditorModeActive()) {
            Log.d(TAG, "Cannot enter $modeName mode. Editor mode is active.")
            return false
        }

        annotationController.setAnnotationType(annotationType)
        activeAnnotationType = annotationType
        freehandModeActive = annotationType != AnnotationType.ERASER
        eraserModeActive = annotationType == AnnotationType.ERASER
        annotationSelectionVisible = true
        resetAnnotationInteractionState()
        selectElement(null)

        Log.d(TAG, "$modeName mode entered")
        invalidate()
        return true
    }

    /** Returns true when another editor mode currently owns the canvas. */
    private fun isAnotherEditorModeActive(): Boolean {
        return cropModeActive ||
                rotationModeActive ||
                adjustmentModeActive ||
                filterModeActive
    }

    /**
     * Clears only transient annotation interaction state.
     *
     * Persistent annotations and their history are intentionally untouched.
     */
    private fun resetAnnotationInteractionState() {
        activeAnnotationPath = null
        activeAnnotationPointCount = 0
        activeEraserPoint = null
        lastEraserImagePoint = null
        resetElementGestureState()
    }

    /**
     * Controls visibility of annotation selection handles independently from
     * the drawing state. The selected annotation itself is preserved when the
     * annotation toolbar is closed, so reopening the toolbar restores its
     * selection/delete affordance.
     */
    fun setAnnotationSelectionVisible(visible: Boolean) {
        annotationSelectionVisible = visible
        invalidate()
    }

    /**
     * Reopens annotation selection UI without entering a drawing tool.
     * Existing selected annotations are intentionally preserved.
     */
    fun enterAnnotationSelectionMode() {
        if (bitmap == null) return

        freehandModeActive = false
        eraserModeActive = false
        activeAnnotationPath = null
        activeAnnotationPointCount = 0
        activeEraserPoint = null
        lastEraserImagePoint = null
        annotationSelectionVisible = true
        resetElementGestureState()
        invalidate()
    }

    /** Returns true when an annotation drawing mode currently owns canvas touch input. */
    fun isFreehandMode(): Boolean = freehandModeActive

    /**
     * Updates the color used by newly created annotation strokes.
     * The in-progress preview also uses the new color immediately.
     */
    fun setAnnotationColor(color: Int) {
        annotationController.setColor(color)
        invalidate()
    }

    /**
     * Updates the stroke width used by newly created annotation strokes.
     * The value is stored in image coordinates.
     */
    fun setAnnotationStrokeWidth(strokeWidth: Float) {
        annotationController.setStrokeWidth(strokeWidth)
        invalidate()
    }

    /** Returns the currently selected annotation color. */
    fun getAnnotationColor(): Int = annotationController.currentColor

    /** Returns the currently selected annotation stroke width. */
    fun getAnnotationStrokeWidth(): Float = annotationController.currentStrokeWidth

    /** Exits Freehand Drawing mode without changing existing annotations. */
    fun exitFreehandMode() {
        // Always hide annotation selection UI when leaving the annotation
        // toolbar, even if drawing mode was already stopped after selecting
        // an existing annotation. The selected annotation itself is kept so
        // reopening the annotation toolbar can show its handles again.
        if (freehandModeActive || eraserModeActive) {
            finishActiveFreehandPath(commit = false)
            cancelAnnotationHistoryGesture()
        }
        activeEraserPoint = null
        lastEraserImagePoint = null
        freehandModeActive = false
        eraserModeActive = false
        annotationSelectionVisible = false
        resetElementGestureState()
        Log.d(TAG, "Freehand mode exited")
        invalidate()
    }

    /**
     * Captures the current annotation state before a drawing/eraser gesture.
     * One complete finger gesture becomes one Undo operation.
     */
    private fun beginAnnotationHistoryGesture() {
        annotationHistoryBeforeGesture =
            annotationHistoryController.capture(elements)
    }

    /**
     * Records the annotation state after a completed drawing/eraser gesture.
     */
    private fun finishAnnotationHistoryGesture() {
        val before = annotationHistoryBeforeGesture
            ?: return

        val after =
            annotationHistoryController.capture(elements)

        annotationHistoryController.record(
            before = before,
            after = after
        )

        annotationHistoryBeforeGesture = null
        onAnnotationHistoryChanged?.invoke()
    }

    /** Discards a pending annotation history gesture without recording it. */
    private fun cancelAnnotationHistoryGesture() {
        annotationHistoryBeforeGesture = null
    }

    /**
     * Restores only AnnotationElement state from an annotation history snapshot.
     *
     * Text, shapes, image transforms, crop state and other editor state are
     * intentionally untouched.
     */
    private fun restoreAnnotationState(
        state: AnnotationHistoryController.State
    ) {
        elements.removeAll { element ->
            element is AnnotationElement
        }

        state.annotations
            .sortedBy { it.index }
            .forEach { snapshot ->
                val annotation = AnnotationElement(
                    annotationType = snapshot.annotationType,
                    path = Path(snapshot.path),
                    color = snapshot.color,
                    strokeWidth = snapshot.strokeWidth
                )

                val targetIndex =
                    snapshot.index.coerceIn(0, elements.size)

                elements.add(targetIndex, annotation)
            }

        selectedElement?.isSelected = false
        selectedElement = null
        transformMode = TransformMode.NONE

        notifySelectionChanged()
        invalidate()
    }

    /** Undoes the most recent annotation operation. */
    fun undoAnnotation(): Boolean {
        if (freehandModeActive || eraserModeActive) {
            finishActiveFreehandPath(commit = false)
            freehandModeActive = false
            eraserModeActive = false
            activeEraserPoint = null
            lastEraserImagePoint = null
            resetElementGestureState()
        }

        val state = annotationHistoryController.undo()
            ?: return false

        restoreAnnotationState(state)
        onAnnotationHistoryChanged?.invoke()

        Log.d(TAG, "Annotation undo performed")
        return true
    }

    /** Redoes the most recently undone annotation operation. */
    fun redoAnnotation(): Boolean {
        if (freehandModeActive || eraserModeActive) {
            finishActiveFreehandPath(commit = false)
            freehandModeActive = false
            eraserModeActive = false
            activeEraserPoint = null
            lastEraserImagePoint = null
            resetElementGestureState()
        }

        val state = annotationHistoryController.redo()
            ?: return false

        restoreAnnotationState(state)
        onAnnotationHistoryChanged?.invoke()

        Log.d(TAG, "Annotation redo performed")
        return true
    }

    fun canUndoAnnotation(): Boolean =
        annotationHistoryController.canUndo()

    fun canRedoAnnotation(): Boolean =
        annotationHistoryController.canRedo()

    /** Clears annotation Undo/Redo history without changing annotations. */
    fun clearAnnotationHistory() {
        annotationHistoryController.clear()
        annotationHistoryBeforeGesture = null
        onAnnotationHistoryChanged?.invoke()
        invalidate()
    }

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
     * Global history snapshot captured at the beginning of one element gesture.
     *
     * One continuous move / resize / rotate gesture is recorded as one
     * Undo operation rather than one operation per MotionEvent.ACTION_MOVE.
     */
    private var elementGestureHistoryBefore: EditorStateSnapshot? = null
    private var elementGestureHistoryChanged = false

    /**
     * Global history snapshot for a temporary image-transform session.
     *
     * Flip/rotate operations can be combined before Apply, so the complete
     * transform session is recorded as one global history operation.
     */
    private var transformHistoryBefore: EditorStateSnapshot? = null
    private var transformHistoryChanged = false

    /**
     * Global history snapshot captured when an adjustment session is about to
     * be committed. AdjustmentController invokes onAdjustmentsApplied only
     * after the newest preview has actually been committed.
     */
    private var adjustmentHistoryBefore: EditorStateSnapshot? = null

    private fun beginElementHistoryGesture() {
        elementGestureHistoryBefore = captureEditorState()
        elementGestureHistoryChanged = false
    }

    private fun markElementHistoryChanged() {
        elementGestureHistoryChanged = true
    }

    private fun finishElementHistoryGesture() {
        val before = elementGestureHistoryBefore
            ?: return

        if (elementGestureHistoryChanged) {
            recordEditorHistory(
                before = before,
                after = captureEditorState()
            )
        }

        elementGestureHistoryBefore = null
        elementGestureHistoryChanged = false
    }

    private fun cancelElementHistoryGesture() {
        elementGestureHistoryBefore = null
        elementGestureHistoryChanged = false
    }
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
    /**
     * Called whenever annotation Undo/Redo availability changes.
     *
     * MainActivity uses this callback to enable/disable the annotation
     * Undo and Redo toolbar buttons.
     */
    var onAnnotationHistoryChanged:
            (() -> Unit)? = null

    /**
     * Called whenever global editor Undo/Redo availability changes.
     *
     * MainActivity uses this callback to keep the global History buttons
     * synchronized with the editor history.
     */
    var onHistoryChanged:
            (() -> Unit)? = null
    /** Owns temporary adjustment state and background preview processing. */
    private val adjustmentController = AdjustmentController(
        getSourceBitmap = { bitmap },
        setCommittedBitmap = { bitmap = it },
        resetGestureState = ::resetGestureState,
        onModeChanged = { active ->
            onAdjustmentModeChanged?.invoke(active)

            // AdjustmentController commits the bitmap before it reports that
            // the session ended. This records history only after Apply has
            // actually committed the newest preview. Cancel clears the pending
            // snapshot first, so Cancel is never stored.
            if (!active) {
                val before = adjustmentHistoryBefore
                adjustmentHistoryBefore = null

                if (before != null) {
                    recordEditorHistory(
                        before = before,
                        after = captureEditorState(includeBitmap = true)
                    )
                }
            }
        },
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
                        if (tappedElement.isLocked) {
                            Log.d(TAG, "Text edit ignored: element is locked")
                            return true
                        }
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
        clearBlurredBitmapCache()
        clearAnnotationHistory()
        clearHistory()
        cancelElementHistoryGesture()
        transformHistoryBefore = null
        transformHistoryChanged = false
        adjustmentHistoryBefore = null
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
        freehandModeActive = false
        eraserModeActive = false
        activeAnnotationPath = null
        activeAnnotationPointCount = 0
        activeAnnotationType = AnnotationType.FREEHAND
        activeEraserPoint = null
        lastEraserImagePoint = null
        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        Log.d( TAG, "Image set: ${bitmap.width} x ${bitmap.height}" )
        invalidate()
    }
    fun clearImage() {
        clearBlurredBitmapCache()
        clearAnnotationHistory()
        clearHistory()
        cancelElementHistoryGesture()
        transformHistoryBefore = null
        transformHistoryChanged = false
        adjustmentHistoryBefore = null
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
        freehandModeActive = false
        eraserModeActive = false
        activeAnnotationPath = null
        activeAnnotationPointCount = 0
        activeAnnotationType = AnnotationType.FREEHAND
        activeEraserPoint = null
        lastEraserImagePoint = null
        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        invalidate()
    }
    fun getCurrentBitmap(): Bitmap? = bitmap
    /**
     * Applies all current annotation effects directly to the editor bitmap.
     *
     * Blur and Pixelate are composited from the current source bitmap using
     * their existing image-space paths. Regular annotations are rendered into
     * the same bitmap. Text and Shape elements remain editable because they
     * are intentionally not part of the annotation layer.
     *
     * After applying, annotation elements are removed and annotation history
     * is cleared because the committed bitmap is now the new source image.
     */
    fun applyAnnotations(): Boolean {
        val sourceBitmap = bitmap ?: return false

        // Finish any active drawing gesture before committing the annotation layer.
        if (freehandModeActive) {
            finishActiveFreehandPath(commit = true)
        }
        cancelAnnotationHistoryGesture()

        val annotationElements = elements
            .filterIsInstance<AnnotationElement>()
            .toList()

        if (annotationElements.isEmpty()) {
            return false
        }

        // Phase 11.6: applying annotations is a committed editor operation,
        // so global Undo/Redo must restore both the bitmap and annotation layers.
        val historyBefore = captureEditorState(includeBitmap = true)

        return try {
            val outputBitmap = Bitmap.createBitmap(
                sourceBitmap.width,
                sourceBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            val outputCanvas = Canvas(outputBitmap)

            // Start from the current editor bitmap so every existing image edit
            // remains intact.
            outputCanvas.drawBitmap(
                sourceBitmap,
                0f,
                0f,
                bitmapPaint
            )

            val identityMatrix = Matrix()

            // Blur/Pixelate must be composited from the original source bitmap
            // rather than from the progressively modified output bitmap.
            val blurredSource = if (
                annotationElements.any {
                    it.annotationType == AnnotationType.BLUR
                }
            ) {
                getOrCreateBlurredBitmap(sourceBitmap)
            } else {
                null
            }

            val pixelatedSource = if (
                annotationElements.any {
                    it.annotationType == AnnotationType.PIXELATE
                }
            ) {
                getOrCreatePixelatedBitmap(sourceBitmap)
            } else {
                null
            }

            annotationElements.forEach { annotation ->
                when (annotation.annotationType) {
                    AnnotationType.BLUR -> {
                        val blurred = blurredSource ?: return@forEach

                        blurMaskPaint.style = Paint.Style.STROKE
                        blurMaskPaint.strokeWidth = annotation.strokeWidth
                        blurMaskPaint.xfermode = null

                        val maskPath = Path()
                        blurMaskPaint.getFillPath(
                            annotation.path,
                            maskPath
                        )

                        outputCanvas.save()
                        outputCanvas.clipPath(maskPath)
                        outputCanvas.drawBitmap(
                            blurred,
                            0f,
                            0f,
                            bitmapPaint
                        )
                        outputCanvas.restore()
                    }

                    AnnotationType.PIXELATE -> {
                        val pixelated = pixelatedSource ?: return@forEach

                        blurMaskPaint.style = Paint.Style.STROKE
                        blurMaskPaint.strokeWidth = annotation.strokeWidth
                        blurMaskPaint.xfermode = null

                        val maskPath = Path()
                        blurMaskPaint.getFillPath(
                            annotation.path,
                            maskPath
                        )

                        outputCanvas.save()
                        outputCanvas.clipPath(maskPath)
                        outputCanvas.drawBitmap(
                            pixelated,
                            0f,
                            0f,
                            bitmapPaint
                        )

                        pixelateColorPaint.color = annotation.color
                        outputCanvas.drawPath(
                            maskPath,
                            pixelateColorPaint
                        )
                        outputCanvas.restore()
                    }

                    AnnotationType.FREEHAND,
                    AnnotationType.PEN,
                    AnnotationType.HIGHLIGHTER -> {
                        annotation.draw(
                            outputCanvas,
                            identityMatrix
                        )
                    }

                    // Eraser changes annotation paths while editing and does
                    // not represent a drawable bitmap layer of its own.
                    AnnotationType.ERASER -> Unit
                }
            }

            bitmap = outputBitmap
            clearBlurredBitmapCache()

            elements.removeAll { element ->
                element is AnnotationElement
            }

            selectedElement = null
            annotationSelectionVisible = false
            activeAnnotationPath = null
            activeAnnotationPointCount = 0
            activeEraserPoint = null
            lastEraserImagePoint = null
            freehandModeActive = false
            eraserModeActive = false
            resetElementGestureState()
            notifySelectionChanged()

            annotationHistoryController.clear()
            annotationHistoryBeforeGesture = null
            onAnnotationHistoryChanged?.invoke()

            // Record only after the annotation bitmap has been successfully
            // committed and annotation elements have been removed.
            recordEditorHistory(
                before = historyBefore,
                after = captureEditorState(includeBitmap = true)
            )

            invalidate()

            Log.d(
                TAG,
                "Annotations applied: ${annotationElements.size}"
            )
            true
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to apply annotations",
                exception
            )
            false
        }
    }

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
            transformHistoryChanged = true
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

        val historyBefore = if (!isFullImage) {
            captureEditorState(includeBitmap = true)
        } else {
            null
        }

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
        if (historyBefore != null) {
            recordEditorHistory(
                before = historyBefore,
                after = captureEditorState(includeBitmap = true)
            )
        }

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
        if (!adjustmentModeActive) {
            Log.d(TAG, "Apply adjustments ignored: adjustment mode is not active")
            return
        }

        // A neutral adjustment session does not change the committed image.
        if (getAdjustmentState() == AdjustmentState()) {
            adjustmentHistoryBefore = null
            adjustmentController.apply()
            return
        }

        if (adjustmentHistoryBefore == null) {
            adjustmentHistoryBefore = captureEditorState(includeBitmap = true)
        }

        // AdjustmentController may wait for the newest background preview.
        // The AdjustmentController mode callback records history only after
        // that preview is actually committed.
        adjustmentController.apply()
    }
    /** Discards the temporary adjustment preview. */
    fun cancelAdjustments() {
        adjustmentHistoryBefore = null
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
        if (!rotationModeActive) {
            transformHistoryBefore = captureEditorState(includeBitmap = true)
            transformHistoryChanged = false
        }

        transformController.enter()
    }
    /** Commits the current transform preview. */
    fun applyRotation() {
        if (!rotationModeActive) {
            Log.d(TAG, "Apply rotation ignored: transform mode is not active")
            return
        }

        transformController.apply()

        val before = transformHistoryBefore
        if (before != null && transformHistoryChanged) {
            recordEditorHistory(
                before = before,
                after = captureEditorState(includeBitmap = true)
            )
        }

        transformHistoryBefore = null
        transformHistoryChanged = false
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
        transformHistoryBefore = null
        transformHistoryChanged = false
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
            transformHistoryChanged = true
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
            transformHistoryChanged = true
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
            transformHistoryChanged = true
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
        val historyBefore = captureEditorState()

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

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
    }
    fun getElements(): List<EditorElement> = elements

    // =========================================================================
    // GLOBAL EDITOR HISTORY - PHASE 11.1
    // =========================================================================

    /**
     * Captures the current editor state for the global Undo / Redo system.
     *
     * Element snapshots are independent copies. The current bitmap is optional
     * for Phase 11.1 because image-operation history is integrated separately
     * in Phase 11.3.
     *
     * Viewport state (zoom/pan) is intentionally not part of editor history.
     */
    private fun captureEditorState(
        includeBitmap: Boolean = false
    ): EditorStateSnapshot {
        val elementCopies = elements.map { element ->
            element.duplicate().also { copy ->
                copy.isSelected = false
                copy.isVisible = element.isVisible
                copy.isLocked = element.isLocked
            }
        }

        return EditorStateSnapshot(
            elements = elementCopies,
            selectedElementIndex = elements.indexOf(selectedElement),
            bitmap = if (includeBitmap) {
                bitmap?.let { currentBitmap ->
                    currentBitmap.copy(
                        currentBitmap.config ?: Bitmap.Config.ARGB_8888,
                        true
                    )
                }
            } else {
                null
            }
        )
    }

    /**
     * Restores a previously captured global editor state.
     *
     * Bitmap restoration is performed only when the snapshot contains a bitmap.
     * This allows Phase 11.1 element history to be introduced without changing
     * the existing image-operation pipeline.
     */
    private fun restoreEditorState(
        state: EditorStateSnapshot
    ) {
        if (state.bitmap != null) {
            bitmap = state.bitmap
            clearBlurredBitmapCache()
        }

        selectedElement?.isSelected = false
        elements.clear()
        elements.addAll(
            state.elements.map { element ->
                element.duplicate().also { copy ->
                    copy.isSelected = false
                    copy.isVisible = element.isVisible
                    copy.isLocked = element.isLocked
                }
            }
        )

        selectedElement = elements.getOrNull(
            state.selectedElementIndex
        )

        elements.forEach { element ->
            element.isSelected = element === selectedElement
        }

        transformMode = TransformMode.NONE
        isMovingElement = false
        isDragging = false

        notifySelectionChanged()
        invalidate()
    }

    /**
     * Records one completed editor operation.
     *
     * The current Phase 11.1 foundation stores the state that existed before
     * the operation. Redo receives the current state at the moment Redo/Undo
     * is requested. Operation-specific integration is added in later phases.
     */
    private fun recordEditorHistory(
        before: EditorStateSnapshot,
        after: EditorStateSnapshot
    ) {
        editorHistoryController.record(
            before = before,
            after = after
        )

        onHistoryChanged?.invoke()
    }

    /**
     * Undoes the most recent global editor operation.
     *
     * Annotation Undo remains separate and continues to use the existing
     * AnnotationHistoryController until Phase 11.6 integrates the two systems.
     */
    fun undo(): Boolean {
        if (freehandModeActive || eraserModeActive) {
            finishActiveFreehandPath(commit = false)
            cancelAnnotationHistoryGesture()
            freehandModeActive = false
            eraserModeActive = false
            activeEraserPoint = null
            lastEraserImagePoint = null
            resetElementGestureState()
        }

        val state = editorHistoryController.undo(
            currentState = captureEditorState()
        ) ?: return false

        restoreEditorState(state)

        onHistoryChanged?.invoke()

        Log.d(TAG, "Global editor undo performed")
        return true
    }

    /**
     * Redoes the most recently undone global editor operation.
     */
    fun redo(): Boolean {
        if (freehandModeActive || eraserModeActive) {
            finishActiveFreehandPath(commit = false)
            cancelAnnotationHistoryGesture()
            freehandModeActive = false
            eraserModeActive = false
            activeEraserPoint = null
            lastEraserImagePoint = null
            resetElementGestureState()
        }

        val state = editorHistoryController.redo(
            currentState = captureEditorState()
        ) ?: return false

        restoreEditorState(state)

        onHistoryChanged?.invoke()

        Log.d(TAG, "Global editor redo performed")
        return true
    }

    /**
     * Returns true when a global editor Undo operation is available.
     */
    fun canUndo(): Boolean {
        return editorHistoryController.canUndo()
    }

    /**
     * Returns true when a global editor Redo operation is available.
     */
    fun canRedo(): Boolean {
        return editorHistoryController.canRedo()
    }

    /**
     * Returns the number of available global Undo operations.
     */
    fun undoCount(): Int {
        return editorHistoryController.undoCount()
    }

    /**
     * Returns the number of available global Redo operations.
     */
    fun redoCount(): Int {
        return editorHistoryController.redoCount()
    }

    /**
     * Clears global editor history without changing the current editor state.
     *
     * This is used when starting a new image/editor session and will also be
     * used by later export/save history-safety integration.
     */
    fun clearHistory() {
        editorHistoryController.clear()
        onHistoryChanged?.invoke()
        Log.d(TAG, "Global editor history cleared")
    }


    /**
     * Returns the number of editable layers currently in the editor.
     *
     * The existing elements list is the single source of truth for layer
     * ordering. No separate layer collection is maintained.
     */
    fun getLayerCount(): Int = elements.size

    /**
     * Returns the editor element at the requested layer index.
     *
     * Layer ordering follows the existing elements list:
     *
     * - index 0 = bottom-most layer
     * - last index = top-most layer
     *
     * Returns null when the index is outside the current layer range.
     */
    fun getLayer(index: Int): EditorElement? {
        return elements.getOrNull(index)
    }

    /**
     * Returns the current layer index of the supplied editor element.
     *
     * Returns -1 when the element is not currently part of the editor.
     *
     * Layer ordering follows the existing elements list:
     *
     * - index 0 = bottom-most layer
     * - last index = top-most layer
     */
    fun getLayerIndex(element: EditorElement): Int {
        return elements.indexOf(element)
    }

    /**
     * Selects an existing element by its layer index.
     *
     * Layer index follows the existing elements list:
     * index 0 is the bottom-most layer and the last index is the top-most layer.
     *
     * Selection goes through the existing selection pipeline so existing
     * selection callbacks, handles, and UI state remain synchronized.
     */
    fun selectLayer(index: Int): Boolean {
        val element = elements.getOrNull(index) ?: return false

        selectElement(element)
        return true
    }

    /**
     * Moves the selected element to the top-most layer.
     *
     * Layer order is represented directly by the existing elements list:
     * index 0 is the bottom-most layer and the last index is the top-most.
     * The selected element remains selected after reordering.
     */
    /**
     * Duplicates the selected element and inserts the duplicate immediately
     * above the original in the existing layer list.
     */
    fun duplicateSelectedElement(): Boolean {
        if (rotationModeActive || cropModeActive) {
            Log.d(TAG, "Duplicate ignored: editor mode is active")
            return false
        }

        val element = selectedElement ?: return false
        val historyBefore = captureEditorState()
        val currentIndex = elements.indexOf(element)
        if (currentIndex < 0) {
            Log.w(TAG, "Duplicate ignored: selected element not found")
            return false
        }

        val duplicate = element.duplicate()
        // A duplicated layer is an active, visible layer even when the source
        // layer was hidden from the Layers panel.
        duplicate.isVisible = true

        element.isSelected = false
        duplicate.isSelected = true
        elements.add(currentIndex + 1, duplicate)
        selectedElement = duplicate
        transformMode = TransformMode.NONE

        Log.d(TAG, "Element duplicated. Original index=$currentIndex, duplicate index=${currentIndex + 1}, total=${elements.size}")
        notifySelectionChanged()
        invalidate()
        return true
    }

    /**
     * Toggles visibility of the currently selected layer.
     *
     * Hidden layers stay in the existing elements list so their layer order
     * and data are preserved. The layer can be shown again from the Layers panel.
     */
    fun toggleSelectedElementVisibility(): Boolean {
        if (rotationModeActive || cropModeActive) {
            Log.d(TAG, "Visibility change ignored: editor mode is active")
            return false
        }

        val element = selectedElement ?: return false
        if (!elements.contains(element)) return false

        val historyBefore = captureEditorState()

        element.isVisible = !element.isVisible
        element.isSelected = element.isVisible

        Log.d(TAG, "Layer visibility changed: visible=${element.isVisible}")
        notifySelectionChanged()
        invalidate()
        return true
    }

    /**
     * Toggles the lock state of the currently selected layer.
     *
     * Locked elements remain in the layer stack and can still be selected
     * from the Layers panel. Canvas editing operations are blocked while
     * layer ordering remains available.
     */
    fun toggleSelectedElementLock(): Boolean {
        if (rotationModeActive || cropModeActive) {
            Log.d(TAG, "Lock change ignored: editor mode is active")
            return false
        }

        val element = selectedElement ?: return false
        if (!elements.contains(element)) return false

        val historyBefore = captureEditorState()

        element.isLocked = !element.isLocked
        transformMode = TransformMode.NONE
        isMovingElement = false

        Log.d(TAG, "Layer lock changed: locked=${element.isLocked}")
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )

        return true
    }

    /**
     * Sets the lock state of the selected layer.
     */
    fun setSelectedElementLocked(locked: Boolean): Boolean {
        if (rotationModeActive || cropModeActive) {
            Log.d(TAG, "Lock change ignored: editor mode is active")
            return false
        }

        val element = selectedElement ?: return false
        if (!elements.contains(element)) return false

        val historyBefore = captureEditorState()

        element.isLocked = locked
        transformMode = TransformMode.NONE
        isMovingElement = false

        Log.d(TAG, "Layer lock set: locked=$locked")
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )

        return true
    }

    fun bringSelectedElementToFront(): Boolean {
        val historyBefore = captureEditorState()
        val element = selectedElement ?: return false
        val currentIndex = elements.indexOf(element)
        if (currentIndex < 0 || currentIndex == elements.lastIndex) {
            return false
        }

        elements.removeAt(currentIndex)
        elements.add(element)
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )

        return true
    }

    /**
     * Moves the selected element to the bottom-most layer.
     */
    fun sendSelectedElementToBack(): Boolean {
        val historyBefore = captureEditorState()
        val element = selectedElement ?: return false
        val currentIndex = elements.indexOf(element)
        if (currentIndex <= 0) {
            return false
        }

        elements.removeAt(currentIndex)
        elements.add(0, element)
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )

        return true
    }

    /**
     * Moves the selected element up by one layer.
     */
    fun bringSelectedElementForward(): Boolean {
        val historyBefore = captureEditorState()
        val element = selectedElement ?: return false
        val currentIndex = elements.indexOf(element)
        if (currentIndex < 0 || currentIndex == elements.lastIndex) {
            return false
        }

        elements[currentIndex] = elements[currentIndex + 1].also {
            elements[currentIndex + 1] = element
        }
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )

        return true
    }

    /**
     * Moves the selected element down by one layer.
     */
    fun sendSelectedElementBackward(): Boolean {
        val historyBefore = captureEditorState()
        val element = selectedElement ?: return false
        val currentIndex = elements.indexOf(element)
        if (currentIndex <= 0) {
            return false
        }

        elements[currentIndex] = elements[currentIndex - 1].also {
            elements[currentIndex - 1] = element
        }
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )

        return true
    }

    fun clearElements() {
        clearAnnotationHistory()
        clearHistory()
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
        val historyBefore = captureEditorState()

        textElement.updateText(
            newText
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text element updated: $newText" )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateColor(
            newColor
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text color updated: $newColor" )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateColorRange(
            start,
            end,
            newColor
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text color range updated: " + "start=$start end=$end color=$newColor" )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.setColorRanges(ranges)
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text color ranges restored: ${ranges.size} ranges" )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateTextSize(
            newSize
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text size updated: " + textElement.textSize )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateBold(
            enabled
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text bold updated: " + textElement.bold )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateItalic(
            enabled
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text italic updated: " + textElement.italic )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateAlignment(
            alignment
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text alignment updated: " + textElement.alignment )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateFont(font)
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text font updated: ${textElement.getFontDisplayName()}" )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateBackground(
            enabled
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text background updated: " + enabled )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
        val historyBefore = captureEditorState()

        textElement.updateBackgroundColor(
            newColor
        )
        selectUpdatedTextElement(textElement)
        Log.d( TAG, "Text background color updated: " + newColor )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
         * Search backwards so the top-most element is selected first.
         *
         * Annotation paths are often very thin. Using only EditorElement.contains()
         * makes a freehand stroke difficult to select with a finger, especially
         * after it has been scaled on screen. Give annotations a small image-space
         * touch tolerance and fall back to the normal element hit test for all
         * other element types.
         */
        for (index in elements.indices.reversed()) {
            val element = elements[index]

            // Hidden layers remain available in the Layers panel but cannot
            // be selected or interacted with directly on the canvas.
            if (!element.isVisible) {
                continue
            }

            if (element is AnnotationElement) {
                if (isPointNearAnnotation(element, imageX, imageY)) {
                    return element
                }
            } else if (element.contains(imageX, imageY)) {
                return element
            }
        }

        return null
    }

    private fun isPointNearAnnotation(
        annotation: AnnotationElement,
        imageX: Float,
        imageY: Float
    ): Boolean {
        val bounds = annotation.getBounds()
        val tolerance =
            maxOf(
                annotation.strokeWidth * 1.5f,
                ANNOTATION_SELECTION_TOUCH_PADDING
            )

        if (imageX < bounds.left - tolerance ||
            imageX > bounds.right + tolerance ||
            imageY < bounds.top - tolerance ||
            imageY > bounds.bottom + tolerance
        ) {
            return false
        }

        val measure = android.graphics.PathMeasure(annotation.path, false)
        val position = FloatArray(2)
        val step = 12f.coerceAtLeast(annotation.strokeWidth / 2f)

        do {
            val length = measure.length
            if (length <= 0f) {
                if (measure.getPosTan(0f, position, null)) {
                    val dx = position[0] - imageX
                    val dy = position[1] - imageY
                    if (dx * dx + dy * dy <= tolerance * tolerance) return true
                }
            } else {
                var distanceAlongPath = 0f
                while (distanceAlongPath <= length) {
                    if (!measure.getPosTan(distanceAlongPath, position, null)) break
                    val dx = position[0] - imageX
                    val dy = position[1] - imageY
                    if (dx * dx + dy * dy <= tolerance * tolerance) return true
                    distanceAlongPath += step
                }

                if (measure.getPosTan(length, position, null)) {
                    val dx = position[0] - imageX
                    val dy = position[1] - imageY
                    if (dx * dx + dy * dy <= tolerance * tolerance) return true
                }
            }
        } while (measure.nextContour())

        return false
    }
    private fun getAnnotationDeleteHandlePosition(annotation: AnnotationElement): PointF {
        val bounds = annotation.getBounds()
        val topRight = imageToScreen(bounds.right, bounds.top) ?: return PointF()
        return PointF(
            topRight.x + ANNOTATION_DELETE_HANDLE_DISTANCE,
            topRight.y - ANNOTATION_DELETE_HANDLE_DISTANCE
        )
    }

    private fun isOnAnnotationDeleteHandle(eventX: Float, eventY: Float): Boolean {
        val annotation = selectedElement as? AnnotationElement ?: return false
        val handle = getAnnotationDeleteHandlePosition(annotation)
        return distance(eventX, eventY, handle.x, handle.y) <= ANNOTATION_DELETE_BUTTON_TOUCH_RADIUS
    }

    private fun drawAnnotationSelectionHandles(canvas: Canvas, annotation: AnnotationElement) {
        val bounds = annotation.getBounds()
        val topLeft = imageToScreen(bounds.left, bounds.top) ?: return
        val topRight = imageToScreen(bounds.right, bounds.top) ?: return
        val bottomRight = imageToScreen(bounds.right, bounds.bottom) ?: return
        val bottomLeft = imageToScreen(bounds.left, bounds.bottom) ?: return
        val deleteHandle = getAnnotationDeleteHandlePosition(annotation)

        val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.WHITE
            alpha = 220
        }
        val selectionPath = Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }
        canvas.drawPath(selectionPath, selectionPaint)
        canvas.drawLine(topRight.x, topRight.y, deleteHandle.x, deleteHandle.y, selectionPaint)

        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(220, 45, 45)
        }
        val buttonStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = Color.WHITE
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }

        // Large, high-contrast delete button with a visible X.
        canvas.drawCircle(
            deleteHandle.x,
            deleteHandle.y,
            ANNOTATION_DELETE_BUTTON_RADIUS,
            buttonPaint
        )
        canvas.drawCircle(
            deleteHandle.x,
            deleteHandle.y,
            ANNOTATION_DELETE_BUTTON_RADIUS,
            buttonStroke
        )
        val iconSize = ANNOTATION_DELETE_BUTTON_RADIUS * 0.38f
        canvas.drawLine(
            deleteHandle.x - iconSize,
            deleteHandle.y - iconSize,
            deleteHandle.x + iconSize,
            deleteHandle.y + iconSize,
            iconPaint
        )
        canvas.drawLine(
            deleteHandle.x + iconSize,
            deleteHandle.y - iconSize,
            deleteHandle.x - iconSize,
            deleteHandle.y + iconSize,
            iconPaint
        )

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
        if (shape.isLocked) {
            Log.d(TAG, "Shape rotation ignored: element is locked")
            beginElementHistoryGesture()
            transformMode = TransformMode.NONE
            return
        }
        beginElementHistoryGesture()
        transformMode = TransformMode.ROTATE
        initialRotation = shape.rotation
        val center = imageToScreen(shape.position.x, shape.position.y)
            ?: run {
                cancelElementHistoryGesture()
                transformMode = TransformMode.NONE
                return
            }
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
        markElementHistoryChanged()
        invalidate()
    }
    private fun startShapeResize(touchX: Float, touchY: Float) {
        val shape = selectedElement as? ShapeElement ?: return
        if (shape.isLocked) {
            Log.d(TAG, "Shape resize ignored: element is locked")
            beginElementHistoryGesture()
            transformMode = TransformMode.NONE
            return
        }
        beginElementHistoryGesture()
        transformMode = TransformMode.RESIZE
        initialElementScale = shape.scale
        val center = imageToScreen(shape.position.x, shape.position.y)
            ?: run {
                cancelElementHistoryGesture()
                transformMode = TransformMode.NONE
                return
            }
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
        markElementHistoryChanged()
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
        if (element.isLocked) {
            Log.d(TAG, "Text rotation ignored: element is locked")
            beginElementHistoryGesture()
            transformMode = TransformMode.NONE
            return
        }
        beginElementHistoryGesture()
        transformMode =
            TransformMode.ROTATE
        initialRotation =
            element.rotation
        /*
         * Calculate the center of the text
         * in screen coordinates.
         */
        val center =
            imageToScreen(element.position.x, element.position.y)
                ?: run {
                    cancelElementHistoryGesture()
                    transformMode = TransformMode.NONE
                    return
                }
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
        markElementHistoryChanged()
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
        if (element.isLocked) {
            Log.d(TAG, "Text resize ignored: element is locked")
            beginElementHistoryGesture()
            transformMode = TransformMode.NONE
            return
        }
        beginElementHistoryGesture()
        transformMode =
            TransformMode.RESIZE
        initialElementScale =
            element.scale
        val center =
            imageToScreen(element.position.x, element.position.y)
                ?: run {
                    cancelElementHistoryGesture()
                    transformMode = TransformMode.NONE
                    return
                }
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
        markElementHistoryChanged()
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

        if (element.isLocked) {
            Log.d(TAG, "Delete ignored: selected element is locked")
            return
        }
        Log.d( TAG, "Deleting selected element" )

        val historyBefore = captureEditorState()

        val annotationHistoryBeforeDelete =
            if (element is AnnotationElement) {
                annotationHistoryController.capture(elements)
            } else {
                null
            }
        elements.remove(
            element
        )
        element.isSelected = false
        selectedElement = null
        transformMode = TransformMode.NONE
        notifySelectionChanged()

        if (annotationHistoryBeforeDelete != null) {
            val annotationHistoryAfterDelete =
                annotationHistoryController.capture(elements)
            annotationHistoryController.record(
                before = annotationHistoryBeforeDelete,
                after = annotationHistoryAfterDelete
            )
            onAnnotationHistoryChanged?.invoke()
        }

        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
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
    /** Starts a new freehand annotation path in image coordinates. */
    private fun startFreehandPath(screenX: Float, screenY: Float): Boolean {
        val imagePoint = screenToImage(screenX, screenY) ?: return false

        val path = Path()
        path.moveTo(imagePoint.x, imagePoint.y)

        activeAnnotationPath = path
        activeAnnotationPointCount = 1
        return true
    }

    /** Adds the current screen point to the active image-space freehand path. */
    private fun appendFreehandPoint(screenX: Float, screenY: Float): Boolean {
        val path = activeAnnotationPath ?: return false
        val imagePoint = screenToImage(screenX, screenY) ?: return false

        path.lineTo(imagePoint.x, imagePoint.y)
        activeAnnotationPointCount++
        invalidate()
        return true
    }

    /** Commits or discards the currently active freehand path. */
    private fun finishActiveFreehandPath(commit: Boolean) {
        val path = activeAnnotationPath
        var historyCommitted = false

        if (
            commit &&
            activeAnnotationType != AnnotationType.ERASER &&
            path != null &&
            activeAnnotationPointCount >= 2
        ) {
            val annotation = annotationController.createAnnotation(
                path = path,
                annotationType = activeAnnotationType,
                color = annotationController.currentColor,
                strokeWidth = annotationController.currentStrokeWidth
            )

            addElement(annotation)
            historyCommitted = true

            Log.d(
                TAG,
                "Freehand annotation added. Total elements=${elements.size}"
            )
        }

        activeAnnotationPath = null
        activeAnnotationPointCount = 0
        activeAnnotationType = AnnotationType.FREEHAND

        if (historyCommitted) {
            finishAnnotationHistoryGesture()
        } else {
            cancelAnnotationHistoryGesture()
        }
    }

    /** Draws the in-progress freehand path without adding it to the element list. */
    private fun drawActiveFreehandPath(canvas: Canvas) {
        val path = activeAnnotationPath ?: return

        if (
            activeAnnotationType == AnnotationType.BLUR ||
            activeAnnotationType == AnnotationType.PIXELATE
        ) {
            val transformedPath = Path(path)
            transformedPath.transform(imageToScreenMatrix)
            blurPreviewPaint.strokeWidth = annotationController.currentStrokeWidth *
                    currentScreenScale()
            canvas.drawPath(transformedPath, blurPreviewPaint)
            return
        }

        val previewAnnotation = annotationController.createAnnotation(
            path = path,
            annotationType = activeAnnotationType,
            color = annotationController.currentColor,
            strokeWidth = annotationController.currentStrokeWidth
        )

        previewAnnotation.draw(
            canvas = canvas,
            matrix = imageToScreenMatrix
        )
    }

    private fun currentScreenScale(): Float {
        val values = FloatArray(9)
        imageToScreenMatrix.getValues(values)
        return hypot(
            values[Matrix.MSCALE_X].toDouble(),
            values[Matrix.MSKEW_X].toDouble()
        ).toFloat().coerceAtLeast(0.001f)
    }

    /** Draws the visible eraser cursor. */
    private fun drawActiveEraserPreview(canvas: Canvas) {
        val point = activeEraserPoint ?: return
        val imageRadius = annotationController.currentStrokeWidth / 2f
        val screenScale = currentScreenScale()
        val screenRadius = imageRadius * screenScale

        if (screenRadius <= 0f) return

        // The fill makes the erase area easy to see without hiding the image.
        canvas.drawCircle(
            point.x,
            point.y,
            screenRadius,
            eraserPreviewFillPaint
        )

        // The outline clearly shows the exact boundary of the erase area.
        canvas.drawCircle(
            point.x,
            point.y,
            screenRadius,
            eraserPreviewPaint
        )
    }

    /**
     * Erases annotation content at the supplied image-space point.
     * Returns true when at least one annotation changed.
     */
    private fun eraseAnnotationsAt(
        imageX: Float,
        imageY: Float
    ): Boolean {
        val radius = annotationController.currentStrokeWidth / 2f
        var changed = false
        val iterator = elements.listIterator()

        while (iterator.hasNext()) {
            val element = iterator.next()
            if (element !is AnnotationElement) continue

            val bounds = element.getBounds()
            if (imageX < bounds.left - radius ||
                imageX > bounds.right + radius ||
                imageY < bounds.top - radius ||
                imageY > bounds.bottom + radius
            ) {
                continue
            }

            val remains = element.eraseAt(
                x = imageX,
                y = imageY,
                radius = radius
            )

            changed = true

            if (!remains) {
                element.isSelected = false
                if (selectedElement === element) {
                    selectedElement = null
                }
                iterator.remove()
            }
        }

        if (changed) {
            notifySelectionChanged()
            invalidate()
        }

        return changed
    }

    /**
     * Handles one eraser point in screen coordinates.
     *
     * Eraser gestures are converted to image coordinates and sampled between
     * touch events so fast finger movement cannot jump over an annotation.
     */
    private fun eraseAtScreenPoint(screenX: Float, screenY: Float): Boolean {
        val imagePoint = screenToImage(screenX, screenY) ?: return false

        var changed = false
        activeEraserPoint = PointF(screenX, screenY)

        val previous = lastEraserImagePoint
        if (previous == null) {
            changed = eraseAnnotationsAt(
                imageX = imagePoint.x,
                imageY = imagePoint.y
            ) || changed
        } else {
            val dx = imagePoint.x - previous.x
            val dy = imagePoint.y - previous.y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val step = (annotationController.currentStrokeWidth / 2f)
                .coerceAtLeast(2f)
            val steps = (distance / step).toInt().coerceAtLeast(1)

            for (index in 1..steps) {
                val fraction = index.toFloat() / steps.toFloat()
                val sampleX = previous.x + dx * fraction
                val sampleY = previous.y + dy * fraction

                changed = eraseAnnotationsAt(
                    imageX = sampleX,
                    imageY = sampleY
                ) || changed
            }
        }

        lastEraserImagePoint = PointF(
            imagePoint.x,
            imagePoint.y
        )

        invalidate()
        return changed
    }

    /** Draws all committed blur annotations over a blurred copy of the image. */
    private fun drawBlurAnnotations(
        canvas: Canvas,
        sourceBitmap: Bitmap
    ) {
        val blurAnnotations = elements
            .asSequence()
            .filterIsInstance<AnnotationElement>()
            .filter { it.annotationType == AnnotationType.BLUR && it.isVisible }
            .toList()

        if (blurAnnotations.isEmpty()) return

        val blurred = getOrCreateBlurredBitmap(sourceBitmap) ?: return

        for (annotation in blurAnnotations) {
            val transformedPath = Path(annotation.path)
            transformedPath.transform(imageToScreenMatrix)

            canvas.saveLayer(null, null)
            canvas.drawBitmap(
                blurred,
                imageToScreenMatrix,
                bitmapPaint
            )

            blurMaskPaint.strokeWidth = annotation.strokeWidth * currentScreenScale()
            blurMaskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawPath(transformedPath, blurMaskPaint)
            blurMaskPaint.xfermode = null
            canvas.restore()
        }
    }

    /**
     * Draws all committed pixelate annotations over a pixelated copy of the image.
     *
     * The pixelated bitmap is cached per source bitmap so normal drawing does
     * not regenerate the effect for every frame.
     */
    private fun drawPixelateAnnotations(
        canvas: Canvas,
        sourceBitmap: Bitmap
    ) {
        val pixelateAnnotations = elements
            .asSequence()
            .filterIsInstance<AnnotationElement>()
            .filter { it.annotationType == AnnotationType.PIXELATE && it.isVisible }
            .toList()

        if (pixelateAnnotations.isEmpty()) return

        val pixelated = getOrCreatePixelatedBitmap(sourceBitmap) ?: return

        for (annotation in pixelateAnnotations) {
            val transformedPath = Path(annotation.path)
            transformedPath.transform(imageToScreenMatrix)

            /*
             * Build the actual stroked shape and use it as a canvas clip.
             *
             * The previous implementation used DST_IN on a full-canvas layer.
             * On the first render after creating a pixelate annotation, that
             * approach could leave the full pixelated bitmap visible instead
             * of restricting it to the finger path. Converting the stroke to
             * a fill path and clipping before drawing makes the mask explicit
             * and keeps the behavior deterministic on every render.
             */
            blurMaskPaint.style = Paint.Style.STROKE
            blurMaskPaint.strokeWidth =
                annotation.strokeWidth * currentScreenScale()
            blurMaskPaint.xfermode = null

            val maskPath = Path()
            blurMaskPaint.getFillPath(
                transformedPath,
                maskPath
            )

            canvas.save()
            canvas.clipPath(maskPath)

            // Draw the mosaic first so the original image detail is still
            // recognizable as pixel blocks.
            canvas.drawBitmap(
                pixelated,
                imageToScreenMatrix,
                bitmapPaint
            )

            // Apply the annotation's selected color as a translucent tint.
            // This makes the existing Annotation Color button work for
            // Pixelate without changing the underlying pixelation algorithm.
            pixelateColorPaint.color = annotation.color
            canvas.drawPath(maskPath, pixelateColorPaint)

            canvas.restore()
        }
    }

    /**
     * Returns a cached pixelated copy of the supplied bitmap.
     *
     * A block-average mosaic is used so the result remains compatible with
     * the project's existing Android/API configuration.
     */
    private fun getOrCreatePixelatedBitmap(source: Bitmap): Bitmap? {
        if (
            pixelatedBitmapSource === source &&
            pixelatedBitmap != null &&
            !pixelatedBitmap!!.isRecycled
        ) {
            return pixelatedBitmap
        }

        pixelatedBitmap = null
        pixelatedBitmapSource = null

        return try {
            val output = Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888
            )

            val pixels = IntArray(source.width * source.height)
            source.getPixels(
                pixels,
                0,
                source.width,
                0,
                0,
                source.width,
                source.height
            )

            // 16px blocks provide a visible mosaic while retaining enough
            // detail for the user to see the covered area.
            val blockSize = 16

            var blockTop = 0
            while (blockTop < source.height) {
                val blockBottom =
                    min(blockTop + blockSize, source.height)

                var blockLeft = 0
                while (blockLeft < source.width) {
                    val blockRight =
                        min(blockLeft + blockSize, source.width)

                    var a = 0
                    var r = 0
                    var g = 0
                    var b = 0
                    var count = 0

                    for (y in blockTop until blockBottom) {
                        val row = y * source.width

                        for (x in blockLeft until blockRight) {
                            val color = pixels[row + x]

                            a += Color.alpha(color)
                            r += Color.red(color)
                            g += Color.green(color)
                            b += Color.blue(color)
                            count++
                        }
                    }

                    if (count > 0) {
                        val average = Color.argb(
                            a / count,
                            r / count,
                            g / count,
                            b / count
                        )

                        for (y in blockTop until blockBottom) {
                            val row = y * source.width

                            for (x in blockLeft until blockRight) {
                                pixels[row + x] = average
                            }
                        }
                    }

                    blockLeft += blockSize
                }

                blockTop += blockSize
            }

            output.setPixels(
                pixels,
                0,
                source.width,
                0,
                0,
                source.width,
                source.height
            )

            pixelatedBitmap = output
            pixelatedBitmapSource = source
            output
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "Unable to create pixelated bitmap",
                exception
            )

            pixelatedBitmap = null
            pixelatedBitmapSource = null
            null
        }
    }

    /** Returns a cached blurred copy of the supplied bitmap. */
    /**
     * Returns a cached blurred copy of the supplied bitmap.
     *
     * Uses the existing CPU fallback blur implementation so this remains
     * compatible with the project's current Android/API configuration.
     */
    private fun getOrCreateBlurredBitmap(source: Bitmap): Bitmap? {

        if (
            blurredBitmapSource === source &&
            blurredBitmap != null &&
            !blurredBitmap!!.isRecycled
        ) {
            return blurredBitmap
        }

        clearBlurredBitmapCache()

        return try {
            val output = Bitmap.createBitmap(
                source.width,
                source.height,
                Bitmap.Config.ARGB_8888
            )

            drawFallbackBlur(
                source = source,
                output = output
            )

            blurredBitmap = output
            blurredBitmapSource = source

            output

        } catch (exception: Exception) {

            Log.e(
                TAG,
                "Unable to create blurred bitmap",
                exception
            )

            clearBlurredBitmapCache()

            null
        }
    }

    /**
     * Small separable box blur fallback for API levels below 31. The blur is
     * generated once and cached, so normal drawing does not repeatedly process
     * the image.
     */
    private fun drawFallbackBlur(source: Bitmap, output: Bitmap) {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)

        val temp = IntArray(pixels.size)
        val radius = 7
        val diameter = radius * 2 + 1

        for (y in 0 until source.height) {
            val row = y * source.width
            for (x in 0 until source.width) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val start = maxOf(0, x - radius)
                val end = minOf(source.width - 1, x + radius)
                for (sampleX in start..end) {
                    val color = pixels[row + sampleX]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                temp[row + x] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }

        for (x in 0 until source.width) {
            for (y in 0 until source.height) {
                var a = 0
                var r = 0
                var g = 0
                var b = 0
                var count = 0
                val start = maxOf(0, y - radius)
                val end = minOf(source.height - 1, y + radius)
                for (sampleY in start..end) {
                    val color = temp[sampleY * source.width + x]
                    a += Color.alpha(color)
                    r += Color.red(color)
                    g += Color.green(color)
                    b += Color.blue(color)
                    count++
                }
                pixels[y * source.width + x] = Color.argb(a / count, r / count, g / count, b / count)
            }
        }

        output.setPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    }

    private fun clearBlurredBitmapCache() {
        blurredBitmap = null
        blurredBitmapSource = null
        pixelatedBitmap = null
        pixelatedBitmapSource = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val currentBitmap =
            when {
                adjustmentModeActive -> adjustmentController.currentPreviewBitmap
                filterModeActive -> filterPreviewBitmap
                else -> bitmap
            } ?: return

        if (width <= 0 || height <= 0) {
            return
        }

        updateMatrices()

        // DRAW IMAGE
        editorRenderer.drawBitmap(
            canvas = canvas,
            bitmap = currentBitmap,
            imageToScreenMatrix = imageToScreenMatrix
        )

        // DRAW BLUR ANNOTATIONS BEFORE TEXT/SHAPES SO THOSE ELEMENTS REMAIN SHARP.
        drawBlurAnnotations(
            canvas = canvas,
            sourceBitmap = currentBitmap
        )

        // DRAW PIXELATE ANNOTATIONS BEFORE TEXT/SHAPES SO THOSE ELEMENTS
        // remain sharp and the pixelation only affects the selected region.
        drawPixelateAnnotations(
            canvas = canvas,
            sourceBitmap = currentBitmap
        )

        // PIXELATE annotations are visual effects rendered above, so exclude
        // only those effect elements from the normal element renderer. Every
        // existing Text/Shape/Freehand/Pen/Highlighter/Blur element remains
        // on the existing rendering pipeline.
        val drawableElements = elements.filter { element ->
            element.isVisible &&
                    !(element is AnnotationElement &&
                            element.annotationType == AnnotationType.PIXELATE)
        }

        editorRenderer.drawElements(
            canvas = canvas,
            elements = drawableElements,
            imageToScreenMatrix = imageToScreenMatrix
        )

        // DRAW ACTIVE ANNOTATION PREVIEW
        if (freehandModeActive) {
            drawActiveFreehandPath(canvas)
        }

        // DRAW ERASER CURSOR
        if (eraserModeActive) {
            drawActiveEraserPreview(canvas)
        }

        val selectedAnnotation = selectedElement as? AnnotationElement
        if (
            annotationSelectionVisible &&
            selectedAnnotation != null &&
            selectedAnnotation.isSelected
        ) {
            drawAnnotationSelectionHandles(canvas, selectedAnnotation)
        }

        // -----------------------------------------------------------------
        // SELECTED TEXT / SHAPE HANDLES
        // -----------------------------------------------------------------
        // Keep the existing Phase 8 selection/transform UI visible while
        // preserving the Phase 9 annotation selection UI above.
        val selectedText = selectedElement as? TextElement
        if (selectedText != null && selectedText.isSelected) {
            val bounds = selectedText.getBounds()

            val topLeft = imageToScreen(bounds.left, bounds.top) ?: PointF()
            val topRight = imageToScreen(bounds.right, bounds.top) ?: PointF()
            val bottomLeft = imageToScreen(bounds.left, bounds.bottom) ?: PointF()
            val bottomRight = imageToScreen(bounds.right, bounds.bottom) ?: PointF()

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

        /*
         * CROP MODE
         *
         * The crop selection must be rendered after the normal editor content
         * so the outside area is dimmed and the crop border/grid/handles stay
         * visible above every editor element.
         */
        if (cropModeActive) {
            drawCropSelection(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        /*
         * Annotation selection actions always have highest priority. This is
         * deliberately BEFORE eraser/freehand handling; otherwise a tap on
         * the delete button is interpreted as a new drawing gesture.
         */
        if (
            event.actionMasked == MotionEvent.ACTION_DOWN &&
            event.pointerCount == 1 &&
            selectedElement is AnnotationElement &&
            selectedElement?.isLocked != true &&
            isOnAnnotationDeleteHandle(event.x, event.y)
        ) {
            Log.d(TAG, "Annotation delete handle touched")
            deleteSelectedElement()
            isMovingElement = false
            isDragging = false
            transformMode = TransformMode.NONE
            invalidate()
            return true
        }

        // ERASER MODE
        //
        // Eraser is an exclusive interaction mode. It is checked before any
        // GestureDetector/freehand/element handling and therefore can never
        // create a new AnnotationElement.
        if (eraserModeActive) {
            // Defensive guard: even if another state was changed unexpectedly,
            // eraser input must never fall through to the drawing pipeline.
            freehandModeActive = false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetElementGestureState()
                    beginAnnotationHistoryGesture()
                    beginElementHistoryGesture()
                    activeEraserPoint = PointF(event.x, event.y)
                    lastEraserImagePoint = null

                    if (eraseAtScreenPoint(
                            event.x,
                            event.y
                        )
                    ) {
                        markElementHistoryChanged()
                    }

                    invalidate()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1) {
                        if (eraseAtScreenPoint(
                                event.x,
                                event.y
                            )
                        ) {
                            markElementHistoryChanged()
                        }
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    finishAnnotationHistoryGesture()
                    finishElementHistoryGesture()
                    activeEraserPoint = null
                    lastEraserImagePoint = null
                    isDragging = false
                    isMovingElement = false
                    transformMode = TransformMode.NONE
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    cancelAnnotationHistoryGesture()
                    cancelElementHistoryGesture()
                    activeEraserPoint = null
                    lastEraserImagePoint = null
                    isDragging = false
                    isMovingElement = false
                    transformMode = TransformMode.NONE
                    invalidate()
                    return true
                }
            }
        }

        // FREEHAND / PEN / HIGHLIGHTER MODE
        if (freehandModeActive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    /*
                     * If the user taps an existing annotation while the drawing
                     * mode is still active, treat that touch as selection rather
                     * than starting another stroke. This prevents accidental
                     * strokes and makes selection reliable even if the toolbar
                     * cancel action was not the last interaction.
                     */
                    val tappedImagePoint = screenToImage(event.x, event.y)
                    val tappedAnnotation = tappedImagePoint?.let { point ->
                        findElementAt(point.x, point.y) as? AnnotationElement
                    }

                    if (tappedAnnotation != null) {
                        freehandModeActive = false
                        eraserModeActive = false
                        activeAnnotationPath = null
                        activeAnnotationPointCount = 0
                        cancelAnnotationHistoryGesture()
                        resetElementGestureState()
                        selectElement(tappedAnnotation)
                        isDragging = true
                        isMovingElement = !tappedAnnotation.isLocked
                        lastTouchX = event.x
                        lastTouchY = event.y
                        return true
                    }

                    resetElementGestureState()
                    isDragging = true
                    lastTouchX = event.x
                    lastTouchY = event.y
                    beginAnnotationHistoryGesture()
                    if (!startFreehandPath(event.x, event.y)) {
                        cancelAnnotationHistoryGesture()
                    }
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (event.pointerCount == 1 &&
                        !scaleGestureDetector.isInProgress
                    ) {
                        appendFreehandPoint(event.x, event.y)
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    finishActiveFreehandPath(commit = true)
                    isDragging = false
                    isMovingElement = false
                    transformMode = TransformMode.NONE
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    finishActiveFreehandPath(commit = false)
                    isDragging = false
                    isMovingElement = false
                    transformMode = TransformMode.NONE
                    invalidate()
                    return true
                }
            }
        }

        /*
         * CROP MODE
         *
         * CropController owns crop handle detection, movement and resizing.
         * Crop touches must be handled before GestureDetector, ScaleGestureDetector,
         * and normal element interaction; otherwise the crop gesture falls through
         * to the regular editor pipeline and the crop rectangle cannot be changed.
         */
        if (cropModeActive) {
            cropController.handleTouch(event)
            return true
        }

        /*
         * First let GestureDetector process taps / double taps.
         */
        gestureDetector.onTouchEvent(event)

        /*
         * Always allow ScaleGestureDetector to process the event.
         */
        scaleGestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                transformMode = TransformMode.NONE

                /*
                 * A locked layer can still be selected and reordered from the
                 * Layers panel, but it cannot be edited through canvas gestures.
                 *
                 * IMPORTANT: the resize/rotate/delete handles sit outside the
                 * element bounds, so checking only findElementAt() is not enough.
                 * Block the selected locked element's body AND all of its floating
                 * handles before the normal handle pipeline gets a chance to start.
                 *
                 * Tapping a different element is still allowed so the user can
                 * switch selection away from the locked layer.
                 */
                if (selectedElement?.isLocked == true) {
                    val imagePoint = screenToImage(event.x, event.y)
                    val touchedElement = imagePoint?.let {
                        findElementAt(it.x, it.y)
                    }

                    val touchedSelectedLockedElement =
                        touchedElement === selectedElement

                    val touchedLockedHandle =
                        (selectedElement is ShapeElement &&
                                (isOnShapeDeleteHandle(event.x, event.y) ||
                                        isOnShapeRotationHandle(event.x, event.y) ||
                                        isOnShapeResizeHandle(event.x, event.y))) ||
                                (selectedElement is TextElement &&
                                        (isOnTextDeleteHandle(event.x, event.y) ||
                                                isOnRotationHandle(event.x, event.y) ||
                                                isOnResizeHandle(event.x, event.y))) ||
                                (selectedElement is AnnotationElement &&
                                        isOnAnnotationDeleteHandle(event.x, event.y))

                    if (touchedSelectedLockedElement || touchedLockedHandle) {
                        isMovingElement = false
                        transformMode = TransformMode.NONE
                        Log.d(TAG, "Locked element interaction ignored on canvas")
                        return true
                    }
                }

                /*
                 * CHECK FLOATING DELETE ACTIONS FIRST
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
                    isOnTextDeleteHandle(event.x, event.y)
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
                    isOnRotationHandle(event.x, event.y)
                ) {
                    startRotation(event.x, event.y)
                    isMovingElement = false
                    Log.d(TAG, "Rotation handle touched")
                    return true
                }

                /*
                 * CHECK RESIZE HANDLE
                 */
                if (
                    event.pointerCount == 1 &&
                    selectedElement is TextElement &&
                    isOnResizeHandle(event.x, event.y)
                ) {
                    startResize(event.x, event.y)
                    isMovingElement = false
                    Log.d(TAG, "Resize handle touched")
                    return true
                }

                /*
                 * CONVERT SCREEN TO IMAGE
                 */
                val imagePoint = screenToImage(event.x, event.y)

                if (imagePoint != null) {
                    /*
                     * CHECK ELEMENT
                     */
                    val touchedElement = findElementAt(
                        imagePoint.x,
                        imagePoint.y
                    )

                    if (touchedElement != null) {
                        selectElement(touchedElement)

                        if (!touchedElement.isLocked) {
                            beginElementHistoryGesture()
                        }

                        isMovingElement = true
                        Log.d(TAG, "Element touched")
                    } else {
                        /*
                         * EMPTY CANVAS
                         */
                        selectElement(null)
                        isMovingElement = false
                        Log.d(TAG, "Empty canvas touched")
                    }
                }

                return true
            }

            MotionEvent.ACTION_MOVE -> {
                /*
                 * ROTATE ELEMENT
                 */
                if (
                    transformMode == TransformMode.ROTATE &&
                    event.pointerCount == 1 &&
                    selectedElement?.isLocked != true
                ) {
                    if (selectedElement is ShapeElement) {
                        updateShapeRotation(event.x, event.y)
                    } else {
                        updateRotation(event.x, event.y)
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }

                /*
                 * RESIZE ELEMENT
                 */
                if (
                    transformMode == TransformMode.RESIZE &&
                    event.pointerCount == 1 &&
                    selectedElement?.isLocked != true
                ) {
                    if (selectedElement is ShapeElement) {
                        updateShapeResize(event.x, event.y)
                    } else {
                        updateResize(event.x, event.y)
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }

                /*
                 * NORMAL SINGLE FINGER MOVEMENT
                 * Multi-touch is handled by ScaleGestureDetector.
                 */
                if (
                    event.pointerCount == 1 &&
                    !scaleGestureDetector.isInProgress &&
                    isDragging
                ) {
                    if (
                        isMovingElement &&
                        selectedElement != null &&
                        !selectedElement!!.isLocked
                    ) {
                        /*
                         * MOVE ELEMENT
                         */
                        val previousPoint = screenToImage(
                            lastTouchX,
                            lastTouchY
                        )
                        val currentPoint = screenToImage(
                            event.x,
                            event.y
                        )

                        if (previousPoint != null && currentPoint != null) {
                            val dx = currentPoint.x - previousPoint.x
                            val dy = currentPoint.y - previousPoint.y

                            if (dx != 0f || dy != 0f) {
                                selectedElement?.moveBy(dx, dy)
                                markElementHistoryChanged()
                            }
                            Log.d(TAG, "Moving element dx=$dx dy=$dy")
                        }
                    } else {
                        /*
                         * MOVE IMAGE
                         */
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY

                        translationX += dx
                        translationY += dy
                        Log.d(TAG, "Panning image dx=$dx dy=$dy")
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                finishElementHistoryGesture()
                isDragging = false
                isMovingElement = false
                transformMode = TransformMode.NONE
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelElementHistoryGesture()
                isDragging = false
                isMovingElement = false
                transformMode = TransformMode.NONE
                return true
            }
        }
        return true
    }
}
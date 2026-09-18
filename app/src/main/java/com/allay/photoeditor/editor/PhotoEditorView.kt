package com.allay.photoeditor.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
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
import com.allay.photoeditor.editor.annotation.AnnotationInteractionController
import com.allay.photoeditor.editor.annotation.EditorAnnotationEffectController
import com.allay.photoeditor.editor.annotation.EditorAnnotationModeController
import com.allay.photoeditor.editor.history.EditorHistoryController
import com.allay.photoeditor.editor.history.EditorStateSnapshot
import com.allay.photoeditor.editor.layer.EditorLayerController
import com.allay.photoeditor.editor.selection.EditorSelectionController
import com.allay.photoeditor.editor.selection.EditorSelectionHandleController
import com.allay.photoeditor.editor.core.EditorElementStore
import com.allay.photoeditor.editor.core.EditorCoordinateMapper
import com.allay.photoeditor.editor.text.TextElementController
import com.allay.photoeditor.editor.gesture.ElementGestureController
import com.allay.photoeditor.editor.gesture.EditorTapGestureController
import com.allay.photoeditor.editor.crop.CropController
import com.allay.photoeditor.editor.drawing.EditorRenderer
import com.allay.photoeditor.editor.transform.EditorImageTransformOperations
import com.allay.photoeditor.editor.transform.TransformController
import com.allay.photoeditor.editor.viewport.EditorPanController
import com.allay.photoeditor.editor.viewport.EditorZoomController
import kotlin.math.min
private typealias CropAspectRatio = CropController.AspectRatio
private typealias CropHandle = CropController.Handle
private typealias TransformMode = ElementGestureController.TransformMode
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
    private var lastTouchX: Float
        get() = elementGestureController.getLastTouchX()
        set(value) = elementGestureController.setLastTouch(value, lastTouchY)

    private var lastTouchY: Float
        get() = elementGestureController.getLastTouchY()
        set(value) = elementGestureController.setLastTouch(lastTouchX, value)

    private var isDragging: Boolean
        get() = elementGestureController.isDragging
        set(value) = elementGestureController.setDragging(value)

    /**
     * True when the current gesture is moving an editor element.
     *
     * False means the gesture is being used for image panning.
     */
    private var isMovingElement: Boolean
        get() = elementGestureController.isMovingElement
        set(value) = elementGestureController.setMovingElement(value)

    private var transformMode: TransformMode
        get() = elementGestureController.transformMode
        set(value) = elementGestureController.setTransformMode(value)

    /** Clears only transient touch/element-transform state. */
    private fun resetGestureState() {
        elementGestureController.reset()
        activeCropHandle = CropHandle.NONE
    }

    /** Resets only the element gesture flags without affecting crop handle state. */
    private fun resetElementGestureState() {
        elementGestureController.reset()
    }

    /**
     * Editor element state is owned by EditorElementStore.
     * PhotoEditorView keeps the public API and coordinates the subsystems.
     *
     * This must be initialized before controllers whose constructors capture
     * elementStore.elements in their callbacks.
     */
    private val elementStore = EditorElementStore()
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
            elementStore.elements.map { element ->
                when (element) {
                    is TextElement -> element.copyForCropSession()
                    else -> element
                }
            }
        },
        getSelectedElementIndex = { selectedElementInternal?.let(elementStore.elements::indexOf) ?: -1 },
        getScaleFactor = { scaleFactor },
        getTranslationX = { translationX },
        getTranslationY = { translationY },
        resetGestureState = ::resetGestureState,
        onModeChanged = { active -> onRotationModeChanged?.invoke(active) },
        invalidate = ::invalidate
    )
    private val rotationModeActive: Boolean
        get() = transformController.isActive

    /**
     * Owns image Flip/Rotate operations while PhotoEditorView keeps the
     * existing public transform API and coordinates the editor subsystems.
     */
    private val transformOperations = EditorImageTransformOperations(
        tag = TAG,
        getBitmap = { bitmap },
        setBitmap = { bitmap = it },
        elements = elementStore.elements,
        bitmapPaint = bitmapPaint,
        transformController = transformController,
        isCropModeActive = { cropModeActive },
        getSelectedElement = { selectedElementInternal },
        setSelectedElement = { selectedElementInternal = it },
        getScaleFactor = { scaleFactor },
        setScaleFactor = { scaleFactor = it },
        getTranslationX = { translationX },
        setTranslationX = { translationX = it },
        getTranslationY = { translationY },
        setTranslationY = { translationY = it },
        captureHistoryState = { captureEditorState(includeBitmap = true) },
        recordHistory = { before, after ->
            recordEditorHistory(
                before = before,
                after = after
            )
        },
        resetTransform = ::resetTransform,
        resetGestureState = ::resetGestureState,
        notifySelectionChanged = ::notifySelectionChanged,
        onModeChanged = { active ->
            onRotationModeChanged?.invoke(active)
        },
        invalidate = ::invalidate
    )
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
        resetAnnotationInteractionState()
        annotationInteractionController.setAnnotationType(annotationType)
        if (annotationType == AnnotationType.ERASER) {
            annotationModeController.enterEraserMode()
        } else {
            annotationModeController.enterDrawingMode()
        }
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
        annotationInteractionController.reset()
        resetElementGestureState()
    }

    /**
     * Controls visibility of annotation selection handles independently from
     * the drawing state. The selected annotation itself is preserved when the
     * annotation toolbar is closed, so reopening the toolbar restores its
     * selection/delete affordance.
     */
    fun setAnnotationSelectionVisible(visible: Boolean) {
        annotationSelectionVisibleInternal = visible
        invalidate()
    }

    /**
     * Reopens annotation selection UI without entering a drawing tool.
     * Existing selected annotations are intentionally preserved.
     */
    fun enterAnnotationSelectionMode() {
        if (bitmap == null) return

        annotationModeController.enterSelectionMode()
        annotationInteractionController.reset()
        annotationInteractionController.clearEraserState()
        annotationSelectionVisibleInternal = true
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
        annotationInteractionController.clearEraserState()
        annotationModeController.exit()
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
            annotationHistoryController.capture(elementStore.elements)
    }

    /**
     * Records the annotation state after a completed drawing/eraser gesture.
     */
    private fun finishAnnotationHistoryGesture() {
        val before = annotationHistoryBeforeGesture
            ?: return

        val after =
            annotationHistoryController.capture(elementStore.elements)

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
        elementStore.elements.removeAll { element ->
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
                    snapshot.index.coerceIn(0, elementStore.elements.size)

                elementStore.elements.add(targetIndex, annotation)
            }

        selectedElementInternal?.isSelected = false
        selectedElementInternal = null
        transformMode = TransformMode.NONE

        notifySelectionChanged()
        invalidate()
    }

    /** Undoes the most recent annotation operation. */
    fun undoAnnotation(): Boolean {
        if (freehandModeActive || eraserModeActive) {
            finishActiveFreehandPath(commit = false)
            annotationModeController.exit()
            annotationInteractionController.clearEraserState()
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
            annotationModeController.exit()
            annotationInteractionController.clearEraserState()
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
     * Owns selected-element state and canvas hit testing.
     *
     * PhotoEditorView keeps the existing public API and delegates selection
     * behavior to this controller so Text, Shape and Annotation selection
     * continue to use the same pipeline.
     */
    private val selectionController = EditorSelectionController(
        elements = elementStore.elements,
        resetSelectionTransform = { transformMode = TransformMode.NONE },
        onSelectionChanged = { element ->
            onSelectionChanged?.invoke(element)
        },
        invalidate = ::invalidate,
        annotationSelectionTouchPadding = ANNOTATION_SELECTION_TOUCH_PADDING
    )

    /** Compatibility property; actual selection state lives in the controller. */
    private var selectedElementInternal: EditorElement?
        get() = selectionController.selectedElement
        set(value) = selectionController.setSelectedElement(value)

    /**
     * Owns layer ordering, visibility, locking and duplication while
     * PhotoEditorView remains the public editor API and selection coordinator.
     */
    private val layerController = EditorLayerController(
        elements = elementStore.elements,
        getSelectedElement = { selectedElementInternal },
        setSelectedElement = { selectedElementInternal = it },
        selectElement = ::selectElement,
        isEditorModeActive = { rotationModeActive || cropModeActive },
        captureHistoryState = { captureEditorState() },
        recordHistory = { before, after ->
            recordEditorHistory(
                before = before,
                after = after
            )
        },
        resetElementInteraction = {
            transformMode = TransformMode.NONE
            isMovingElement = false
        },
        notifySelectionChanged = ::notifySelectionChanged,
        invalidate = ::invalidate
    )

    /**
     * Global history snapshot captured when an adjustment session is about to
     * be committed. AdjustmentController invokes onAdjustmentsApplied only
     * after the newest preview has actually been committed.
     */
    private var adjustmentHistoryBefore: EditorStateSnapshot? = null

    private fun beginElementHistoryGesture() {
        elementGestureController.beginHistoryGesture()
    }

    private fun markElementHistoryChanged() {
        elementGestureController.markHistoryChanged()
    }

    private fun finishElementHistoryGesture() {
        elementGestureController.finishHistoryGesture()
    }

    private fun cancelElementHistoryGesture() {
        elementGestureController.cancelHistoryGesture()
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
    /**
     * Owns image/screen coordinate conversion while PhotoEditorView keeps
     * the existing public API and viewport state.
     */
    private val coordinateMapper = EditorCoordinateMapper(
        getBitmap = { bitmap },
        getViewWidth = { width },
        getViewHeight = { height },
        getScaleFactor = { scaleFactor },
        getTranslationX = { translationX },
        getTranslationY = { translationY }
    )

    /** Owns blur/pixelate rendering, previews and cached effect bitmaps. */
    private val annotationEffectController = EditorAnnotationEffectController(
        elements = elementStore.elements,
        coordinateMapper = coordinateMapper,
        annotationController = annotationController,
        bitmapPaint = bitmapPaint,
        annotationInteractionPathProvider = {
            annotationInteractionController.currentAnnotationPath
        },
        annotationInteractionTypeProvider = {
            annotationInteractionController.currentAnnotationType
        },
        eraserPointProvider = {
            annotationInteractionController.currentEraserPoint
        }
    )

    /**
     * Owns selection-handle geometry, hit testing and selection-handle
     * rendering. PhotoEditorView keeps the public APIs and gesture pipeline.
     */
    private val selectionHandleController = EditorSelectionHandleController(
        coordinateMapper = coordinateMapper,
        getSelectedElement = { selectedElementInternal },
        drawTextTransformHandles = { canvas, topLeft, topRight, bottomLeft, bottomRight, rotationHandle, resizeHandle ->
            editorRenderer.drawTextSelectionHandles(
                canvas = canvas,
                topLeft = topLeft,
                topRight = topRight,
                bottomLeft = bottomLeft,
                bottomRight = bottomRight,
                rotationHandle = rotationHandle,
                resizeHandle = resizeHandle
            )
        },
        rotationHandleDistance = ROTATION_HANDLE_DISTANCE,
        shapeDeleteHandleDistance = SHAPE_DELETE_HANDLE_DISTANCE,
        shapeDeleteButtonRadius = SHAPE_DELETE_BUTTON_RADIUS,
        shapeDeleteButtonTouchRadius = SHAPE_DELETE_BUTTON_TOUCH_RADIUS,
        textDeleteHandleDistance = TEXT_DELETE_HANDLE_DISTANCE,
        textDeleteButtonRadius = TEXT_DELETE_BUTTON_RADIUS,
        textDeleteButtonTouchRadius = TEXT_DELETE_BUTTON_TOUCH_RADIUS,
        annotationDeleteHandleDistance = ANNOTATION_DELETE_HANDLE_DISTANCE,
        annotationDeleteButtonRadius = ANNOTATION_DELETE_BUTTON_RADIUS,
        annotationDeleteButtonTouchRadius = ANNOTATION_DELETE_BUTTON_TOUCH_RADIUS,
        handleTouchRadius = HANDLE_TOUCH_RADIUS
    )

    /**
     * Owns TextElement-specific property editing while PhotoEditorView keeps
     * the existing public API and coordinates selection/history/invalidation.
     */
    /**
     * Owns transient annotation drawing and eraser interaction state while
     * PhotoEditorView remains the annotation mode coordinator.
     */
    private val annotationInteractionController = AnnotationInteractionController(
        coordinateMapper = coordinateMapper,
        annotationController = annotationController,
        elements = elementStore.elements,
        addElement = ::addElement,
        getSelectedElement = { selectedElementInternal },
        setSelectedElement = { selectedElementInternal = it },
        notifySelectionChanged = ::notifySelectionChanged,
        finishAnnotationHistoryGesture = ::finishAnnotationHistoryGesture,
        cancelAnnotationHistoryGesture = ::cancelAnnotationHistoryGesture,
        invalidate = ::invalidate
    )

    /**
     * Owns annotation mode flags while PhotoEditorView keeps the existing
     * public annotation APIs and delegates drawing/eraser interaction to the
     * existing AnnotationInteractionController.
     */
    private val annotationModeController = EditorAnnotationModeController()


    /** Compatibility accessors keep the existing annotation pipeline unchanged. */
    private var freehandModeActive: Boolean
        get() = annotationModeController.isFreehandActive
        set(value) {
            if (value) {
                annotationModeController.enterDrawingMode()
            } else {
                annotationModeController.stopDrawingModes()
            }
        }

    private var eraserModeActive: Boolean
        get() = annotationModeController.isEraserActive
        set(value) {
            if (value) {
                annotationModeController.enterEraserMode()
            } else {
                annotationModeController.stopDrawingModes()
            }
        }

    private var annotationSelectionVisibleInternal: Boolean
        get() = annotationModeController.isSelectionVisible
        set(value) = annotationModeController.setSelectionVisible(value)

    private val textElementController = TextElementController(
        elements = elementStore.elements,
        captureHistoryState = { captureEditorState() },
        recordHistory = { before, after ->
            recordEditorHistory(
                before = before,
                after = after
            )
        },
        selectElement = ::selectElement,
        notifySelectionChanged = ::notifySelectionChanged,
        invalidate = ::invalidate
    )

    /**
     * Compatibility accessors keep the existing renderer/annotation pipeline
     * unchanged while the matrices are now owned by EditorCoordinateMapper.
     */
    private val imageToScreenMatrix: Matrix
        get() = coordinateMapper.imageToScreenMatrix

    private val screenToImageMatrix: Matrix
        get() = coordinateMapper.screenToImageMatrix

    /**
     * Owns move / resize / rotate element gestures while PhotoEditorView
     * remains the public coordinator.
     */
    private val elementGestureController = ElementGestureController(
        coordinateMapper = coordinateMapper,
        getSelectedElement = { selectedElementInternal },
        findElementAt = ::findElementAt,
        selectElement = ::selectElement,
        captureHistoryState = { captureEditorState() },
        recordHistory = { before, after ->
            recordEditorHistory(
                before = before,
                after = after
            )
        },
        invalidate = ::invalidate,
        isShapeDeleteHandle = ::isOnShapeDeleteHandle,
        isShapeRotationHandle = ::isOnShapeRotationHandle,
        isShapeResizeHandle = ::isOnShapeResizeHandle,
        isTextDeleteHandle = ::isOnTextDeleteHandle,
        isTextRotationHandle = ::isOnRotationHandle,
        isTextResizeHandle = ::isOnResizeHandle,
        minElementScale = MIN_ELEMENT_SCALE,
        maxElementScale = MAX_ELEMENT_SCALE,
        handleTouchRadius = HANDLE_TOUCH_RADIUS
    )

    /**
     * Owns pinch-zoom interaction while PhotoEditorView remains the
     * public touch-event coordinator.
     */
    private val zoomController = EditorZoomController(
        context = context,
        getScaleFactor = { scaleFactor },
        setScaleFactor = { scaleFactor = it },
        isElementTransformActive = { transformMode != TransformMode.NONE },
        isCropModeActive = { cropModeActive },
        minScale = MIN_SCALE,
        maxScale = MAX_SCALE,
        invalidate = ::invalidate
    )

    /**
     * Owns single-finger image panning while PhotoEditorView remains the
     * public touch-event coordinator.
     */
    private val panController = EditorPanController(
        getTranslationX = { translationX },
        getTranslationY = { translationY },
        setTranslationX = { translationX = it },
        setTranslationY = { translationY = it },
        isScaleGestureInProgress = { zoomController.isInProgress },
        invalidate = ::invalidate
    )

    /**
     * Owns tap and double-tap recognition while PhotoEditorView remains the
     * public touch-event coordinator.
     */
    private val tapGestureController = EditorTapGestureController(
        context = context,
        isCropModeActive = { cropModeActive },
        screenToImage = ::screenToImage,
        findElementAt = ::findElementAt,
        selectElement = ::selectElement,
        onEditTextRequested = { onEditTextRequested }
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
        annotationEffectController.clearCaches()
        clearAnnotationHistory()
        clearHistory()
        panController.reset()
        cancelElementHistoryGesture()
        adjustmentHistoryBefore = null
        this.bitmap = bitmap
        resetTransform()
        elementStore.elements.clear()
        selectedElementInternal = null
        transformMode = TransformMode.NONE
        cropModeActive = false
        cropRectImage = null
        cropController.clearSession()
        transformOperations.clearSession()
        filterController.clear()
        adjustmentController.clearSession()
        annotationModeController.reset()
        annotationInteractionController.reset()
        annotationInteractionController.setAnnotationType(AnnotationType.FREEHAND)
        annotationInteractionController.clearEraserState()
        notifySelectionChanged()
        onCropModeChanged?.invoke(false)
        Log.d( TAG, "Image set: ${bitmap.width} x ${bitmap.height}" )
        invalidate()
    }
    fun clearImage() {
        annotationEffectController.clearCaches()
        clearAnnotationHistory()
        clearHistory()
        panController.reset()
        cancelElementHistoryGesture()
        adjustmentHistoryBefore = null
        bitmap = null
        elementStore.elements.clear()
        selectedElementInternal = null
        resetTransform()
        transformMode = TransformMode.NONE
        cropModeActive = false
        activeCropHandle = CropHandle.NONE
        cropController.clearSession()
        transformOperations.clearSession()
        adjustmentController.clearSession()
        annotationModeController.reset()
        annotationInteractionController.reset()
        annotationInteractionController.setAnnotationType(AnnotationType.FREEHAND)
        annotationInteractionController.clearEraserState()
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

        val annotationElements = elementStore.elements
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
                annotationEffectController.getOrCreateBlurredBitmap(sourceBitmap)
            } else {
                null
            }

            val pixelatedSource = if (
                annotationElements.any {
                    it.annotationType == AnnotationType.PIXELATE
                }
            ) {
                annotationEffectController.getOrCreatePixelatedBitmap(sourceBitmap)
            } else {
                null
            }

            annotationElements.forEach { annotation ->
                when (annotation.annotationType) {
                    AnnotationType.BLUR -> {
                        val blurred = blurredSource ?: return@forEach

                        annotationEffectController.blurMaskPaint.style = Paint.Style.STROKE
                        annotationEffectController.blurMaskPaint.strokeWidth = annotation.strokeWidth
                        annotationEffectController.blurMaskPaint.xfermode = null

                        val maskPath = Path()
                        annotationEffectController.blurMaskPaint.getFillPath(
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

                        annotationEffectController.blurMaskPaint.style = Paint.Style.STROKE
                        annotationEffectController.blurMaskPaint.strokeWidth = annotation.strokeWidth
                        annotationEffectController.blurMaskPaint.xfermode = null

                        val maskPath = Path()
                        annotationEffectController.blurMaskPaint.getFillPath(
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

                        annotationEffectController.pixelateColorPaint.color = annotation.color
                        outputCanvas.drawPath(
                            maskPath,
                            annotationEffectController.pixelateColorPaint
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
            annotationEffectController.clearCaches()

            elementStore.elements.removeAll { element ->
                element is AnnotationElement
            }

            selectedElementInternal = null
            annotationSelectionVisibleInternal = false
            annotationInteractionController.currentAnnotationPath = null
            annotationInteractionController.reset()
            annotationInteractionController.clearEraserState()
            annotationModeController.exit()
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
        transformOperations.flipHorizontal()
    }
    fun flipVertical() {
        transformOperations.flipVertical()
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
            elements = elementStore.elements
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
        selectedElementInternal?.isSelected = false
        selectedElementInternal = null
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
            val iterator = elementStore.elements.iterator()
            while (iterator.hasNext()) {
                val element = iterator.next()
                val elementBounds = element.getBounds()
                if (!RectF.intersects(
                        cropBounds,
                        elementBounds
                    )
                ) {
                    element.isSelected = false
                    if (selectedElementInternal === element) {
                        selectedElementInternal = null
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
        selectedElementInternal = null
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

        Log.d( TAG, "Crop applied: ${cropWidth}x${cropHeight}, " + "origin=($left,$top), " + "elements=${elementStore.elements.size}" )
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
        elementStore.elements.clear()
        elementStore.elements.addAll(session.elements)
        selectedElementInternal = session.elements.getOrNull(session.selectedIndex)
        // Re-apply selection state exactly as it was before crop mode.
        elementStore.elements.forEach { element ->
            element.isSelected = element === selectedElementInternal
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
        Log.d( TAG, "Crop cancelled. Original image restored: " + "${session.bitmap?.width}x${session.bitmap?.height}, " + "elements=${elementStore.elements.size}" )
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
        return elementStore.elements.map { element ->
            when (element) {
                is TextElement -> element.copyForCropSession()
                else -> element
            }
        }
    }
    /** Returns the selected element index for the current Crop session. */
    private fun getSelectedElementIndexForCropSession(): Int = elementStore.elements.indexOf(selectedElementInternal)
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
    /**
     * Applies the existing clockwise 90-degree image-coordinate transform to
     * a TextElement while Crop Mode is active.
     *
     * This helper remains in PhotoEditorView because crop rotation is owned by
     * CropController/session logic and is intentionally separate from the
     * normal committed transform operations.
     */
    private fun transformTextForRotateRight90(
        textElement: TextElement,
        oldImageHeight: Float
    ) {
        val oldX = textElement.position.x
        val oldY = textElement.position.y

        textElement.position.x = oldImageHeight - oldY
        textElement.position.y = oldX
        textElement.rotation = normalizeRotation(
            textElement.rotation + 90f
        )
    }

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
            elementStore.elements.forEach { element ->
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
                        "elements=${elementStore.elements.size}"
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
     * Enters the temporary Transform Mode.
     *
     * All Flip/Rotate operations made while this mode is active are previews
     * until Apply is pressed. Cancel restores the complete editor state.
     */
    fun enterTransformMode() {
        transformOperations.enter()
    }

    fun isRotationMode(): Boolean = transformOperations.isActive()

    /** Commits the current transform preview. */
    fun applyRotation() {
        transformOperations.apply()
    }

    /** Rotates the current image 90 degrees counter-clockwise. */
    fun rotateLeft90() {
        transformOperations.rotateLeft90()
    }

    /** Rotates the current image 90 degrees clockwise. */
    fun rotateRight90() {
        transformOperations.rotateRight90()
    }

    /** Restores the exact editor state captured before transform preview began. */
    fun cancelRotation() {
        transformOperations.cancel()
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
    private fun getFitScale(): Float =
        coordinateMapper.getFitScale()

    private fun updateMatrices() {
        coordinateMapper.updateMatrices()
    }

    fun screenToImage(
        screenX: Float,
        screenY: Float
    ): PointF? =
        coordinateMapper.screenToImage(
            screenX,
            screenY
        )

    private fun imageToScreenOrOrigin(imageX: Float, imageY: Float): PointF =
        imageToScreen(imageX, imageY) ?: PointF()

    fun imageToScreen(
        imageX: Float,
        imageY: Float
    ): PointF? =
        coordinateMapper.imageToScreen(
            imageX,
            imageY
        )

    fun addElement(
        element: EditorElement
    ) {
        val historyBefore = captureEditorState()

        /*
         * Deselect previous element.
         */
        selectedElementInternal?.isSelected = false
        /*
         * New element becomes selected.
         */
        element.isSelected = true
        selectedElementInternal = element
        elementStore.elements.add(
            element
        )
        Log.d( TAG, "Element added. Total elements: " + elementStore.elements.size )
        notifySelectionChanged()
        invalidate()

        recordEditorHistory(
            before = historyBefore,
            after = captureEditorState()
        )
    }
    fun getElements(): List<EditorElement> = elementStore.elements

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
        val elementCopies = elementStore.elements.map { element ->
            element.duplicate().also { copy ->
                copy.isSelected = false
                copy.isVisible = element.isVisible
                copy.isLocked = element.isLocked
            }
        }

        return EditorStateSnapshot(
            elements = elementCopies,
            selectedElementIndex = elementStore.elements.indexOf(selectedElementInternal),
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
            annotationEffectController.clearCaches()
        }

        selectedElementInternal?.isSelected = false
        elementStore.elements.clear()
        elementStore.elements.addAll(
            state.elements.map { element ->
                element.duplicate().also { copy ->
                    copy.isSelected = false
                    copy.isVisible = element.isVisible
                    copy.isLocked = element.isLocked
                }
            }
        )

        selectedElementInternal = elementStore.elements.getOrNull(
            state.selectedElementIndex
        )

        elementStore.elements.forEach { element ->
            element.isSelected = element === selectedElementInternal
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
            annotationModeController.exit()
            annotationInteractionController.clearEraserState()
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
            annotationModeController.exit()
            annotationInteractionController.clearEraserState()
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


    /** Returns the number of editable layers currently in the editor. */
    fun getLayerCount(): Int = layerController.getLayerCount()

    /** Returns the editor element at the requested layer index. */
    fun getLayer(index: Int): EditorElement? = layerController.getLayer(index)

    /** Returns the current layer index of the supplied editor element. */
    fun getLayerIndex(element: EditorElement): Int = layerController.getLayerIndex(element)

    /** Selects an existing element by its layer index. */
    fun selectLayer(index: Int): Boolean = layerController.selectLayer(index)

    /** Duplicates the selected element and inserts it immediately above the original. */
    fun duplicateSelectedElement(): Boolean = layerController.duplicateSelectedElement()

    /** Toggles visibility of the currently selected layer. */
    fun toggleSelectedElementVisibility(): Boolean =
        layerController.toggleSelectedElementVisibility()

    /** Toggles the lock state of the currently selected layer. */
    fun toggleSelectedElementLock(): Boolean =
        layerController.toggleSelectedElementLock()

    /** Sets the lock state of the selected layer. */
    fun setSelectedElementLocked(locked: Boolean): Boolean =
        layerController.setSelectedElementLocked(locked)

    /** Moves the selected element to the top-most layer. */
    fun bringSelectedElementToFront(): Boolean =
        layerController.bringSelectedElementToFront()

    /** Moves the selected element to the bottom-most layer. */
    fun sendSelectedElementToBack(): Boolean =
        layerController.sendSelectedElementToBack()

    /** Moves the selected element up by one layer. */
    fun bringSelectedElementForward(): Boolean =
        layerController.bringSelectedElementForward()

    /** Moves the selected element down by one layer. */
    fun sendSelectedElementBackward(): Boolean =
        layerController.sendSelectedElementBackward()

    /** Updates an existing TextElement while preserving the public API. */
    fun updateTextElement(
        textElement: TextElement,
        newText: String
    ) = textElementController.updateTextElement(textElement, newText)

    /** Updates the color of an existing TextElement. */
    fun updateTextElementColor(
        textElement: TextElement,
        newColor: Int
    ) = textElementController.updateTextElementColor(textElement, newColor)

    /** Updates the color of a selected range inside a TextElement. */
    fun updateTextElementColorRange(
        textElement: TextElement,
        start: Int,
        end: Int,
        newColor: Int
    ) = textElementController.updateTextElementColorRange(
        textElement,
        start,
        end,
        newColor
    )

    /** Restores previously saved text color ranges. */
    fun restoreTextElementColorRanges(
        textElement: TextElement,
        ranges: List<TextElement.TextColorRange>
    ) = textElementController.restoreTextElementColorRanges(textElement, ranges)

    /** Updates the size of an existing TextElement. */
    fun updateTextElementSize(
        textElement: TextElement,
        newSize: Float
    ) = textElementController.updateTextElementSize(textElement, newSize)

    /** Updates the Bold state. */
    fun updateTextElementBold(
        textElement: TextElement,
        enabled: Boolean
    ) = textElementController.updateTextElementBold(textElement, enabled)

    /** Updates the Italic state. */
    fun updateTextElementItalic(
        textElement: TextElement,
        enabled: Boolean
    ) = textElementController.updateTextElementItalic(textElement, enabled)

    /** Updates text alignment. */
    fun updateTextElementAlignment(
        textElement: TextElement,
        alignment: TextElement.TextAlignment
    ) = textElementController.updateTextElementAlignment(textElement, alignment)

    /** Updates the typeface of an existing TextElement. */
    fun updateTextElementFont(
        textElement: TextElement,
        font: TextElement.TextFont
    ) = textElementController.updateTextElementFont(textElement, font)

    /** Updates the background enabled state of an existing TextElement. */
    fun updateTextElementBackground(
        textElement: TextElement,
        enabled: Boolean
    ) = textElementController.updateTextElementBackground(textElement, enabled)

    /** Updates the background color of an existing TextElement. */
    fun updateTextElementBackgroundColor(
        textElement: TextElement,
        newColor: Int
    ) = textElementController.updateTextElementBackgroundColor(
        textElement,
        newColor
    )

    private fun selectElement(
        element: EditorElement?
    ) {
        selectionController.selectElement(element)
    }

    private fun notifySelectionChanged() {
        selectionController.notifySelectionChanged()
    }

    fun getSelectedElement(): EditorElement? =
        selectionController.selectedElement

    private fun findElementAt(
        imageX: Float,
        imageY: Float
    ): EditorElement? =
        selectionController.findElementAt(
            imageX = imageX,
            imageY = imageY
        )

    private fun getAnnotationDeleteHandlePosition(annotation: AnnotationElement): PointF =
        selectionHandleController.getAnnotationDeleteHandlePosition(annotation)

    private fun isOnAnnotationDeleteHandle(eventX: Float, eventY: Float): Boolean =
        selectionHandleController.isOnAnnotationDeleteHandle(eventX, eventY)

    private fun drawAnnotationSelectionHandles(
        canvas: Canvas,
        annotation: AnnotationElement
    ) = selectionHandleController.drawAnnotationSelectionHandles(canvas, annotation)

    private fun getShapeResizeHandlePosition(shape: ShapeElement): PointF =
        selectionHandleController.getShapeResizeHandlePosition(shape)

    private fun getShapeRotationHandlePosition(shape: ShapeElement): PointF =
        selectionHandleController.getShapeRotationHandlePosition(shape)

    private fun isOnShapeRotationHandle(eventX: Float, eventY: Float): Boolean =
        selectionHandleController.isOnShapeRotationHandle(eventX, eventY)

    private fun getShapeDeleteHandlePosition(shape: ShapeElement): PointF =
        selectionHandleController.getShapeDeleteHandlePosition(shape)

    private fun isOnShapeDeleteHandle(eventX: Float, eventY: Float): Boolean =
        selectionHandleController.isOnShapeDeleteHandle(eventX, eventY)

    private fun isOnShapeResizeHandle(eventX: Float, eventY: Float): Boolean =
        selectionHandleController.isOnShapeResizeHandle(eventX, eventY)

    private fun drawShapeSelectionHandles(canvas: Canvas, shape: ShapeElement) =
        selectionHandleController.drawShapeSelectionHandles(canvas, shape)

    private fun getTextDeleteHandlePosition(textElement: TextElement): PointF =
        selectionHandleController.getTextDeleteHandlePosition(textElement)

    private fun isOnTextDeleteHandle(eventX: Float, eventY: Float): Boolean =
        selectionHandleController.isOnTextDeleteHandle(eventX, eventY)

    private fun drawTextDeleteButton(canvas: Canvas, textElement: TextElement) =
        selectionHandleController.drawTextDeleteButton(canvas, textElement)

    private fun getRotationHandlePosition(textElement: TextElement): PointF =
        selectionHandleController.getRotationHandlePosition(textElement)

    private fun getResizeHandlePosition(textElement: TextElement): PointF =
        selectionHandleController.getResizeHandlePosition(textElement)

    private fun isOnRotationHandle(eventX: Float, eventY: Float): Boolean =
        selectionHandleController.isOnRotationHandle(eventX, eventY)

    private fun isOnResizeHandle(eventX: Float, eventY: Float): Boolean =
        selectionHandleController.isOnResizeHandle(eventX, eventY)

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        kotlin.math.hypot(x2 - x1, y2 - y1)

    fun deleteSelectedElement() {
        if (rotationModeActive || cropModeActive) {
            Log.d(TAG, "Delete ignored: editor mode is active")
            return
        }
        val element =
            selectedElementInternal
                ?: return

        if (element.isLocked) {
            Log.d(TAG, "Delete ignored: selected element is locked")
            return
        }
        Log.d( TAG, "Deleting selected element" )

        val historyBefore = captureEditorState()

        val annotationHistoryBeforeDelete =
            if (element is AnnotationElement) {
                annotationHistoryController.capture(elementStore.elements)
            } else {
                null
            }
        elementStore.elements.remove(
            element
        )
        element.isSelected = false
        selectedElementInternal = null
        transformMode = TransformMode.NONE
        notifySelectionChanged()

        if (annotationHistoryBeforeDelete != null) {
            val annotationHistoryAfterDelete =
                annotationHistoryController.capture(elementStore.elements)
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
        return annotationInteractionController.startFreehandPath(
            screenX = screenX,
            screenY = screenY
        )
    }

    /** Adds the current screen point to the active image-space freehand path. */
    private fun appendFreehandPoint(screenX: Float, screenY: Float): Boolean {
        return annotationInteractionController.appendFreehandPoint(
            screenX = screenX,
            screenY = screenY
        )
    }

    /** Commits or discards the currently active freehand path. */
    private fun finishActiveFreehandPath(commit: Boolean) {
        annotationInteractionController.finishActiveFreehandPath(commit)
    }

    /** Handles one eraser point in screen coordinates through the annotation interaction controller. */
    private fun eraseAtScreenPoint(screenX: Float, screenY: Float): Boolean {
        return annotationInteractionController.eraseAtScreenPoint(
            screenX = screenX,
            screenY = screenY
        )
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
        annotationEffectController.drawBlurAnnotations(
            canvas = canvas,
            sourceBitmap = currentBitmap
        )

        // DRAW PIXELATE ANNOTATIONS BEFORE TEXT/SHAPES SO THOSE ELEMENTS
        // remain sharp and the pixelation only affects the selected region.
        annotationEffectController.drawPixelateAnnotations(
            canvas = canvas,
            sourceBitmap = currentBitmap
        )

        // PIXELATE annotations are visual effects rendered above, so exclude
        // only those effect elements from the normal element renderer. Every
        // existing Text/Shape/Freehand/Pen/Highlighter/Blur element remains
        // on the existing rendering pipeline.
        val drawableElements = elementStore.elements.filter { element ->
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
            annotationEffectController.drawActiveFreehandPath(canvas)
        }

        // DRAW ERASER CURSOR
        if (eraserModeActive) {
            annotationEffectController.drawActiveEraserPreview(canvas)
        }

        val selectedAnnotation = selectedElementInternal as? AnnotationElement
        if (
            annotationSelectionVisibleInternal &&
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
        val selectedText = selectedElementInternal as? TextElement
        if (selectedText != null && selectedText.isSelected) {
            selectionHandleController.drawTextSelectionHandles(
                canvas = canvas,
                textElement = selectedText
            )

            selectionHandleController.drawTextDeleteButton(
                canvas = canvas,
                textElement = selectedText
            )
        }

        val selectedShape = selectedElementInternal as? ShapeElement
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
            selectedElementInternal is AnnotationElement &&
            selectedElementInternal?.isLocked != true &&
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
            // Defensive guard: the mode controller keeps eraser mode exclusive,
            // so eraser input cannot fall through to the drawing pipeline.

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    resetElementGestureState()
                    beginAnnotationHistoryGesture()
                    beginElementHistoryGesture()
                    annotationInteractionController.currentEraserPoint = PointF(event.x, event.y)
                    annotationInteractionController.clearEraserState()

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
                    annotationInteractionController.currentEraserPoint = null
                    annotationInteractionController.clearEraserState()
                    isDragging = false
                    isMovingElement = false
                    transformMode = TransformMode.NONE
                    invalidate()
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    cancelAnnotationHistoryGesture()
                    cancelElementHistoryGesture()
                    annotationInteractionController.currentEraserPoint = null
                    annotationInteractionController.clearEraserState()
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
                        annotationModeController.enterSelectionMode()
                        annotationInteractionController.currentAnnotationPath = null
                        annotationInteractionController.reset()
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
                        !zoomController.isInProgress
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
        tapGestureController.onTouchEvent(event)

        /*
         * Always allow ScaleGestureDetector to process the event.
         */
        zoomController.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                transformMode = TransformMode.NONE

                val handledByElementGesture = elementGestureController.handleActionDown(
                    event = event,
                    onDeleteHandle = {
                        when {
                            selectedElementInternal is ShapeElement &&
                                    isOnShapeDeleteHandle(event.x, event.y) -> {
                                Log.d(TAG, "Shape delete handle touched")
                                deleteSelectedElement()
                                true
                            }

                            selectedElementInternal is TextElement &&
                                    isOnTextDeleteHandle(event.x, event.y) -> {
                                Log.d(TAG, "Text delete button touched")
                                deleteSelectedElement()
                                true
                            }

                            else -> false
                        }
                    }
                )

                if (handledByElementGesture) {
                    return true
                }

                /*
                 * EMPTY CANVAS / IMAGE PAN
                 */
                elementGestureController.setDragging(true)
                elementGestureController.setMovingElement(false)
                elementGestureController.setLastTouch(event.x, event.y)
                panController.begin(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (elementGestureController.handleActionMove(event)) {
                    return true
                }

                /*
                 * NORMAL SINGLE FINGER IMAGE PANNING
                 * Multi-touch is handled by ScaleGestureDetector.
                 */
                if (
                    event.pointerCount == 1 &&
                    isDragging &&
                    !isMovingElement
                ) {
                    if (panController.move(event)) {
                        Log.d(TAG, "Panning image")
                    }
                }

                return true
            }

            MotionEvent.ACTION_UP -> {
                elementGestureController.finishAction()
                panController.finish()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                elementGestureController.cancelAction()
                panController.cancel()
                return true
            }
        }
        return true
    }
}
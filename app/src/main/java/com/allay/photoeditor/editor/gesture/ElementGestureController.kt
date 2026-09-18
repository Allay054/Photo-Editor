package com.allay.photoeditor.editor.gesture

import android.view.MotionEvent
import android.util.Log
import com.allay.photoeditor.editor.core.EditorCoordinateMapper
import com.allay.photoeditor.editor.history.EditorStateSnapshot
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.ShapeElement
import com.allay.photoeditor.model.TextElement
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Owns interactive editor-element gesture state.
 *
 * PhotoEditorView remains the coordinator. This controller owns:
 * - element move gestures
 * - element resize gestures
 * - element rotation gestures
 * - transform handle gesture state
 * - grouped global history for one continuous element gesture
 *
 * Existing element models and public PhotoEditorView APIs remain unchanged.
 */
class ElementGestureController(
    private val coordinateMapper: EditorCoordinateMapper,
    private val getSelectedElement: () -> EditorElement?,
    private val findElementAt: (Float, Float) -> EditorElement?,
    private val selectElement: (EditorElement?) -> Unit,
    private val captureHistoryState: () -> EditorStateSnapshot,
    private val recordHistory: (EditorStateSnapshot, EditorStateSnapshot) -> Unit,
    private val invalidate: () -> Unit,
    private val isShapeDeleteHandle: (Float, Float) -> Boolean,
    private val isShapeRotationHandle: (Float, Float) -> Boolean,
    private val isShapeResizeHandle: (Float, Float) -> Boolean,
    private val isTextDeleteHandle: (Float, Float) -> Boolean,
    private val isTextRotationHandle: (Float, Float) -> Boolean,
    private val isTextResizeHandle: (Float, Float) -> Boolean,
    private val minElementScale: Float,
    private val maxElementScale: Float,
    private val handleTouchRadius: Float
) {

    companion object {
        private const val TAG = "ElementGesture"
    }

    enum class TransformMode {
        NONE,
        ROTATE,
        RESIZE
    }

    var transformMode: TransformMode = TransformMode.NONE
        private set

    var isMovingElement: Boolean = false
        private set

    var isDragging: Boolean = false
        private set

    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var initialRotation = 0f
    private var initialRotationAngle = 0f
    private var initialElementScale = 1f
    private var initialResizeDistance = 1f

    private var historyBeforeGesture: EditorStateSnapshot? = null
    private var historyGestureChanged = false

    fun reset() {
        lastTouchX = 0f
        lastTouchY = 0f
        isDragging = false
        isMovingElement = false
        transformMode = TransformMode.NONE
        historyBeforeGesture = null
        historyGestureChanged = false
    }

    fun setTransformMode(mode: TransformMode) {
        transformMode = mode
    }

    fun setMovingElement(moving: Boolean) {
        isMovingElement = moving
    }

    fun setDragging(dragging: Boolean) {
        isDragging = dragging
    }

    fun setLastTouch(x: Float, y: Float) {
        lastTouchX = x
        lastTouchY = y
    }

    fun getLastTouchX(): Float = lastTouchX

    fun getLastTouchY(): Float = lastTouchY

    fun beginHistoryGesture() {
        historyBeforeGesture = captureHistoryState()
        historyGestureChanged = false
    }

    fun markHistoryChanged() {
        historyGestureChanged = true
    }

    fun finishHistoryGesture() {
        val before = historyBeforeGesture ?: return

        if (historyGestureChanged) {
            val after = captureHistoryState()
            // Function types do not support named arguments.
            recordHistory(before, after)
        }

        historyBeforeGesture = null
        historyGestureChanged = false
    }

    fun cancelHistoryGesture() {
        historyBeforeGesture = null
        historyGestureChanged = false
    }

    fun startRotation(touchX: Float, touchY: Float): Boolean {
        val element = getTransformElement() ?: return false

        if (element.isLocked) {
            Log.d(TAG, "Rotation ignored: element is locked")
            transformMode = TransformMode.NONE
            return true
        }

        val center = coordinateMapper.imageToScreen(
            getElementX(element),
            getElementY(element)
        ) ?: return false

        beginHistoryGesture()

        initialRotation = getElementRotation(element)
        initialRotationAngle = Math.toDegrees(
            atan2(
                (touchY - center.y).toDouble(),
                (touchX - center.x).toDouble()
            )
        ).toFloat()

        transformMode = TransformMode.ROTATE

        Log.d(
            TAG,
            "Rotation started: rotation=$initialRotation angle=$initialRotationAngle"
        )

        return true
    }

    fun updateRotation(touchX: Float, touchY: Float): Boolean {
        val element = getTransformElement() ?: return false

        if (element.isLocked) {
            return false
        }

        val center = coordinateMapper.imageToScreen(
            getElementX(element),
            getElementY(element)
        ) ?: return false

        val currentAngle = Math.toDegrees(
            atan2(
                (touchY - center.y).toDouble(),
                (touchX - center.x).toDouble()
            )
        ).toFloat()

        var delta = currentAngle - initialRotationAngle

        while (delta > 180f) {
            delta -= 360f
        }

        while (delta < -180f) {
            delta += 360f
        }

        var newRotation = initialRotation + delta

        while (newRotation < 0f) {
            newRotation += 360f
        }

        while (newRotation >= 360f) {
            newRotation -= 360f
        }

        setElementRotation(element, newRotation)
        markHistoryChanged()
        invalidate()

        return true
    }

    fun startResize(touchX: Float, touchY: Float): Boolean {
        val element = getTransformElement() ?: return false

        if (element.isLocked) {
            Log.d(TAG, "Resize ignored: element is locked")
            transformMode = TransformMode.NONE
            return true
        }

        val center = coordinateMapper.imageToScreen(
            getElementX(element),
            getElementY(element)
        ) ?: return false

        beginHistoryGesture()

        initialElementScale = getElementScale(element)

        initialResizeDistance = distance(
            center.x,
            center.y,
            touchX,
            touchY
        ).coerceAtLeast(1f)

        transformMode = TransformMode.RESIZE

        Log.d(
            TAG,
            "Resize started: scale=$initialElementScale distance=$initialResizeDistance"
        )

        return true
    }

    fun updateResize(touchX: Float, touchY: Float): Boolean {
        val element = getTransformElement() ?: return false

        if (element.isLocked || initialResizeDistance <= 0f) {
            return false
        }

        val center = coordinateMapper.imageToScreen(
            getElementX(element),
            getElementY(element)
        ) ?: return false

        val currentDistance = distance(
            center.x,
            center.y,
            touchX,
            touchY
        )

        val ratio = currentDistance / initialResizeDistance

        val newScale = (
                initialElementScale * ratio
                ).coerceIn(
                minElementScale,
                maxElementScale
            )

        setElementScale(element, newScale)
        markHistoryChanged()
        invalidate()

        return true
    }

    fun handleActionDown(
        event: MotionEvent,
        onDeleteHandle: (() -> Boolean)? = null
    ): Boolean {
        if (event.pointerCount != 1) {
            return false
        }

        val selected = getSelectedElement()

        if (selected?.isLocked == true) {
            val imagePoint = coordinateMapper.screenToImage(
                event.x,
                event.y
            )

            val touchedElement = imagePoint?.let {
                findElementAt(it.x, it.y)
            }

            val touchedSelectedLockedElement =
                touchedElement === selected

            val touchedLockedHandle =
                when (selected) {
                    is ShapeElement ->
                        isShapeDeleteHandle(event.x, event.y) ||
                                isShapeRotationHandle(event.x, event.y) ||
                                isShapeResizeHandle(event.x, event.y)

                    is TextElement ->
                        isTextDeleteHandle(event.x, event.y) ||
                                isTextRotationHandle(event.x, event.y) ||
                                isTextResizeHandle(event.x, event.y)

                    else -> false
                }

            if (touchedSelectedLockedElement || touchedLockedHandle) {
                isMovingElement = false
                transformMode = TransformMode.NONE
                return true
            }
        }

        if (onDeleteHandle?.invoke() == true) {
            isMovingElement = false
            transformMode = TransformMode.NONE
            return true
        }

        when (selected) {
            is ShapeElement -> {
                if (isShapeRotationHandle(event.x, event.y)) {
                    startRotation(event.x, event.y)
                    isMovingElement = false
                    return true
                }

                if (isShapeResizeHandle(event.x, event.y)) {
                    startResize(event.x, event.y)
                    isMovingElement = false
                    return true
                }
            }

            is TextElement -> {
                if (isTextRotationHandle(event.x, event.y)) {
                    startRotation(event.x, event.y)
                    isMovingElement = false
                    return true
                }

                if (isTextResizeHandle(event.x, event.y)) {
                    startResize(event.x, event.y)
                    isMovingElement = false
                    return true
                }
            }

            else -> Unit
        }

        val imagePoint = coordinateMapper.screenToImage(
            event.x,
            event.y
        ) ?: return false

        val touchedElement = findElementAt(
            imagePoint.x,
            imagePoint.y
        )

        if (touchedElement != null) {
            selectElement(touchedElement)

            if (!touchedElement.isLocked) {
                beginHistoryGesture()
                isMovingElement = true
            } else {
                isMovingElement = false
            }

            isDragging = true
            setLastTouch(event.x, event.y)
            return true
        }

        selectElement(null)
        isMovingElement = false
        isDragging = true
        setLastTouch(event.x, event.y)

        return true
    }

    fun moveSelectedElement(
        previousScreenX: Float,
        previousScreenY: Float,
        currentScreenX: Float,
        currentScreenY: Float
    ): Boolean {
        val element = getSelectedElement() ?: return false

        if (element.isLocked) {
            return false
        }

        val previousPoint = coordinateMapper.screenToImage(
            previousScreenX,
            previousScreenY
        )

        val currentPoint = coordinateMapper.screenToImage(
            currentScreenX,
            currentScreenY
        )

        if (previousPoint == null || currentPoint == null) {
            return false
        }

        val dx = currentPoint.x - previousPoint.x
        val dy = currentPoint.y - previousPoint.y

        if (dx == 0f && dy == 0f) {
            return false
        }

        element.moveBy(dx, dy)
        markHistoryChanged()
        invalidate()

        return true
    }

    fun handleActionMove(event: MotionEvent): Boolean {
        if (
            transformMode == TransformMode.ROTATE &&
            event.pointerCount == 1 &&
            getSelectedElement()?.isLocked != true
        ) {
            updateRotation(event.x, event.y)
            setLastTouch(event.x, event.y)
            return true
        }

        if (
            transformMode == TransformMode.RESIZE &&
            event.pointerCount == 1 &&
            getSelectedElement()?.isLocked != true
        ) {
            updateResize(event.x, event.y)
            setLastTouch(event.x, event.y)
            return true
        }

        if (
            event.pointerCount == 1 &&
            !isMovingElement &&
            isDragging
        ) {
            return false
        }

        if (
            event.pointerCount == 1 &&
            isMovingElement &&
            isDragging &&
            getSelectedElement()?.isLocked != true
        ) {
            moveSelectedElement(
                lastTouchX,
                lastTouchY,
                event.x,
                event.y
            )
            setLastTouch(event.x, event.y)
            return true
        }

        return false
    }

    fun finishAction() {
        finishHistoryGesture()
        isDragging = false
        isMovingElement = false
        transformMode = TransformMode.NONE
    }

    fun cancelAction() {
        cancelHistoryGesture()
        isDragging = false
        isMovingElement = false
        transformMode = TransformMode.NONE
    }

    private fun getTransformElement(): EditorElement? {
        return when (val element = getSelectedElement()) {
            is TextElement -> element
            is ShapeElement -> element
            else -> null
        }
    }

    private fun getElementX(element: EditorElement): Float {
        return when (element) {
            is TextElement -> element.position.x
            is ShapeElement -> element.position.x
            else -> 0f
        }
    }

    private fun getElementY(element: EditorElement): Float {
        return when (element) {
            is TextElement -> element.position.y
            is ShapeElement -> element.position.y
            else -> 0f
        }
    }

    private fun getElementRotation(element: EditorElement): Float {
        return when (element) {
            is TextElement -> element.rotation
            is ShapeElement -> element.rotation
            else -> 0f
        }
    }

    private fun setElementRotation(
        element: EditorElement,
        rotation: Float
    ) {
        when (element) {
            is TextElement -> element.rotation = rotation
            is ShapeElement -> element.rotation = rotation
            else -> {}
        }
    }

    private fun getElementScale(element: EditorElement): Float {
        return when (element) {
            is TextElement -> element.scale
            is ShapeElement -> element.scale
            else -> 1f
        }
    }

    private fun setElementScale(
        element: EditorElement,
        scale: Float
    ) {
        when (element) {
            is TextElement -> element.scale = scale
            is ShapeElement -> element.scale = scale
            else -> {}
        }
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

    fun getHandleTouchRadius(): Float = handleTouchRadius
}

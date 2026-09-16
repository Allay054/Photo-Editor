package com.allay.photoeditor.editor.crop

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.allay.photoeditor.model.EditorElement
import android.util.Log
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Owns crop editing state and crop geometry/touch behavior.
 *
 * Crop coordinates are stored in image space. Screen/image coordinate
 * conversion and rendering remain owned by PhotoEditorView and are supplied
 * through callbacks so the controller does not depend on the View.
 */
class CropController(
    private val getBitmap: () -> Bitmap?,
    private val getCropRectOnScreen: () -> RectF?,
    private val screenToImage: (Float, Float) -> PointF?,
    private val captureSessionElements: () -> List<EditorElement>,
    private val captureSelectedIndex: () -> Int,
    private val invalidate: () -> Unit,
    private val onModeChanged: (Boolean) -> Unit
) {

    companion object {
        private const val TAG = "CropController"
        private const val CROP_HANDLE_TOUCH_RADIUS = 64f
        private const val MIN_CROP_SIZE = 100f
    }

    enum class AspectRatio { FREE, ONE_TO_ONE, FOUR_TO_THREE, SIXTEEN_TO_NINE, ORIGINAL_RATIO }
    enum class Handle { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    private var active = false
    private var cropRect: RectF? = null
    private var aspectRatio = AspectRatio.FREE
    private var activeHandle = Handle.NONE
    private val lastTouchImage = PointF()

    /**
     * Snapshot captured before Crop Mode changes the editor state.
     * PhotoEditorView owns the actual editor data; this controller owns the
     * temporary Crop-session snapshot lifecycle.
     */
    data class SessionSnapshot(
        val bitmap: Bitmap?,
        val elements: List<EditorElement>,
        val selectedIndex: Int
    )

    private var sessionSnapshot: SessionSnapshot? = null

    val hasSessionSnapshot: Boolean
        get() = sessionSnapshot != null
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    val isActive: Boolean get() = active
    val currentCropRect: RectF? get() = cropRect
    val currentAspectRatio: AspectRatio get() = aspectRatio
    val currentHandle: Handle get() = activeHandle

    fun beginSession() {
        val bitmap = getBitmap() ?: return

        sessionSnapshot = SessionSnapshot(
            bitmap = bitmap,
            elements = captureSessionElements(),
            selectedIndex = captureSelectedIndex()
        )

        Log.d(
            TAG,
            "Crop session snapshot created: bitmap=${bitmap.width}x${bitmap.height}, " +
                    "elements=${sessionSnapshot?.elements?.size}, " +
                    "selectedIndex=${sessionSnapshot?.selectedIndex}"
        )
    }

    fun getSessionSnapshot(): SessionSnapshot? = sessionSnapshot

    fun clearSession() {
        if (sessionSnapshot != null) {
            Log.d(TAG, "Crop session snapshot cleared")
        }
        sessionSnapshot = null
    }

    fun enter() {
        val bitmap = getBitmap() ?: return
        active = true
        aspectRatio = AspectRatio.FREE
        activeHandle = Handle.NONE
        cropRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        resetTouchState()
        onModeChanged(true)
        invalidate()
    }

    fun exit() {
        if (!active) return
        active = false
        cropRect = null
        aspectRatio = AspectRatio.FREE
        resetTouchState()
        onModeChanged(false)
        invalidate()
    }

    fun reset() {
        val bitmap = getBitmap() ?: return
        if (!active) return
        aspectRatio = AspectRatio.FREE
        cropRect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        resetTouchState()
        invalidate()
    }

    fun setActiveState(value: Boolean) { active = value }
    fun setCropRectState(value: RectF?) { cropRect = value?.let(::RectF) }
    fun setAspectRatioState(value: AspectRatio) { aspectRatio = value }
    fun setActiveHandleState(value: Handle) { activeHandle = value }

    fun setLastTouchImageState(x: Float, y: Float) { lastTouchImage.set(x, y) }
    fun getLastTouchImage(): PointF = PointF(lastTouchImage.x, lastTouchImage.y)

    fun resetTouchState() {
        activeHandle = Handle.NONE
        lastTouchX = 0f
        lastTouchY = 0f
        lastTouchImage.set(0f, 0f)
    }

    fun handleTouch(event: MotionEvent) {
        val rect = cropRect ?: return
        if (getBitmap() == null) return

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                activeHandle = findCropHandle(event.x, event.y)
                if (activeHandle == Handle.NONE) {
                    val screenRect = getCropRectOnScreen()
                    if (screenRect?.contains(event.x, event.y) == true) {
                        activeHandle = Handle.MOVE
                    }
                }
                Log.d(TAG, "Crop touch started: x=${event.x}, y=${event.y}, handle=$activeHandle")
            }
            MotionEvent.ACTION_MOVE -> {
                when (activeHandle) {
                    Handle.MOVE -> moveCropArea(lastTouchX, lastTouchY, event.x, event.y)
                    Handle.TOP_LEFT, Handle.TOP_RIGHT, Handle.BOTTOM_LEFT, Handle.BOTTOM_RIGHT -> {
                        val point = screenToImage(event.x, event.y) ?: return
                        resizeCropFromHandle(point.x, point.y, activeHandle)
                    }
                    Handle.NONE -> return
                }
                lastTouchX = event.x
                lastTouchY = event.y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                Log.d(TAG, "Crop touch ended. handle=$activeHandle, cropRect=$cropRect")
                activeHandle = Handle.NONE
            }
        }
    }

    private fun findCropHandle(screenX: Float, screenY: Float): Handle {
        val rect = getCropRectOnScreen() ?: return Handle.NONE
        val radius = CROP_HANDLE_TOUCH_RADIUS
        if (hypot(screenX - rect.left, screenY - rect.top) <= radius) return Handle.TOP_LEFT
        if (hypot(screenX - rect.right, screenY - rect.top) <= radius) return Handle.TOP_RIGHT
        if (hypot(screenX - rect.left, screenY - rect.bottom) <= radius) return Handle.BOTTOM_LEFT
        if (hypot(screenX - rect.right, screenY - rect.bottom) <= radius) return Handle.BOTTOM_RIGHT
        return Handle.NONE
    }

    private fun moveCropArea(previousScreenX: Float, previousScreenY: Float, currentScreenX: Float, currentScreenY: Float) {
        val rect = cropRect ?: return
        val bitmap = getBitmap() ?: return
        val previous = screenToImage(previousScreenX, previousScreenY)
        val current = screenToImage(currentScreenX, currentScreenY)
        if (previous == null || current == null) {
            Log.d(TAG, "Crop move ignored: unable to convert touch to image coordinates")
            return
        }
        val deltaX = current.x - previous.x
        val deltaY = current.y - previous.y
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()
        val maxLeft = (imageWidth - rect.width()).coerceAtLeast(0f)
        val maxTop = (imageHeight - rect.height()).coerceAtLeast(0f)
        rect.offsetTo(
            (rect.left + deltaX).coerceIn(0f, maxLeft),
            (rect.top + deltaY).coerceIn(0f, maxTop)
        )
    }

    private fun resizeCropFromHandle(imageX: Float, imageY: Float, handle: Handle) {
        val rect = cropRect ?: return
        val bitmap = getBitmap() ?: return
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        val minSize = MIN_CROP_SIZE
        when (aspectRatio) {
            AspectRatio.FREE -> resizeCropFree(imageX, imageY, handle, width, height, minSize)
            AspectRatio.ONE_TO_ONE -> resizeCropWithAspectRatio(imageX, imageY, handle, 1f, width, height, minSize)
            AspectRatio.FOUR_TO_THREE -> resizeCropWithAspectRatio(imageX, imageY, handle, 4f / 3f, width, height, minSize)
            AspectRatio.SIXTEEN_TO_NINE -> resizeCropWithAspectRatio(imageX, imageY, handle, 16f / 9f, width, height, minSize)
            AspectRatio.ORIGINAL_RATIO -> resizeCropWithAspectRatio(imageX, imageY, handle, width / height, width, height, minSize)
        }
        Log.d(TAG, "Crop resized: handle=$handle rect=$rect")
    }

    private fun resizeCropFree(imageX: Float, imageY: Float, handle: Handle, imageWidth: Float, imageHeight: Float, minSize: Float) {
        val rect = cropRect ?: return
        when (handle) {
            Handle.TOP_LEFT -> { rect.left = imageX.coerceIn(0f, rect.right - minSize); rect.top = imageY.coerceIn(0f, rect.bottom - minSize) }
            Handle.TOP_RIGHT -> { rect.right = imageX.coerceIn(rect.left + minSize, imageWidth); rect.top = imageY.coerceIn(0f, rect.bottom - minSize) }
            Handle.BOTTOM_LEFT -> { rect.left = imageX.coerceIn(0f, rect.right - minSize); rect.bottom = imageY.coerceIn(rect.top + minSize, imageHeight) }
            Handle.BOTTOM_RIGHT -> { rect.right = imageX.coerceIn(rect.left + minSize, imageWidth); rect.bottom = imageY.coerceIn(rect.top + minSize, imageHeight) }
            Handle.MOVE, Handle.NONE -> Unit
        }
    }

    private fun resizeCropWithAspectRatio(imageX: Float, imageY: Float, handle: Handle, aspectRatio: Float, imageWidth: Float, imageHeight: Float, minSize: Float) {
        val rect = cropRect ?: return
        if (aspectRatio <= 0f) return
        val minWidth = max(minSize, minSize * aspectRatio)
        val minHeight = max(minSize, minSize / aspectRatio)
        when (handle) {
            Handle.TOP_LEFT -> {
                val anchorX = rect.right; val anchorY = rect.bottom
                val requestedWidth = max(anchorX - imageX, (anchorY - imageY) * aspectRatio)
                val maxWidth = min(anchorX, anchorY * aspectRatio)
                val w = requestedWidth.coerceIn(minWidth, maxWidth.coerceAtLeast(minWidth)); val h = w / aspectRatio
                rect.set(anchorX - w, anchorY - h, anchorX, anchorY)
            }
            Handle.TOP_RIGHT -> {
                val anchorX = rect.left; val anchorY = rect.bottom
                val requestedWidth = max(imageX - anchorX, (anchorY - imageY) * aspectRatio)
                val maxWidth = min(imageWidth - anchorX, anchorY * aspectRatio)
                val w = requestedWidth.coerceIn(minWidth, maxWidth.coerceAtLeast(minWidth)); val h = w / aspectRatio
                rect.set(anchorX, anchorY - h, anchorX + w, anchorY)
            }
            Handle.BOTTOM_LEFT -> {
                val anchorX = rect.right; val anchorY = rect.top
                val requestedWidth = max(anchorX - imageX, (imageY - anchorY) * aspectRatio)
                val maxWidth = min(anchorX, (imageHeight - anchorY) * aspectRatio)
                val w = requestedWidth.coerceIn(minWidth, maxWidth.coerceAtLeast(minWidth)); val h = w / aspectRatio
                rect.set(anchorX - w, anchorY, anchorX, anchorY + h)
            }
            Handle.BOTTOM_RIGHT -> {
                val anchorX = rect.left; val anchorY = rect.top
                val requestedWidth = max(imageX - anchorX, (imageY - anchorY) * aspectRatio)
                val maxWidth = min(imageWidth - anchorX, (imageHeight - anchorY) * aspectRatio)
                val w = requestedWidth.coerceIn(minWidth, maxWidth.coerceAtLeast(minWidth)); val h = w / aspectRatio
                rect.set(anchorX, anchorY, anchorX + w, anchorY + h)
            }
            Handle.MOVE, Handle.NONE -> Unit
        }
    }

    fun normalizeToAspectRatio(aspectRatio: Float) {
        val rect = cropRect ?: return
        val bitmap = getBitmap() ?: return
        if (aspectRatio <= 0f) return
        val imageWidth = bitmap.width.toFloat(); val imageHeight = bitmap.height.toFloat()
        var width = rect.width(); var height = rect.height()
        if (width <= 0f || height <= 0f) return
        if (width / height > aspectRatio) width = height * aspectRatio else height = width / aspectRatio
        if (width > imageWidth) { width = imageWidth; height = width / aspectRatio }
        if (height > imageHeight) { height = imageHeight; width = height * aspectRatio }
        val left = (rect.centerX() - width / 2f).coerceIn(0f, (imageWidth - width).coerceAtLeast(0f))
        val top = (rect.centerY() - height / 2f).coerceIn(0f, (imageHeight - height).coerceAtLeast(0f))
        rect.set(left, top, left + width, top + height)
        Log.d(TAG, "Crop normalized to ratio=$aspectRatio rect=$rect")
    }
}

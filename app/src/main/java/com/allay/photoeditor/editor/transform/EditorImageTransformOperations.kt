package com.allay.photoeditor.editor.transform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import com.allay.photoeditor.editor.history.EditorStateSnapshot
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.TextElement

/**
 * Owns committed image transform operations while PhotoEditorView remains the
 * public editor facade.
 *
 * This controller preserves the existing temporary Transform Mode behavior:
 * Flip/Rotate operations are previews until Apply is pressed, Cancel restores
 * the complete state captured at entry, and global history records one
 * operation for the committed transform session.
 */
class EditorImageTransformOperations(
    private val tag: String,
    private val getBitmap: () -> Bitmap?,
    private val setBitmap: (Bitmap) -> Unit,
    private val elements: MutableList<EditorElement>,
    private val bitmapPaint: Paint,
    private val transformController: TransformController,
    private val isCropModeActive: () -> Boolean,
    private val getSelectedElement: () -> EditorElement?,
    private val setSelectedElement: (EditorElement?) -> Unit,
    private val getScaleFactor: () -> Float,
    private val setScaleFactor: (Float) -> Unit,
    private val getTranslationX: () -> Float,
    private val setTranslationX: (Float) -> Unit,
    private val getTranslationY: () -> Float,
    private val setTranslationY: (Float) -> Unit,
    private val captureHistoryState: () -> EditorStateSnapshot,
    private val recordHistory: (EditorStateSnapshot, EditorStateSnapshot) -> Unit,
    private val resetTransform: () -> Unit,
    private val resetGestureState: () -> Unit,
    private val notifySelectionChanged: () -> Unit,
    private val onModeChanged: ((Boolean) -> Unit)? = null,
    private val invalidate: () -> Unit
) {
    private var transformHistoryBefore: EditorStateSnapshot? = null
    private var transformHistoryChanged = false

    fun isActive(): Boolean = transformController.isActive

    fun enter() {
        if (isCropModeActive()) {
            Log.d(tag, "Transform mode ignored: crop mode is active")
            return
        }
        if (getBitmap() == null) {
            Log.d(tag, "Transform mode ignored: no image selected")
            return
        }
        if (isActive()) {
            Log.d(tag, "Transform mode ignored: already active")
            return
        }
        beginTransformSession()
    }

    fun apply() {
        if (!isActive()) {
            Log.d(tag, "Apply rotation ignored: transform mode is not active")
            return
        }

        transformController.apply()

        val before = transformHistoryBefore
        if (before != null && transformHistoryChanged) {
            recordHistory(
                before,
                captureHistoryState()
            )
        }

        transformHistoryBefore = null
        transformHistoryChanged = false
        Log.d(tag, "Transform applied")
    }

    fun cancel() {
        if (!isActive()) {
            Log.d(tag, "Cancel rotation ignored: transform mode is not active")
            return
        }

        val snapshot = transformController.cancel()
            ?: return

        setBitmap(snapshot.bitmap)
        elements.clear()
        elements.addAll(snapshot.elements)

        setSelectedElement(
            snapshot.elements.getOrNull(snapshot.selectedElementIndex)
        )

        elements.forEach { element ->
            element.isSelected = element === getSelectedElement()
        }

        setScaleFactor(snapshot.scaleFactor)
        setTranslationX(snapshot.translationX)
        setTranslationY(snapshot.translationY)

        notifySelectionChanged()
        invalidate()

        transformHistoryBefore = null
        transformHistoryChanged = false

        Log.d(
            tag,
            "Transform cancelled. Original image restored: " +
                    "${snapshot.bitmap.width}x${snapshot.bitmap.height}"
        )
    }

    fun clearSession() {
        transformController.clearSession()
        transformHistoryBefore = null
        transformHistoryChanged = false
    }

    fun flipHorizontal() {
        if (isCropModeActive()) {
            Log.d(tag, "Horizontal flip ignored: crop mode is active")
            return
        }

        val currentBitmap = getBitmap()
        if (currentBitmap == null) {
            Log.d(tag, "Horizontal flip ignored: no image selected")
            return
        }

        val imageWidth = currentBitmap.width.toFloat()

        if (currentBitmap.width <= 0 || currentBitmap.height <= 0) {
            Log.w(tag, "Horizontal flip ignored: invalid bitmap dimensions")
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

            elements.forEach { element ->
                when (element) {
                    is TextElement -> {
                        transformTextForHorizontalFlip(
                            element,
                            imageWidth
                        )
                    }
                    else -> Unit
                }
            }

            setBitmap(flippedBitmap)
            transformHistoryChanged = true
            resetGestureState()

            Log.d(
                tag,
                "Horizontal flip applied: " +
                        "${currentBitmap.width}x${currentBitmap.height}, " +
                        "elements=${elements.size}, " +
                        "scaleFactor=${getScaleFactor()}, " +
                        "translation=(${getTranslationX()},${getTranslationY()})"
            )

            invalidate()
        } catch (exception: Exception) {
            Log.e(tag, "Failed to flip image horizontally", exception)
        }
    }

    fun flipVertical() {
        if (isCropModeActive()) {
            Log.d(tag, "Vertical flip ignored: crop mode is active")
            return
        }

        val currentBitmap = getBitmap()
        if (currentBitmap == null) {
            Log.d(tag, "Vertical flip ignored: no image selected")
            return
        }

        val imageHeight = currentBitmap.height.toFloat()

        if (currentBitmap.width <= 0 || currentBitmap.height <= 0) {
            Log.w(tag, "Vertical flip ignored: invalid bitmap dimensions")
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
                            element,
                            imageHeight
                        )
                    }
                    else -> Unit
                }
            }

            setBitmap(flippedBitmap)
            transformHistoryChanged = true
            resetGestureState()

            Log.d(
                tag,
                "Vertical flip applied: " +
                        "${currentBitmap.width}x${currentBitmap.height}, " +
                        "elements=${elements.size}, " +
                        "scaleFactor=${getScaleFactor()}, " +
                        "translation=(${getTranslationX()},${getTranslationY()})"
            )

            invalidate()
        } catch (exception: Exception) {
            Log.e(tag, "Failed to flip image vertically", exception)
        }
    }

    fun rotateLeft90() {
        if (isCropModeActive()) {
            Log.d(tag, "Rotate left ignored: crop mode is active")
            return
        }

        val currentBitmap = getBitmap()
        if (currentBitmap == null) {
            Log.d(tag, "Rotate left ignored: no image selected")
            return
        }

        if (currentBitmap.width <= 0 || currentBitmap.height <= 0) {
            Log.w(tag, "Rotate left ignored: invalid bitmap dimensions")
            return
        }

        beginTransformSession()

        val oldWidth = currentBitmap.width.toFloat()
        try {
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

            elements.forEach { element ->
                when (element) {
                    is TextElement -> {
                        transformTextForRotateLeft90(
                            element,
                            oldWidth
                        )
                    }
                    else -> Unit
                }
            }

            setBitmap(rotatedBitmap)
            transformHistoryChanged = true

            resetTransform()
            resetGestureState()

            Log.d(
                tag,
                "Image rotated 90 degrees counter-clockwise: " +
                        "${currentBitmap.width}x${currentBitmap.height} -> " +
                        "${rotatedBitmap.width}x${rotatedBitmap.height}, " +
                        "elements=${elements.size}"
            )

            invalidate()
        } catch (exception: Exception) {
            Log.e(
                tag,
                "Failed to rotate image 90 degrees counter-clockwise",
                exception
            )
        }
    }

    fun rotateRight90() {
        if (isCropModeActive()) {
            Log.d(tag, "Rotate right ignored: crop mode is active")
            return
        }

        val currentBitmap = getBitmap()
        if (currentBitmap == null) {
            Log.d(tag, "Rotate right ignored: no image selected")
            return
        }

        if (currentBitmap.width <= 0 || currentBitmap.height <= 0) {
            Log.w(tag, "Rotate right ignored: invalid bitmap dimensions")
            return
        }

        beginTransformSession()

        try {
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

            elements.forEach { element ->
                when (element) {
                    is TextElement -> {
                        val oldX = element.position.x
                        val oldY = element.position.y

                        element.position.x = currentBitmap.height - oldY
                        element.position.y = oldX
                        element.rotation = normalizeRotation(
                            element.rotation + 90f
                        )
                    }
                    else -> Unit
                }
            }

            setBitmap(rotatedBitmap)
            transformHistoryChanged = true

            resetTransform()
            resetGestureState()

            Log.d(
                tag,
                "Image rotated 90 degrees clockwise: " +
                        "${currentBitmap.width}x${currentBitmap.height} -> " +
                        "${rotatedBitmap.width}x${rotatedBitmap.height}, " +
                        "elements=${elements.size}"
            )

            invalidate()
        } catch (exception: Exception) {
            Log.e(
                tag,
                "Failed to rotate image 90 degrees clockwise",
                exception
            )
        }
    }

    private fun beginTransformSession() {
        if (!isActive()) {
            transformHistoryBefore = captureHistoryState()
            transformHistoryChanged = false
        }

        transformController.enter()
    }

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

    private fun transformTextForVerticalFlip(
        textElement: TextElement,
        imageHeight: Float
    ) {
        textElement.position.y = imageHeight - textElement.position.y
        textElement.rotation = normalizeRotation(-textElement.rotation)
    }

    private fun transformTextForRotateLeft90(
        textElement: TextElement,
        oldImageWidth: Float
    ) {
        val oldX = textElement.position.x
        val oldY = textElement.position.y

        textElement.position.x = oldY
        textElement.position.y = oldImageWidth - oldX
        textElement.rotation = normalizeRotation(
            textElement.rotation - 90f
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
}

package com.allay.photoeditor.editor.crop

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import com.allay.photoeditor.editor.core.EditorElementStore
import com.allay.photoeditor.editor.history.EditorStateSnapshot
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.TextElement

/**
 * Owns high-level Crop operations while PhotoEditorView keeps the existing
 * public API and coordinates rendering/touch interaction.
 *
 * CropController continues to own the temporary crop-session state and crop
 * interaction state. This class owns crop commands and committed crop logic.
 */
class EditorCropOperations(
    private val tag: String,
    private val cropController: CropController,
    private val elementStore: EditorElementStore,
    private val getBitmap: () -> Bitmap?,
    private val setBitmap: (Bitmap) -> Unit,
    private val getSelectedElement: () -> EditorElement?,
    private val setSelectedElement: (EditorElement?) -> Unit,
    private val resetElementGestureState: () -> Unit,
    private val resetTransform: () -> Unit,
    private val notifySelectionChanged: () -> Unit,
    private val captureHistoryState: () -> EditorStateSnapshot,
    private val recordHistory: (EditorStateSnapshot, EditorStateSnapshot) -> Unit,
    private val onCropModeChanged: () -> ((Boolean) -> Unit)?,
    private val invalidate: () -> Unit,
    private val minCropSize: Float
) {
    private val cropAspectRatio: CropController.AspectRatio
        get() = cropController.currentAspectRatio

    fun enter() {
        if (cropController.isActive) return

        if (getBitmap() == null) {
            Log.d(tag, "Cannot enter crop mode. No image selected.")
            return
        }

        cropController.beginSession()

        getSelectedElement()?.isSelected = false
        setSelectedElement(null)
        resetElementGestureState()

        cropController.setAspectRatioState(
            CropController.AspectRatio.FREE
        )

        val bitmap = getBitmap() ?: return
        cropController.setCropRectState(
            RectF(
                0f,
                0f,
                bitmap.width.toFloat(),
                bitmap.height.toFloat()
            )
        )

        cropController.setActiveState(true)

        Log.d(tag, "Crop mode entered")
        notifySelectionChanged()
        onCropModeChanged()?.invoke(true)
        invalidate()
    }

    fun apply() {
        val currentBitmap = getBitmap()
        val cropRect = cropController.currentCropRect

        if (currentBitmap == null) {
            Log.w(tag, "Cannot apply crop. No image selected.")
            return
        }

        if (!cropController.isActive || cropRect == null) {
            Log.w(tag, "Cannot apply crop. Crop mode is not active.")
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

        if (cropWidth <= 0 || cropHeight <= 0) {
            Log.w(
                tag,
                "Cannot apply crop. Invalid crop size: ${cropWidth}x${cropHeight}"
            )
            return
        }

        if (
            cropWidth < minCropSize.toInt() ||
            cropHeight < minCropSize.toInt()
        ) {
            Log.w(
                tag,
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

        val historyBefore =
            if (!isFullImage) {
                captureHistoryState()
            } else {
                null
            }

        if (!isFullImage) {
            setBitmap(
                Bitmap.createBitmap(
                    currentBitmap,
                    left,
                    top,
                    cropWidth,
                    cropHeight
                )
            )

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

                if (!RectF.intersects(cropBounds, elementBounds)) {
                    element.isSelected = false
                    if (getSelectedElement() === element) {
                        setSelectedElement(null)
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

        setSelectedElement(null)
        resetElementGestureState()
        cropController.setActiveHandleState(CropController.Handle.NONE)
        cropController.setCropRectState(null)
        cropController.setAspectRatioState(
            CropController.AspectRatio.FREE
        )
        cropController.setActiveState(false)
        cropController.clearSession()
        resetTransform()
        notifySelectionChanged()
        onCropModeChanged()?.invoke(false)

        if (historyBefore != null) {
            recordHistory(
                historyBefore,
                captureHistoryState()
            )
        }

        Log.d(
            tag,
            "Crop applied: ${cropWidth}x${cropHeight}, " +
                    "origin=($left,$top), " +
                    "elements=${elementStore.elements.size}"
        )
        invalidate()
    }

    fun reset() {
        if (!cropController.isActive) {
            Log.d(tag, "Reset crop ignored: crop mode is not active")
            return
        }

        val currentBitmap = getBitmap()
        if (currentBitmap == null) {
            Log.w(tag, "Reset crop ignored: bitmap is missing")
            return
        }

        cropController.setAspectRatioState(
            CropController.AspectRatio.FREE
        )
        cropController.setCropRectState(
            RectF(
                0f,
                0f,
                currentBitmap.width.toFloat(),
                currentBitmap.height.toFloat()
            )
        )
        cropController.setActiveHandleState(CropController.Handle.NONE)

        resetElementGestureState()
        resetTransform()

        Log.d(
            tag,
            "Crop reset to full image: " +
                    "${currentBitmap.width}x${currentBitmap.height}"
        )
        invalidate()
    }

    fun cancel() {
        if (!cropController.isActive) {
            Log.d(tag, "Cancel crop ignored: crop mode is not active")
            return
        }

        val session = cropController.getSessionSnapshot()
        if (session == null) {
            Log.w(
                tag,
                "Cancel crop failed: crop session snapshot is missing"
            )
            exitWithoutRestore()
            return
        }

        session.bitmap?.let(setBitmap)

        elementStore.elements.clear()
        elementStore.elements.addAll(session.elements)

        val restoredSelection =
            session.elements.getOrNull(session.selectedIndex)

        setSelectedElement(restoredSelection)

        elementStore.elements.forEach { element ->
            element.isSelected = element === restoredSelection
        }

        cropController.setCropRectState(null)
        cropController.setAspectRatioState(
            CropController.AspectRatio.FREE
        )
        cropController.setActiveState(false)
        resetElementGestureState()
        cropController.setActiveHandleState(CropController.Handle.NONE)
        cropController.clearSession()
        resetTransform()
        notifySelectionChanged()
        onCropModeChanged()?.invoke(false)

        Log.d(
            tag,
            "Crop cancelled. Original image restored: " +
                    "${session.bitmap?.width}x${session.bitmap?.height}, " +
                    "elements=${elementStore.elements.size}"
        )
        invalidate()
    }

    fun exit() {
        cancel()
    }

    private fun exitWithoutRestore() {
        cropController.setCropRectState(null)
        cropController.setAspectRatioState(
            CropController.AspectRatio.FREE
        )
        cropController.setActiveState(false)
        resetElementGestureState()
        cropController.setActiveHandleState(CropController.Handle.NONE)
        cropController.clearSession()
        notifySelectionChanged()
        onCropModeChanged()?.invoke(false)
        invalidate()
    }

    fun isActive(): Boolean = cropController.isActive

    fun setFreeCropMode() {
        cropController.setAspectRatioState(
            CropController.AspectRatio.FREE
        )
        Log.d(tag, "Free Crop mode selected")
        invalidate()
    }

    fun isFreeCropMode(): Boolean =
        cropAspectRatio == CropController.AspectRatio.FREE

    fun setOneToOneCropMode() {
        cropController.setAspectRatioState(
            CropController.AspectRatio.ONE_TO_ONE
        )
        cropController.normalizeToAspectRatio(1f)
        Log.d(tag, "1:1 Crop mode selected")
        invalidate()
    }

    fun isOneToOneCropMode(): Boolean =
        cropAspectRatio == CropController.AspectRatio.ONE_TO_ONE

    fun setFourToThreeCropMode() {
        cropController.setAspectRatioState(
            CropController.AspectRatio.FOUR_TO_THREE
        )
        cropController.normalizeToAspectRatio(4f / 3f)
        Log.d(tag, "4:3 Crop mode selected")
        invalidate()
    }

    fun isFourToThreeCropMode(): Boolean =
        cropAspectRatio == CropController.AspectRatio.FOUR_TO_THREE

    fun setSixteenToNineCropMode() {
        cropController.setAspectRatioState(
            CropController.AspectRatio.SIXTEEN_TO_NINE
        )
        cropController.normalizeToAspectRatio(16f / 9f)
        Log.d(tag, "16:9 Crop mode selected")
        invalidate()
    }

    fun isSixteenToNineCropMode(): Boolean =
        cropAspectRatio == CropController.AspectRatio.SIXTEEN_TO_NINE

    fun setOriginalRatioCropMode() {
        val currentBitmap = getBitmap() ?: return

        cropController.setAspectRatioState(
            CropController.AspectRatio.ORIGINAL_RATIO
        )
        cropController.normalizeToAspectRatio(
            currentBitmap.width.toFloat() /
                    currentBitmap.height.toFloat()
        )

        Log.d(tag, "Original Ratio Crop mode selected")
        invalidate()
    }

    fun isOriginalRatioCropMode(): Boolean =
        cropAspectRatio == CropController.AspectRatio.ORIGINAL_RATIO

    fun rotate90Degrees() {
        if (!cropController.isActive) {
            Log.d(
                tag,
                "Rotate crop ignored: crop mode is not active"
            )
            return
        }

        val currentBitmap = getBitmap()
        val currentCropRect = cropController.currentCropRect

        if (currentBitmap == null || currentCropRect == null) {
            Log.w(
                tag,
                "Rotate crop ignored: bitmap or crop rectangle is missing"
            )
            return
        }

        val oldHeight = currentBitmap.height.toFloat()

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

            val rotatedCropLeft =
                oldHeight - currentCropRect.bottom
            val rotatedCropTop =
                currentCropRect.left
            val rotatedCropRight =
                oldHeight - currentCropRect.top
            val rotatedCropBottom =
                currentCropRect.right

            elementStore.elements.forEach { element ->
                if (element is TextElement) {
                    transformTextForRotateRight90(
                        element,
                        oldHeight
                    )
                }
            }

            setBitmap(rotatedBitmap)

            currentCropRect.set(
                rotatedCropLeft,
                rotatedCropTop,
                rotatedCropRight,
                rotatedCropBottom
            )

            when (cropAspectRatio) {
                CropController.AspectRatio.FREE -> Unit
                CropController.AspectRatio.ONE_TO_ONE -> {
                    cropController.normalizeToAspectRatio(1f)
                }
                CropController.AspectRatio.FOUR_TO_THREE -> {
                    cropController.normalizeToAspectRatio(4f / 3f)
                }
                CropController.AspectRatio.SIXTEEN_TO_NINE -> {
                    cropController.normalizeToAspectRatio(16f / 9f)
                }
                CropController.AspectRatio.ORIGINAL_RATIO -> {
                    cropController.normalizeToAspectRatio(
                        rotatedBitmap.width.toFloat() /
                                rotatedBitmap.height.toFloat()
                    )
                }
            }

            resetTransform()
            cropController.setActiveHandleState(CropController.Handle.NONE)
            resetElementGestureState()

            Log.d(
                tag,
                "Crop rotated 90 degrees clockwise: " +
                        "${currentBitmap.width}x${currentBitmap.height} -> " +
                        "${rotatedBitmap.width}x${rotatedBitmap.height}, " +
                        "cropAspect=$cropAspectRatio, " +
                        "cropRect=$currentCropRect, " +
                        "elements=${elementStore.elements.size}"
            )
            invalidate()
        } catch (exception: Exception) {
            Log.e(
                tag,
                "Failed to rotate crop 90 degrees",
                exception
            )
        }
    }

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
        while (normalized < 0f) normalized += 360f
        while (normalized >= 360f) normalized -= 360f
        return normalized
    }
}

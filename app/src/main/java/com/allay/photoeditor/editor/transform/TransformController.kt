package com.allay.photoeditor.editor.transform

import android.graphics.Bitmap
import com.allay.photoeditor.model.EditorElement

/**
 * Owns the temporary transform-session state.
 *
 * PhotoEditorView remains responsible for the actual bitmap transform
 * operations and for restoring the editor collections. This controller only
 * manages the transform preview lifecycle and its immutable entry snapshot.
 */
class TransformController(
    private val getBitmap: () -> Bitmap?,
    private val captureElements: () -> List<EditorElement>,
    private val getSelectedElementIndex: () -> Int,
    private val getScaleFactor: () -> Float,
    private val getTranslationX: () -> Float,
    private val getTranslationY: () -> Float,
    private val resetGestureState: () -> Unit,
    private val onModeChanged: (Boolean) -> Unit,
    private val invalidate: () -> Unit
) {

    data class SessionSnapshot(
        val bitmap: Bitmap,
        val elements: List<EditorElement>,
        val selectedElementIndex: Int,
        val scaleFactor: Float,
        val translationX: Float,
        val translationY: Float
    )

    private var active = false
    private var snapshot: SessionSnapshot? = null

    val isActive: Boolean
        get() = active

    /** Starts a transform preview and captures the editor state at entry. */
    fun enter(): Boolean {
        if (active) {
            return false
        }

        val currentBitmap = getBitmap() ?: return false

        val elementsSnapshot = captureElements()
        val selectedIndex = getSelectedElementIndex()
            .takeIf { it >= 0 && it < elementsSnapshot.size }
            ?: -1

        snapshot = SessionSnapshot(
            bitmap = currentBitmap,
            elements = elementsSnapshot,
            selectedElementIndex = selectedIndex,
            scaleFactor = getScaleFactor(),
            translationX = getTranslationX(),
            translationY = getTranslationY()
        )

        active = true
        resetGestureState()
        onModeChanged(true)
        invalidate()

        return true
    }

    /** Commits the current transform preview and discards the entry snapshot. */
    fun apply(): Boolean {
        if (!active) {
            return false
        }

        clearSession()
        resetGestureState()
        onModeChanged(false)
        invalidate()

        return true
    }

    /**
     * Ends the transform preview and returns the exact entry snapshot.
     * PhotoEditorView uses this snapshot to restore its mutable editor state.
     */
    fun cancel(): SessionSnapshot? {
        if (!active) {
            return null
        }

        val currentSnapshot = snapshot

        clearSession()
        resetGestureState()
        onModeChanged(false)
        invalidate()

        return currentSnapshot
    }

    /** Clears the temporary session without triggering UI callbacks. */
    fun clearSession() {
        snapshot = null
        active = false
    }
}

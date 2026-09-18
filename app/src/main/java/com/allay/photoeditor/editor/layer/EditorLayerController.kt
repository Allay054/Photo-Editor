package com.allay.photoeditor.editor.layer

import android.util.Log
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.editor.history.EditorStateSnapshot

/**
 * Owns layer-level operations for the editor element stack.
 *
 * The controller intentionally does not own selection itself. PhotoEditorView
 * remains the coordinator for selection callbacks and editor history while
 * this class owns the layer ordering/visibility/lock operations.
 */
class EditorLayerController(
    private val elements: MutableList<EditorElement>,
    private val getSelectedElement: () -> EditorElement?,
    private val setSelectedElement: (EditorElement?) -> Unit,
    private val selectElement: (EditorElement?) -> Unit,
    private val isEditorModeActive: () -> Boolean,
    private val captureHistoryState: () -> EditorStateSnapshot,
    private val recordHistory: (before: EditorStateSnapshot, after: EditorStateSnapshot) -> Unit,
    private val resetElementInteraction: () -> Unit,
    private val notifySelectionChanged: () -> Unit,
    private val invalidate: () -> Unit
) {
    companion object {
        private const val TAG = "EditorLayerController"
    }

    /** Returns the number of editable layers. */
    fun getLayerCount(): Int = elements.size

    /** Returns the element at the requested layer index, or null if invalid. */
    fun getLayer(index: Int): EditorElement? = elements.getOrNull(index)

    /** Returns the current layer index of an element, or -1 if absent. */
    fun getLayerIndex(element: EditorElement): Int = elements.indexOf(element)

    /** Selects an existing element by its layer index. */
    fun selectLayer(index: Int): Boolean {
        val element = elements.getOrNull(index) ?: return false
        selectElement(element)
        return true
    }

    /** Duplicates the selected element immediately above the original layer. */
    fun duplicateSelectedElement(): Boolean {
        if (isEditorModeActive()) {
            Log.d(TAG, "Duplicate ignored: editor mode is active")
            return false
        }

        val element = getSelectedElement() ?: return false
        val historyBefore = captureHistoryState()
        val currentIndex = elements.indexOf(element)
        if (currentIndex < 0) {
            Log.w(TAG, "Duplicate ignored: selected element not found")
            return false
        }

        val duplicate = element.duplicate()
        duplicate.isVisible = true

        element.isSelected = false
        duplicate.isSelected = true
        elements.add(currentIndex + 1, duplicate)
        setSelectedElement(duplicate)
        resetElementInteraction()

        Log.d(
            TAG,
            "Element duplicated. Original index=$currentIndex, " +
                    "duplicate index=${currentIndex + 1}, total=${elements.size}"
        )
        notifySelectionChanged()
        invalidate()
        recordHistory(historyBefore, captureHistoryState())
        return true
    }

    /** Toggles visibility of the currently selected layer. */
    fun toggleSelectedElementVisibility(): Boolean {
        if (isEditorModeActive()) {
            Log.d(TAG, "Visibility change ignored: editor mode is active")
            return false
        }

        val element = getSelectedElement() ?: return false
        if (!elements.contains(element)) return false

        val historyBefore = captureHistoryState()
        element.isVisible = !element.isVisible
        element.isSelected = element.isVisible

        Log.d(TAG, "Layer visibility changed: visible=${element.isVisible}")
        notifySelectionChanged()
        invalidate()
        recordHistory(historyBefore, captureHistoryState())
        return true
    }

    /** Toggles the lock state of the currently selected layer. */
    fun toggleSelectedElementLock(): Boolean {
        if (isEditorModeActive()) {
            Log.d(TAG, "Lock change ignored: editor mode is active")
            return false
        }

        val element = getSelectedElement() ?: return false
        if (!elements.contains(element)) return false

        val historyBefore = captureHistoryState()
        element.isLocked = !element.isLocked
        resetElementInteraction()

        Log.d(TAG, "Layer lock changed: locked=${element.isLocked}")
        notifySelectionChanged()
        invalidate()
        recordHistory(historyBefore, captureHistoryState())
        return true
    }

    /** Sets the lock state of the currently selected layer. */
    fun setSelectedElementLocked(locked: Boolean): Boolean {
        if (isEditorModeActive()) {
            Log.d(TAG, "Lock change ignored: editor mode is active")
            return false
        }

        val element = getSelectedElement() ?: return false
        if (!elements.contains(element)) return false

        val historyBefore = captureHistoryState()
        element.isLocked = locked
        resetElementInteraction()

        Log.d(TAG, "Layer lock set: locked=$locked")
        notifySelectionChanged()
        invalidate()
        recordHistory(historyBefore, captureHistoryState())
        return true
    }

    /** Moves the selected element to the top-most layer. */
    fun bringSelectedElementToFront(): Boolean {
        val historyBefore = captureHistoryState()
        val element = getSelectedElement() ?: return false
        val currentIndex = elements.indexOf(element)
        if (currentIndex < 0 || currentIndex == elements.lastIndex) return false

        elements.removeAt(currentIndex)
        elements.add(element)
        invalidate()
        recordHistory(historyBefore, captureHistoryState())
        return true
    }

    /** Moves the selected element to the bottom-most layer. */
    fun sendSelectedElementToBack(): Boolean {
        val historyBefore = captureHistoryState()
        val element = getSelectedElement() ?: return false
        val currentIndex = elements.indexOf(element)
        if (currentIndex <= 0) return false

        elements.removeAt(currentIndex)
        elements.add(0, element)
        invalidate()
        recordHistory(historyBefore, captureHistoryState())
        return true
    }

    /** Moves the selected element up by one layer. */
    fun bringSelectedElementForward(): Boolean {
        val historyBefore = captureHistoryState()
        val element = getSelectedElement() ?: return false
        val currentIndex = elements.indexOf(element)
        if (currentIndex < 0 || currentIndex == elements.lastIndex) return false

        elements[currentIndex] = elements[currentIndex + 1].also {
            elements[currentIndex + 1] = element
        }
        invalidate()
        recordHistory(historyBefore, captureHistoryState())
        return true
    }

    /** Moves the selected element down by one layer. */
    fun sendSelectedElementBackward(): Boolean {
        val historyBefore = captureHistoryState()
        val element = getSelectedElement() ?: return false
        val currentIndex = elements.indexOf(element)
        if (currentIndex <= 0) return false

        elements[currentIndex] = elements[currentIndex - 1].also {
            elements[currentIndex - 1] = element
        }
        invalidate()
        recordHistory(historyBefore, captureHistoryState())
        return true
    }
}

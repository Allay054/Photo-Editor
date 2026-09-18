package com.allay.photoeditor.editor.history

import android.graphics.Bitmap
import android.util.Log
import com.allay.photoeditor.editor.core.EditorElementStore
import com.allay.photoeditor.model.EditorElement

/**
 * Coordinates global editor history while keeping PhotoEditorView as the
 * public editor facade.
 *
 * The coordinator owns snapshot creation, restoration, history recording and
 * Undo/Redo state. View-specific cleanup and callbacks are supplied by the
 * host so existing behavior remains unchanged.
 */
class EditorHistoryCoordinator(
    private val tag: String,
    private val elementStore: EditorElementStore,
    private val getBitmap: () -> Bitmap?,
    private val setBitmap: (Bitmap) -> Unit,
    private val getSelectedElement: () -> EditorElement?,
    private val setSelectedElement: (EditorElement?) -> Unit,
    private val resetEditorInteraction: () -> Unit,
    private val clearBitmapCaches: () -> Unit,
    private val notifySelectionChanged: () -> Unit,
    private val invalidate: () -> Unit,
    private val prepareForHistoryRestore: () -> Unit,
    private val onHistoryChanged: () -> Unit
) {
    private val historyController = EditorHistoryController()

    /**
     * Captures the current editor state.
     *
     * Viewport state (zoom/pan) is intentionally excluded from editor history.
     * The bitmap is copied only when an image operation needs bitmap history.
     */
    fun capture(includeBitmap: Boolean = false): EditorStateSnapshot {
        val elementCopies = elementStore.elements.map { element ->
            element.duplicate().also { copy ->
                copy.isSelected = false
                copy.isVisible = element.isVisible
                copy.isLocked = element.isLocked
            }
        }

        return EditorStateSnapshot(
            elements = elementCopies,
            selectedElementIndex =
                elementStore.elements.indexOf(getSelectedElement()),
            bitmap = if (includeBitmap) {
                getBitmap()?.let { currentBitmap ->
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
     */
    fun restore(state: EditorStateSnapshot) {
        prepareForHistoryRestore()

        state.bitmap?.let { restoredBitmap ->
            setBitmap(restoredBitmap)
            clearBitmapCaches()
        }

        getSelectedElement()?.isSelected = false

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

        val restoredSelection =
            elementStore.elements.getOrNull(state.selectedElementIndex)

        setSelectedElement(restoredSelection)

        elementStore.elements.forEach { element ->
            element.isSelected = element === restoredSelection
        }

        resetEditorInteraction()
        notifySelectionChanged()
        invalidate()
    }

    /**
     * Records one completed editor operation.
     */
    fun record(
        before: EditorStateSnapshot,
        after: EditorStateSnapshot
    ) {
        historyController.record(
            before = before,
            after = after
        )
        onHistoryChanged()
    }

    /**
     * Undoes the most recent global editor operation.
     */
    fun undo(): Boolean {
        val state = historyController.undo(
            currentState = capture()
        ) ?: return false

        restore(state)
        onHistoryChanged()
        Log.d(tag, "Global editor undo performed")
        return true
    }

    /**
     * Redoes the most recently undone global editor operation.
     */
    fun redo(): Boolean {
        val state = historyController.redo(
            currentState = capture()
        ) ?: return false

        restore(state)
        onHistoryChanged()
        Log.d(tag, "Global editor redo performed")
        return true
    }

    fun canUndo(): Boolean = historyController.canUndo()

    fun canRedo(): Boolean = historyController.canRedo()

    fun undoCount(): Int = historyController.undoCount()

    fun redoCount(): Int = historyController.redoCount()

    /**
     * Clears global editor history without changing the current editor state.
     */
    fun clear() {
        historyController.clear()
        onHistoryChanged()
        Log.d(tag, "Global editor history cleared")
    }
}

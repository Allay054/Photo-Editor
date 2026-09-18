package com.allay.photoeditor.editor.core

import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import com.allay.photoeditor.editor.annotation.AnnotationHistoryController
import com.allay.photoeditor.editor.history.EditorStateSnapshot
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.TextElement

/**
 * Owns creation and deletion of editor elements while PhotoEditorView keeps
 * the existing public API and coordinates the editor subsystems.
 *
 * This extraction intentionally preserves the existing selection, annotation
 * history, global history and invalidation behavior.
 */
class EditorElementOperations(
    private val tag: String,
    private val elementStore: EditorElementStore,
    private val getSelectedElement: () -> EditorElement?,
    private val setSelectedElement: (EditorElement?) -> Unit,
    private val setTransformModeNone: () -> Unit,
    private val captureHistoryState: () -> EditorStateSnapshot,
    private val recordHistory: (EditorStateSnapshot, EditorStateSnapshot) -> Unit,
    private val notifySelectionChanged: () -> Unit,
    private val invalidate: () -> Unit,
    private val annotationHistoryController: AnnotationHistoryController,
    private val onAnnotationHistoryChanged: () -> Unit,
    private val getBitmap: () -> android.graphics.Bitmap?
) {
    fun addElement(element: EditorElement) {
        val historyBefore = captureHistoryState()

        // Deselect the previous element.
        getSelectedElement()?.isSelected = false

        // New element becomes selected.
        element.isSelected = true
        setSelectedElement(element)

        elementStore.elements.add(element)

        Log.d(
            tag,
            "Element added. Total elements: ${elementStore.elements.size}"
        )

        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    fun deleteSelectedElement() {
        val element = getSelectedElement() ?: return

        if (element.isLocked) {
            Log.d(tag, "Delete ignored: selected element is locked")
            return
        }

        Log.d(tag, "Deleting selected element")

        val historyBefore = captureHistoryState()

        val annotationHistoryBeforeDelete =
            if (element is AnnotationElement) {
                annotationHistoryController.capture(elementStore.elements)
            } else {
                null
            }

        elementStore.elements.remove(element)
        element.isSelected = false
        setSelectedElement(null)
        setTransformModeNone()
        notifySelectionChanged()

        if (annotationHistoryBeforeDelete != null) {
            val annotationHistoryAfterDelete =
                annotationHistoryController.capture(elementStore.elements)

            annotationHistoryController.record(
                before = annotationHistoryBeforeDelete,
                after = annotationHistoryAfterDelete
            )

            onAnnotationHistoryChanged()
        }

        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    fun addTestText(): Boolean {
        val currentBitmap = getBitmap() ?: return false

        val position = PointF(
            currentBitmap.width / 2f,
            currentBitmap.height / 2f
        )

        val textElement = TextElement(
            text = "Hello Photo Editor",
            position = position,
            textSize = 80f,
            color = Color.WHITE
        )

        addElement(textElement)
        return true
    }
}

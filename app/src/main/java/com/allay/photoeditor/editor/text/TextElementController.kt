package com.allay.photoeditor.editor.text

import android.util.Log
import com.allay.photoeditor.editor.history.EditorStateSnapshot
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.TextElement

/**
 * Owns TextElement-specific property editing.
 *
 * PhotoEditorView remains the public facade and supplies selection, history,
 * invalidation, and element-store coordination through callbacks.
 *
 * Existing TextElement behavior is intentionally preserved. This controller
 * only extracts the text-property update responsibility; it does not change
 * TextElement itself or the public PhotoEditorView API.
 */
class TextElementController(
    private val elements: List<EditorElement>,
    private val captureHistoryState: () -> EditorStateSnapshot,
    private val recordHistory: (EditorStateSnapshot, EditorStateSnapshot) -> Unit,
    private val selectElement: (EditorElement?) -> Unit,
    private val notifySelectionChanged: () -> Unit,
    private val invalidate: () -> Unit
) {

    companion object {
        private const val TAG = "PhotoEditor"
    }

    /**
     * Updates the text content of an existing TextElement.
     */
    fun updateTextElement(
        textElement: TextElement,
        newText: String
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update text. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateText(newText)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text element updated: $newText")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    /**
     * Updates the primary color of an existing TextElement.
     */
    fun updateTextElementColor(
        textElement: TextElement,
        newColor: Int
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update text color. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateColor(newColor)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text color updated: $newColor")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
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
            Log.w(TAG, "Cannot update text color range. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateColorRange(
            start,
            end,
            newColor
        )
        selectUpdatedTextElement(textElement)
        Log.d(
            TAG,
            "Text color range updated: start=$start end=$end color=$newColor"
        )
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
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
            Log.w(TAG, "Cannot restore text color ranges. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.setColorRanges(ranges)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text color ranges restored: ${ranges.size} ranges")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    /**
     * Updates the text size of an existing TextElement.
     */
    fun updateTextElementSize(
        textElement: TextElement,
        newSize: Float
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update text size. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateTextSize(newSize)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text size updated: ${textElement.textSize}")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    /**
     * Updates the Bold state.
     */
    fun updateTextElementBold(
        textElement: TextElement,
        enabled: Boolean
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update text bold. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateBold(enabled)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text bold updated: ${textElement.bold}")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    /**
     * Updates the Italic state.
     */
    fun updateTextElementItalic(
        textElement: TextElement,
        enabled: Boolean
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update text italic. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateItalic(enabled)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text italic updated: ${textElement.italic}")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    /**
     * Updates text alignment.
     */
    fun updateTextElementAlignment(
        textElement: TextElement,
        alignment: TextElement.TextAlignment
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update text alignment. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateAlignment(alignment)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text alignment updated: ${textElement.alignment}")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
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

        val historyBefore = captureHistoryState()

        textElement.updateFont(font)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text font updated: ${textElement.getFontDisplayName()}")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    /**
     * Updates the background enabled state of an existing TextElement.
     */
    fun updateTextElementBackground(
        textElement: TextElement,
        enabled: Boolean
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update text background. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateBackground(enabled)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text background updated: $enabled")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    /**
     * Updates the background color of an existing TextElement.
     */
    fun updateTextElementBackgroundColor(
        textElement: TextElement,
        newColor: Int
    ) {
        if (!elements.contains(textElement)) {
            Log.w(TAG, "Cannot update text background color. Element not found.")
            return
        }

        val historyBefore = captureHistoryState()

        textElement.updateBackgroundColor(newColor)
        selectUpdatedTextElement(textElement)
        Log.d(TAG, "Text background color updated: $newColor")
        notifySelectionChanged()
        invalidate()

        recordHistory(
            historyBefore,
            captureHistoryState()
        )
    }

    /** Marks an existing text element as the active selection after an update. */
    private fun selectUpdatedTextElement(textElement: TextElement) {
        textElement.isSelected = true
        selectElement(textElement)
    }
}

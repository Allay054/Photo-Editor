package com.allay.photoeditor.editor.history

import android.graphics.Bitmap
import com.allay.photoeditor.model.EditorElement

/**
 * Immutable snapshot of the editor state used by the global
 * Undo / Redo history system.
 *
 * Phase 11.1
 *
 * The snapshot stores:
 * - editor elements
 * - selected element index
 * - optional bitmap state
 *
 * Viewport state such as zoom, pan and screen translation is
 * intentionally NOT stored because viewport changes are not
 * editor operations.
 */
data class EditorStateSnapshot(
    val elements: List<EditorElement>,
    val selectedElementIndex: Int,
    val bitmap: Bitmap?
)
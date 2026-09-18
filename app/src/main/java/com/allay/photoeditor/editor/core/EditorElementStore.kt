package com.allay.photoeditor.editor.core

import com.allay.photoeditor.model.EditorElement

/**
 * Owns the editor's element collection.
 *
 * Editor elements remain stored in ORIGINAL IMAGE coordinates and their list
 * order remains the single source of truth for layer order:
 * - index 0 = bottom-most layer
 * - last index = top-most layer
 *
 * Phase 11.10.1 intentionally keeps this class small. Selection, history,
 * gestures and higher-level layer operations remain coordinated by
 * PhotoEditorView for now and will be extracted in later refactor steps.
 */
class EditorElementStore {

    /**
     * The single mutable collection of editor elements.
     *
     * The collection itself is exposed to the editor package so existing
     * behavior can be migrated incrementally without changing element APIs or
     * duplicating state.
     */
    val elements: MutableList<EditorElement> = mutableListOf()

    /** Returns the number of editor elements currently stored. */
    val size: Int
        get() = elements.size

    /** Returns the element at [index], or null when the index is invalid. */
    fun getOrNull(index: Int): EditorElement? = elements.getOrNull(index)

    /** Returns the index of [element], or -1 when it is not stored. */
    fun indexOf(element: EditorElement): Int = elements.indexOf(element)

    /** Returns true when [element] is currently stored. */
    fun contains(element: EditorElement): Boolean = elements.contains(element)

    /** Removes all stored elements. */
    fun clear() {
        elements.clear()
    }
}

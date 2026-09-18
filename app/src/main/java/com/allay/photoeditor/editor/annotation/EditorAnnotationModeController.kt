package com.allay.photoeditor.editor.annotation

/**
 * Owns annotation mode flags that control which annotation interaction path
 * receives canvas touch input.
 *
 * This controller intentionally does not own AnnotationElement data or drawing
 * behavior. AnnotationController and AnnotationInteractionController continue
 * to own those responsibilities.
 */
class EditorAnnotationModeController {

    private var freehandActive = false
    private var eraserActive = false
    private var selectionVisible = false

    val isFreehandActive: Boolean
        get() = freehandActive

    val isEraserActive: Boolean
        get() = eraserActive

    val isSelectionVisible: Boolean
        get() = selectionVisible

    fun enterDrawingMode() {
        freehandActive = true
        eraserActive = false
        selectionVisible = true
    }

    fun enterEraserMode() {
        freehandActive = false
        eraserActive = true
        selectionVisible = true
    }

    fun enterSelectionMode() {
        freehandActive = false
        eraserActive = false
        selectionVisible = true
    }

    fun setSelectionVisible(visible: Boolean) {
        selectionVisible = visible
    }

    fun exit() {
        freehandActive = false
        eraserActive = false
        selectionVisible = false
    }

    fun stopDrawingModes() {
        freehandActive = false
        eraserActive = false
    }

    fun reset() {
        freehandActive = false
        eraserActive = false
        selectionVisible = false
    }
}

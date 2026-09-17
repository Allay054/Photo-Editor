package com.allay.photoeditor.editor.element

import com.allay.photoeditor.model.EditorElement

/**
 * Owns the editor element collection and selection state.
 *
 * PhotoEditorView remains responsible for view-specific behavior such as
 * gesture modes, invalidation and callbacks to the hosting Activity.
 */
class ElementController(
    private val onSelectionChanged: (EditorElement?) -> Unit
) {

    val elements = mutableListOf<EditorElement>()

    var selectedElement: EditorElement? = null
        private set

    /**
     * Adds an element and makes it the selected element.
     */
    fun addElement(element: EditorElement) {
        selectedElement?.isSelected = false

        elements.add(element)

        selectedElement = element
        selectedElement?.isSelected = true

        onSelectionChanged(selectedElement)
    }

    /**
     * Changes the current selection.
     *
     * @return true when the selection actually changed.
     */
    fun selectElement(
        element: EditorElement?,
        notifyIfAlreadySelected: Boolean = false
    ): Boolean {
        if (selectedElement === element) {
            if (notifyIfAlreadySelected) {
                onSelectionChanged(selectedElement)
            }
            return false
        }

        selectedElement?.isSelected = false
        selectedElement = element
        selectedElement?.isSelected = true

        onSelectionChanged(selectedElement)
        return true
    }

    /**
     * Removes the currently selected element.
     *
     * @return true when an element was removed.
     */
    fun removeSelectedElement(): Boolean {
        val element = selectedElement ?: return false

        elements.remove(element)
        element.isSelected = false
        selectedElement = null

        onSelectionChanged(null)
        return true
    }

    /**
     * Removes all elements and clears selection.
     */
    fun clearElements() {
        elements.forEach { it.isSelected = false }
        elements.clear()
        selectedElement = null

        onSelectionChanged(null)
    }

    /**
     * Replaces the complete element list, normally when restoring a temporary
     * crop or rotation session.
     */
    fun replaceElements(
        newElements: List<EditorElement>,
        selectedIndex: Int = -1
    ) {
        elements.clear()
        elements.addAll(newElements)

        selectedElement = newElements.getOrNull(selectedIndex)

        elements.forEach { element ->
            element.isSelected = element === selectedElement
        }

        onSelectionChanged(selectedElement)
    }

    /**
     * Finds the top-most element at the supplied image coordinate.
     */
    fun findElementAt(
        imageX: Float,
        imageY: Float
    ): EditorElement? {
        for (index in elements.indices.reversed()) {
            val element = elements[index]

            if (element.contains(imageX, imageY)) {
                return element
            }
        }

        return null
    }
}

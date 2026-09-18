package com.allay.photoeditor.editor.selection

import android.graphics.PathMeasure
import android.util.Log
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.EditorElement

/**
 * Owns selected-element state and canvas hit testing.
 *
 * The controller deliberately does not own editor history or layer ordering.
 * Those responsibilities remain in their existing subsystems. This keeps the
 * refactor incremental while giving Text, Shape and Annotation one shared
 * selection pipeline.
 */
class EditorSelectionController(
    private val elements: List<EditorElement>,
    private val resetSelectionTransform: () -> Unit,
    private val onSelectionChanged: (EditorElement?) -> Unit,
    private val invalidate: () -> Unit,
    private val annotationSelectionTouchPadding: Float = 24f
) {
    companion object {
        private const val TAG = "EditorSelectionController"
    }

    /** The currently selected editor element, or null when nothing is selected. */
    var selectedElement: EditorElement? = null
        private set

    /**
     * Updates the selected element directly for state restoration.
     *
     * This intentionally does not notify listeners. Existing restore paths in
     * PhotoEditorView set selection flags and then explicitly notify once the
     * complete editor state has been restored.
     */
    fun setSelectedElement(element: EditorElement?) {
        selectedElement = element
    }

    /** Selects an element and preserves the existing selection behavior. */
    fun selectElement(element: EditorElement?) {
        if (selectedElement === element) {
            return
        }

        selectedElement?.isSelected = false
        selectedElement = element
        selectedElement?.isSelected = true

        resetSelectionTransform()

        Log.d(TAG, "Selected element: $selectedElement")
        notifySelectionChanged()
        invalidate()
    }

    /** Notifies the existing PhotoEditorView selection callback. */
    fun notifySelectionChanged() {
        onSelectionChanged(selectedElement)
    }

    /**
     * Finds the top-most visible element at an image-space point.
     *
     * Annotation paths receive the same generous touch tolerance that existed
     * in PhotoEditorView so thin freehand/Pen/Highlighter strokes remain easy
     * to select after scaling.
     */
    fun findElementAt(
        imageX: Float,
        imageY: Float
    ): EditorElement? {
        for (index in elements.indices.reversed()) {
            val element = elements[index]

            // Hidden layers remain available in the Layers panel but cannot be
            // selected or interacted with directly on the canvas.
            if (!element.isVisible) {
                continue
            }

            if (element is AnnotationElement) {
                if (isPointNearAnnotation(element, imageX, imageY)) {
                    return element
                }
            } else if (element.contains(imageX, imageY)) {
                return element
            }
        }

        return null
    }

    private fun isPointNearAnnotation(
        annotation: AnnotationElement,
        imageX: Float,
        imageY: Float
    ): Boolean {
        val bounds = annotation.getBounds()
        val tolerance = maxOf(
            annotation.strokeWidth * 1.5f,
            annotationSelectionTouchPadding
        )

        if (
            imageX < bounds.left - tolerance ||
            imageX > bounds.right + tolerance ||
            imageY < bounds.top - tolerance ||
            imageY > bounds.bottom + tolerance
        ) {
            return false
        }

        val measure = PathMeasure(annotation.path, false)
        val position = FloatArray(2)
        val step = 12f.coerceAtLeast(annotation.strokeWidth / 2f)

        do {
            val length = measure.length

            if (length <= 0f) {
                if (measure.getPosTan(0f, position, null)) {
                    val dx = position[0] - imageX
                    val dy = position[1] - imageY
                    if (dx * dx + dy * dy <= tolerance * tolerance) {
                        return true
                    }
                }
            } else {
                var distanceAlongPath = 0f
                while (distanceAlongPath <= length) {
                    if (!measure.getPosTan(distanceAlongPath, position, null)) {
                        break
                    }

                    val dx = position[0] - imageX
                    val dy = position[1] - imageY
                    if (dx * dx + dy * dy <= tolerance * tolerance) {
                        return true
                    }

                    distanceAlongPath += step
                }

                if (measure.getPosTan(length, position, null)) {
                    val dx = position[0] - imageX
                    val dy = position[1] - imageY
                    if (dx * dx + dy * dy <= tolerance * tolerance) {
                        return true
                    }
                }
            }
        } while (measure.nextContour())

        return false
    }
}

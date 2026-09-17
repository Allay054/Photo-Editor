package com.allay.photoeditor.editor.annotation

import android.graphics.Path
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.EditorElement

/**
 * Keeps Undo/Redo history for annotation operations only.
 *
 * Text, shapes and other editor elements are intentionally not included.
 */
class AnnotationHistoryController {

    companion object {
        private const val MAX_HISTORY_SIZE = 50
    }

    data class AnnotationSnapshot(
        val index: Int,
        val annotationType: com.allay.photoeditor.model.AnnotationType,
        val path: Path,
        val color: Int,
        val strokeWidth: Float
    )

    data class State(
        val annotations: List<AnnotationSnapshot>
    )

    private val undoStack = ArrayDeque<Pair<State, State>>()
    private val redoStack = ArrayDeque<Pair<State, State>>()

    fun capture(elements: List<EditorElement>): State {
        return State(
            annotations = elements.mapIndexedNotNull { index, element ->
                val annotation = element as? AnnotationElement
                    ?: return@mapIndexedNotNull null

                AnnotationSnapshot(
                    index = index,
                    annotationType = annotation.annotationType,
                    path = Path(annotation.path),
                    color = annotation.color,
                    strokeWidth = annotation.strokeWidth
                )
            }
        )
    }

    fun record(
        before: State,
        after: State
    ) {
        if (areEqual(before, after)) {
            return
        }

        undoStack.addLast(before to after)

        if (undoStack.size > MAX_HISTORY_SIZE) {
            undoStack.removeFirst()
        }

        redoStack.clear()
    }

    fun canUndo(): Boolean {
        return undoStack.isNotEmpty()
    }

    fun canRedo(): Boolean {
        return redoStack.isNotEmpty()
    }

    fun undo(): State? {
        val operation = undoStack.removeLastOrNull()
            ?: return null

        redoStack.addLast(operation)

        return operation.first
    }

    fun redo(): State? {
        val operation = redoStack.removeLastOrNull()
            ?: return null

        undoStack.addLast(operation)

        return operation.second
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    private fun areEqual(
        first: State,
        second: State
    ): Boolean {

        if (first.annotations.size != second.annotations.size) {
            return false
        }

        first.annotations.zip(second.annotations).forEach { (a, b) ->

            if (a.index != b.index) return false
            if (a.annotationType != b.annotationType) return false
            if (a.color != b.color) return false
            if (a.strokeWidth != b.strokeWidth) return false

            if (!pathsEqual(a.path, b.path)) {
                return false
            }
        }

        return true
    }

    /**
     * Path does not expose a cheap structural equality API.
     *
     * We compare its approximate sampled geometry through PathMeasure.
     */
    private fun pathsEqual(
        first: Path,
        second: Path
    ): Boolean {

        val firstBounds = android.graphics.RectF()
        val secondBounds = android.graphics.RectF()

        first.computeBounds(firstBounds, true)
        second.computeBounds(secondBounds, true)

        if (firstBounds != secondBounds) {
            return false
        }

        val firstMeasure =
            android.graphics.PathMeasure(first, false)

        val secondMeasure =
            android.graphics.PathMeasure(second, false)

        if (firstMeasure.length != secondMeasure.length) {
            return false
        }

        val length = firstMeasure.length

        if (length <= 0f) {
            return true
        }

        val positionA = FloatArray(2)
        val positionB = FloatArray(2)

        val sampleCount =
            (length / 8f).toInt().coerceIn(8, 200)

        for (index in 0..sampleCount) {

            val distance =
                length * index.toFloat() / sampleCount.toFloat()

            if (!firstMeasure.getPosTan(distance, positionA, null)) {
                return false
            }

            if (!secondMeasure.getPosTan(distance, positionB, null)) {
                return false
            }

            if (
                kotlin.math.abs(positionA[0] - positionB[0]) > 0.5f ||
                kotlin.math.abs(positionA[1] - positionB[1]) > 0.5f
            ) {
                return false
            }
        }

        return true
    }
}
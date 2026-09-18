package com.allay.photoeditor.editor.annotation

import android.graphics.Path
import android.graphics.PointF
import android.util.Log
import com.allay.photoeditor.editor.core.EditorCoordinateMapper
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.AnnotationType
import com.allay.photoeditor.model.EditorElement
import kotlin.math.hypot

/**
 * Owns transient annotation drawing and eraser interaction state.
 *
 * PhotoEditorView remains the coordinator for annotation modes, public APIs
 * and rendering. This controller owns only touch-time drawing/eraser state
 * and the image-space interaction logic.
 */
class AnnotationInteractionController(
    private val coordinateMapper: EditorCoordinateMapper,
    private val annotationController: AnnotationController,
    private val elements: MutableList<EditorElement>,
    private val addElement: (EditorElement) -> Unit,
    private val getSelectedElement: () -> EditorElement?,
    private val setSelectedElement: (EditorElement?) -> Unit,
    private val notifySelectionChanged: () -> Unit,
    private val finishAnnotationHistoryGesture: () -> Unit,
    private val cancelAnnotationHistoryGesture: () -> Unit,
    private val invalidate: () -> Unit
) {
    companion object {
        private const val TAG = "AnnotationInteraction"
    }

    private var activeAnnotationPath: Path? = null
    private var activeAnnotationPointCount = 0
    private var activeAnnotationType: AnnotationType = AnnotationType.FREEHAND
    private var activeEraserPoint: PointF? = null
    private var lastEraserImagePoint: PointF? = null

    var currentAnnotationPath: Path?
        get() = activeAnnotationPath
        set(value) { activeAnnotationPath = value }

    val currentAnnotationType: AnnotationType
        get() = activeAnnotationType

    var currentEraserPoint: PointF?
        get() = activeEraserPoint
        set(value) { activeEraserPoint = value }

    fun reset() {
        activeAnnotationPath = null
        activeAnnotationPointCount = 0
        activeEraserPoint = null
        lastEraserImagePoint = null
    }

    fun setAnnotationType(annotationType: AnnotationType) {
        activeAnnotationType = annotationType
    }

    fun startFreehandPath(
        screenX: Float,
        screenY: Float
    ): Boolean {
        val imagePoint = coordinateMapper.screenToImage(screenX, screenY)
            ?: return false

        val path = Path()
        path.moveTo(imagePoint.x, imagePoint.y)

        activeAnnotationPath = path
        activeAnnotationPointCount = 1
        return true
    }

    fun appendFreehandPoint(
        screenX: Float,
        screenY: Float
    ): Boolean {
        val path = activeAnnotationPath ?: return false
        val imagePoint = coordinateMapper.screenToImage(screenX, screenY)
            ?: return false

        path.lineTo(imagePoint.x, imagePoint.y)
        activeAnnotationPointCount++
        invalidate()
        return true
    }

    fun finishActiveFreehandPath(commit: Boolean) {
        val path = activeAnnotationPath
        var historyCommitted = false

        if (
            commit &&
            activeAnnotationType != AnnotationType.ERASER &&
            path != null &&
            activeAnnotationPointCount >= 2
        ) {
            val annotation = annotationController.createAnnotation(
                path = path,
                annotationType = activeAnnotationType,
                color = annotationController.currentColor,
                strokeWidth = annotationController.currentStrokeWidth
            )

            addElement(annotation)
            historyCommitted = true

            Log.d(
                TAG,
                "Freehand annotation added. Total elements=${elements.size}"
            )
        }

        activeAnnotationPath = null
        activeAnnotationPointCount = 0
        activeAnnotationType = AnnotationType.FREEHAND

        if (historyCommitted) {
            finishAnnotationHistoryGesture()
        } else {
            cancelAnnotationHistoryGesture()
        }
    }

    fun eraseAtScreenPoint(
        screenX: Float,
        screenY: Float
    ): Boolean {
        val imagePoint = coordinateMapper.screenToImage(screenX, screenY)
            ?: return false

        var changed = false
        activeEraserPoint = PointF(screenX, screenY)

        val previous = lastEraserImagePoint
        if (previous == null) {
            changed = eraseAnnotationsAt(
                imageX = imagePoint.x,
                imageY = imagePoint.y
            ) || changed
        } else {
            val dx = imagePoint.x - previous.x
            val dy = imagePoint.y - previous.y
            val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
            val step = (annotationController.currentStrokeWidth / 2f)
                .coerceAtLeast(2f)
            val steps = (distance / step).toInt().coerceAtLeast(1)

            for (index in 1..steps) {
                val fraction = index.toFloat() / steps.toFloat()
                val sampleX = previous.x + dx * fraction
                val sampleY = previous.y + dy * fraction

                changed = eraseAnnotationsAt(
                    imageX = sampleX,
                    imageY = sampleY
                ) || changed
            }
        }

        lastEraserImagePoint = PointF(
            imagePoint.x,
            imagePoint.y
        )

        invalidate()
        return changed
    }

    private fun eraseAnnotationsAt(
        imageX: Float,
        imageY: Float
    ): Boolean {
        val radius = annotationController.currentStrokeWidth / 2f
        var changed = false
        val iterator = elements.listIterator()

        while (iterator.hasNext()) {
            val element = iterator.next()
            if (element !is AnnotationElement) continue

            val bounds = element.getBounds()
            if (
                imageX < bounds.left - radius ||
                imageX > bounds.right + radius ||
                imageY < bounds.top - radius ||
                imageY > bounds.bottom + radius
            ) {
                continue
            }

            val remains = element.eraseAt(
                x = imageX,
                y = imageY,
                radius = radius
            )

            changed = true

            if (!remains) {
                element.isSelected = false
                if (getSelectedElement() === element) {
                    setSelectedElement(null)
                }
                iterator.remove()
            }
        }

        if (changed) {
            notifySelectionChanged()
            invalidate()
        }

        return changed
    }

    fun clearEraserState() {
        activeEraserPoint = null
        lastEraserImagePoint = null
    }
}

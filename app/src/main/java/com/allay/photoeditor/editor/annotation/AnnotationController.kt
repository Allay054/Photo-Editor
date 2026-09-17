package com.allay.photoeditor.editor.annotation

import android.graphics.Color
import android.graphics.Path
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.AnnotationType

/**
 * Creates and manages annotation-related configuration.
 *
 * Color and stroke width are shared by all drawing-style annotation tools
 * so Freehand, Pen, and later Highlighter tools can use one consistent UI.
 */
class AnnotationController {

    companion object {

        const val MIN_STROKE_WIDTH = 4f

        const val MAX_STROKE_WIDTH = 96f

        const val DEFAULT_STROKE_WIDTH = 24f

        const val DEFAULT_COLOR = Color.WHITE
    }

    /** Currently selected annotation tool. */
    var currentAnnotationType: AnnotationType =
        AnnotationType.FREEHAND
        private set

    /** Current annotation color. */
    var currentColor: Int =
        DEFAULT_COLOR
        private set

    /** Current annotation stroke width in image coordinates. */
    var currentStrokeWidth: Float =
        DEFAULT_STROKE_WIDTH
        private set

    /** Changes the active annotation tool. */
    fun setAnnotationType(annotationType: AnnotationType) {
        currentAnnotationType = annotationType
    }

    /** Changes the color used by newly created annotations. */
    fun setColor(color: Int) {
        currentColor = color
    }

    /** Changes the stroke width used by newly created annotations. */
    fun setStrokeWidth(strokeWidth: Float) {
        require(strokeWidth in MIN_STROKE_WIDTH..MAX_STROKE_WIDTH) {
            "Stroke width must be between $MIN_STROKE_WIDTH and $MAX_STROKE_WIDTH."
        }

        currentStrokeWidth = strokeWidth
    }

    /**
     * Creates an annotation element using the current controller
     * configuration unless explicit values are supplied.
     */
    fun createAnnotation(
        path: Path,
        annotationType: AnnotationType = currentAnnotationType,
        color: Int = currentColor,
        strokeWidth: Float = currentStrokeWidth
    ): AnnotationElement {

        require(strokeWidth in MIN_STROKE_WIDTH..MAX_STROKE_WIDTH) {
            "Stroke width must be between $MIN_STROKE_WIDTH and $MAX_STROKE_WIDTH."
        }

        return AnnotationElement(
            annotationType = annotationType,
            path = Path(path),
            color = color,
            strokeWidth = strokeWidth
        )
    }
}

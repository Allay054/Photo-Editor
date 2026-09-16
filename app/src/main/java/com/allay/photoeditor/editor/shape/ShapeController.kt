package com.allay.photoeditor.editor.shape

import android.graphics.Color
import android.graphics.PointF
import com.allay.photoeditor.model.ShapeElement
import com.allay.photoeditor.model.ShapeType

/**
 * Creates and manages shape-related configuration.
 *
 * Interaction logic will be added incrementally in later
 * Phase 8 sub-phases.
 */
class ShapeController {

    // =========================================================================
    // DEFAULTS
    // =========================================================================

    companion object {

        const val DEFAULT_SHAPE_WIDTH =
            ShapeElement.DEFAULT_WIDTH

        const val DEFAULT_SHAPE_HEIGHT =
            ShapeElement.DEFAULT_HEIGHT

        const val DEFAULT_STROKE_WIDTH =
            ShapeElement.DEFAULT_STROKE_WIDTH

        const val DEFAULT_COLOR =
            Color.WHITE
    }

    // =========================================================================
    // CREATE SHAPE
    // =========================================================================

    /**
     * Creates a new ShapeElement.
     *
     * The returned element is not automatically added to
     * PhotoEditorView. The editor remains responsible for
     * maintaining its element collection and selection state.
     */
    fun createShape(
        shapeType: ShapeType,
        position: PointF,
        width: Float = DEFAULT_SHAPE_WIDTH,
        height: Float = DEFAULT_SHAPE_HEIGHT,
        color: Int = DEFAULT_COLOR,
        strokeWidth: Float = DEFAULT_STROKE_WIDTH,
        isFilled: Boolean = false
    ): ShapeElement {

        return ShapeElement(
            shapeType = shapeType,
            position = PointF(
                position.x,
                position.y
            ),
            width = width,
            height = height,
            color = color,
            strokeWidth = strokeWidth,
            isFilled = isFilled
        )
    }
}
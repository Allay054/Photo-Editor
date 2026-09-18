package com.allay.photoeditor.model

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF

sealed class EditorElement {

    /**
     * True when this element is currently selected.
     */
    var isSelected: Boolean = false

    /**
     * True when this element is visible on the canvas.
     * Hidden elements remain in the layer stack and can be managed from the Layers panel.
     */
    var isVisible: Boolean = true

    /**
     * True when this element is locked.
     *
     * Locked elements remain visible and can still be selected from the Layers panel,
     * but canvas editing operations such as move, resize, rotate and delete are blocked.
     * Layer ordering remains available.
     */
    var isLocked: Boolean = false

    /**
     * Draws the element using the editor's
     * image -> screen transformation matrix.
     */
    abstract fun draw(
        canvas: Canvas,
        matrix: Matrix
    )

    /**
     * Returns the element bounds in image coordinates.
     */
    abstract fun getBounds(): RectF

    /**
     * Returns true when the supplied image coordinate
     * is inside the element.
     */
    abstract fun contains(
        x: Float,
        y: Float
    ): Boolean

    /**
     * Moves the element by the supplied image-space delta.
     */
    abstract fun moveBy(
        dx: Float,
        dy: Float
    )

    /**
     * Creates an independent copy of this element for layer duplication.
     */
    abstract fun duplicate(): EditorElement
}

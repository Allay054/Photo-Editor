package com.allay.photoeditor.model

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF

sealed class EditorElement {

    var isSelected: Boolean = false

    abstract fun draw(
        canvas: Canvas,
        matrix: Matrix
    )

    abstract fun getBounds(): RectF

    abstract fun contains(
        x: Float,
        y: Float
    ): Boolean

    abstract fun moveBy(
        dx: Float,
        dy: Float
    )
}
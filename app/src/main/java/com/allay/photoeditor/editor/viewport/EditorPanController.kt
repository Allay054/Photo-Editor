package com.allay.photoeditor.editor.viewport

import android.view.MotionEvent

/**
 * Owns single-finger image panning while PhotoEditorView coordinates
 * the overall touch pipeline.
 *
 * Element gestures and pinch zoom remain separate so existing editor
 * interaction behavior is preserved.
 */
class EditorPanController(
    private val getTranslationX: () -> Float,
    private val getTranslationY: () -> Float,
    private val setTranslationX: (Float) -> Unit,
    private val setTranslationY: (Float) -> Unit,
    private val isScaleGestureInProgress: () -> Boolean,
    private val invalidate: () -> Unit
) {
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var active = false

    fun reset() {
        active = false
        lastTouchX = 0f
        lastTouchY = 0f
    }

    fun begin(x: Float, y: Float) {
        lastTouchX = x
        lastTouchY = y
        active = true
    }

    fun move(event: MotionEvent): Boolean {
        if (!active || event.pointerCount != 1 || isScaleGestureInProgress()) {
            return false
        }

        val dx = event.x - lastTouchX
        val dy = event.y - lastTouchY

        setTranslationX(getTranslationX() + dx)
        setTranslationY(getTranslationY() + dy)

        lastTouchX = event.x
        lastTouchY = event.y

        invalidate()
        return true
    }

    fun finish() {
        reset()
    }

    fun cancel() {
        reset()
    }
}

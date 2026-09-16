package com.allay.photoeditor.editor.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Owns Android gesture detector wiring for PhotoEditorView.
 *
 * The editor keeps ownership of its actual gesture behavior/state. This
 * controller only routes double-tap and pinch-scale callbacks, allowing the
 * large PhotoEditorView touch implementation to be refactored incrementally
 * without changing existing editor behavior.
 */
class EditorGestureController(
    context: Context,
    private val canScale: () -> Boolean,
    private val onScale: (Float) -> Unit,
    private val onDoubleTap: (MotionEvent) -> Boolean
) {

    private val scaleGestureDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {
                    if (!canScale()) {
                        return false
                    }

                    onScale(detector.scaleFactor)
                    return true
                }
            }
        )

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(
                    e: MotionEvent
                ): Boolean {
                    return true
                }

                override fun onDoubleTap(
                    e: MotionEvent
                ): Boolean {
                    return onDoubleTap(e)
                }
            }
        )

    /**
     * Routes one touch event through both Android gesture detectors.
     *
     * Ordering intentionally matches the previous PhotoEditorView behavior:
     * GestureDetector first, then ScaleGestureDetector.
     */
    fun onTouchEvent(
        event: MotionEvent
    ) {
        gestureDetector.onTouchEvent(event)
        scaleGestureDetector.onTouchEvent(event)
    }

    /**
     * Exposes whether the Android scale detector currently owns a multi-touch
     * gesture. PhotoEditorView uses this to preserve its existing movement
     * behavior for single-finger gestures.
     */
    val isScaleInProgress: Boolean
        get() = scaleGestureDetector.isInProgress
}

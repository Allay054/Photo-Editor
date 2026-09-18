package com.allay.photoeditor.editor.viewport

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector

/**
 * Owns pinch-zoom interaction for the editor image.
 *
 * PhotoEditorView remains the public coordinator while this controller
 * isolates ScaleGestureDetector and image scale state.
 */
class EditorZoomController(
    context: Context,
    private val getScaleFactor: () -> Float,
    private val setScaleFactor: (Float) -> Unit,
    private val isElementTransformActive: () -> Boolean,
    private val isCropModeActive: () -> Boolean,
    private val minScale: Float,
    private val maxScale: Float,
    private val invalidate: () -> Unit
) {

    private val scaleGestureDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

                override fun onScale(
                    detector: ScaleGestureDetector
                ): Boolean {
                    /*
                     * Do not zoom the image while the user is
                     * resizing an editor element.
                     */
                    if (
                        isElementTransformActive() ||
                        isCropModeActive()
                    ) {
                        return false
                    }

                    val nextScale =
                        getScaleFactor() * detector.scaleFactor

                    setScaleFactor(
                        nextScale.coerceIn(
                            minScale,
                            maxScale
                        )
                    )

                    invalidate()
                    return true
                }
            }
        )

    val isInProgress: Boolean
        get() = scaleGestureDetector.isInProgress

    fun onTouchEvent(event: MotionEvent): Boolean {
        return scaleGestureDetector.onTouchEvent(event)
    }

    fun reset() {
        // ScaleGestureDetector state is reset by the next touch sequence.
        // No persistent zoom value is changed here.
    }
}

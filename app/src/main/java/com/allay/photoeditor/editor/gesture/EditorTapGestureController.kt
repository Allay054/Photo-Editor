package com.allay.photoeditor.editor.gesture

import android.content.Context
import android.graphics.PointF
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.TextElement

/**
 * Owns tap and double-tap recognition for the editor canvas.
 *
 * PhotoEditorView remains the public coordinator while this controller keeps
 * Android GestureDetector details and text-edit double-tap behavior isolated.
 */
class EditorTapGestureController(
    context: Context,
    private val isCropModeActive: () -> Boolean,
    private val screenToImage: (Float, Float) -> PointF?,
    private val findElementAt: (Float, Float) -> EditorElement?,
    private val selectElement: (EditorElement?) -> Unit,
    private val onEditTextRequested: () -> ((TextElement) -> Unit)?
) {

    companion object {
        private const val TAG = "PhotoEditor"
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                /*
                 * Must return true so GestureDetector continues receiving
                 * the current gesture.
                 */
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isCropModeActive()) {
                    Log.d(TAG, "Double tap ignored: crop mode active")
                    return true
                }

                Log.d(
                    TAG,
                    "Double tap detected at x=${e.x}, y=${e.y}"
                )

                /*
                 * Handle double tap only on actual elements.
                 */
                val imagePoint = screenToImage(e.x, e.y)

                if (imagePoint == null) {
                    Log.d(TAG, "Double tap ignored: no image")
                    return true
                }

                val tappedElement = findElementAt(
                    imagePoint.x,
                    imagePoint.y
                )

                if (tappedElement == null) {
                    Log.d(TAG, "Double tap ignored: no element")
                    return true
                }

                Log.d(TAG, "Double tapped element: $tappedElement")

                /*
                 * Only TextElement supports text editing.
                 */
                if (tappedElement is TextElement) {
                    selectElement(tappedElement)

                    if (tappedElement.isLocked) {
                        Log.d(TAG, "Text edit ignored: element is locked")
                        return true
                    }

                    Log.d(
                        TAG,
                        "Opening text editor for: ${tappedElement.text}"
                    )

                    onEditTextRequested()?.invoke(tappedElement)
                }

                return true
            }
        }
    )

    fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector.onTouchEvent(event)
    }

    fun reset() {
        /*
         * GestureDetector keeps only internal gesture state; the next touch
         * sequence naturally starts a new gesture.
         */
    }
}
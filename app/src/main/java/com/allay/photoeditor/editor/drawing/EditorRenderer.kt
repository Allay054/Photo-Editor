package com.allay.photoeditor.editor.drawing

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.allay.photoeditor.model.EditorElement

/**
 * Handles the low-level drawing of the editor bitmap.
 *
 * This is intentionally small during the incremental refactor. PhotoEditorView
 * continues to own editor state, element rendering, selection rendering, and
 * crop overlays.
 */
class EditorRenderer(
    private val bitmapPaint: Paint,
    private val cropOverlayPaint: Paint,
    private val cropBorderPaint: Paint,
    private val cropGridPaint: Paint,
    private val cropHandlePaint: Paint,
    private val cropHandleLength: Float
) {

    /**
     * Draws the supplied bitmap using the editor's image-to-screen matrix.
     */
    fun drawBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        imageToScreenMatrix: Matrix
    ) {
        canvas.save()

        canvas.concat(imageToScreenMatrix)

        canvas.drawBitmap(
            bitmap,
            0f,
            0f,
            bitmapPaint
        )

        canvas.restore()
    }
    /**
     * Draws all editor elements using the editor image-to-screen matrix.
     *
     * The elements remain owned by PhotoEditorView; this class only owns the
     * rendering operation. Each element keeps its existing drawing behavior,
     * including its own selection rendering.
     */
    fun drawElements(
        canvas: Canvas,
        elements: List<EditorElement>,
        imageToScreenMatrix: Matrix
    ) {
        elements.forEach { element ->
            element.draw(
                canvas,
                imageToScreenMatrix
            )
        }
    }

    /**
     * Draws the selection rectangle and the rotation/resize handles for a
     * selected text element. Geometry is calculated by PhotoEditorView so
     * existing element transforms and handle positioning remain unchanged.
     */
    fun drawTextSelectionHandles(
        canvas: Canvas,
        topLeft: PointF,
        topRight: PointF,
        bottomLeft: PointF,
        bottomRight: PointF,
        rotationHandle: PointF,
        resizeHandle: PointF
    ) {
        val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = android.graphics.Color.WHITE
        }

        val path = Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }

        canvas.drawPath(path, selectionPaint)

        val topCenter = PointF(
            (topLeft.x + topRight.x) / 2f,
            (topLeft.y + topRight.y) / 2f
        )

        canvas.drawLine(
            topCenter.x,
            topCenter.y,
            rotationHandle.x,
            rotationHandle.y,
            selectionPaint
        )

        val handleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = android.graphics.Color.WHITE
        }

        canvas.drawCircle(
            rotationHandle.x,
            rotationHandle.y,
            18f,
            handleFillPaint
        )
        canvas.drawCircle(
            rotationHandle.x,
            rotationHandle.y,
            18f,
            selectionPaint
        )

        canvas.drawCircle(
            resizeHandle.x,
            resizeHandle.y,
            18f,
            handleFillPaint
        )
        canvas.drawCircle(
            resizeHandle.x,
            resizeHandle.y,
            18f,
            selectionPaint
        )
    }

    /**
     * Draws the complete crop-mode overlay using the geometry calculated by
     * PhotoEditorView. The renderer owns only the visual drawing; crop state
     * and touch behavior remain in the view/controller.
     */
    fun drawCropOverlay(
        canvas: Canvas,
        cropRect: RectF,
        viewWidth: Float,
        viewHeight: Float,
        bitmap: Bitmap,
        imageToScreenMatrix: Matrix,
        elements: List<EditorElement>
    ) {
        // Darken the complete editor.
        canvas.drawRect(
            0f,
            0f,
            viewWidth,
            viewHeight,
            cropOverlayPaint
        )

        // Redraw the selected area so the crop region remains clear.
        canvas.save()
        canvas.clipRect(cropRect)

        drawBitmap(
            canvas = canvas,
            bitmap = bitmap,
            imageToScreenMatrix = imageToScreenMatrix
        )

        drawElements(
            canvas = canvas,
            elements = elements,
            imageToScreenMatrix = imageToScreenMatrix
        )

        canvas.restore()

        drawCropGrid(
            canvas = canvas,
            cropRect = cropRect
        )

        canvas.drawRect(
            cropRect,
            cropBorderPaint
        )

        val handleLength = cropHandleLength

        // Top-left.
        canvas.drawLine(
            cropRect.left,
            cropRect.top,
            cropRect.left + handleLength,
            cropRect.top,
            cropHandlePaint
        )
        canvas.drawLine(
            cropRect.left,
            cropRect.top,
            cropRect.left,
            cropRect.top + handleLength,
            cropHandlePaint
        )

        // Top-right.
        canvas.drawLine(
            cropRect.right,
            cropRect.top,
            cropRect.right - handleLength,
            cropRect.top,
            cropHandlePaint
        )
        canvas.drawLine(
            cropRect.right,
            cropRect.top,
            cropRect.right,
            cropRect.top + handleLength,
            cropHandlePaint
        )

        // Bottom-left.
        canvas.drawLine(
            cropRect.left,
            cropRect.bottom,
            cropRect.left + handleLength,
            cropRect.bottom,
            cropHandlePaint
        )
        canvas.drawLine(
            cropRect.left,
            cropRect.bottom,
            cropRect.left,
            cropRect.bottom - handleLength,
            cropHandlePaint
        )

        // Bottom-right.
        canvas.drawLine(
            cropRect.right,
            cropRect.bottom,
            cropRect.right - handleLength,
            cropRect.bottom,
            cropHandlePaint
        )
        canvas.drawLine(
            cropRect.right,
            cropRect.bottom,
            cropRect.right,
            cropRect.bottom - handleLength,
            cropHandlePaint
        )
    }

    private fun drawCropGrid(
        canvas: Canvas,
        cropRect: RectF
    ) {
        val verticalOneThird =
            cropRect.left + cropRect.width() / 3f
        val verticalTwoThirds =
            cropRect.left + cropRect.width() * 2f / 3f
        val horizontalOneThird =
            cropRect.top + cropRect.height() / 3f
        val horizontalTwoThirds =
            cropRect.top + cropRect.height() * 2f / 3f

        canvas.save()
        canvas.clipRect(cropRect)

        canvas.drawLine(
            verticalOneThird,
            cropRect.top,
            verticalOneThird,
            cropRect.bottom,
            cropGridPaint
        )
        canvas.drawLine(
            verticalTwoThirds,
            cropRect.top,
            verticalTwoThirds,
            cropRect.bottom,
            cropGridPaint
        )
        canvas.drawLine(
            cropRect.left,
            horizontalOneThird,
            cropRect.right,
            horizontalOneThird,
            cropGridPaint
        )
        canvas.drawLine(
            cropRect.left,
            horizontalTwoThirds,
            cropRect.right,
            horizontalTwoThirds,
            cropGridPaint
        )

        canvas.restore()
    }

}

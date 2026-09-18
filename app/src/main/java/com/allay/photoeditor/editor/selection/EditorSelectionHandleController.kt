package com.allay.photoeditor.editor.selection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.allay.photoeditor.editor.core.EditorCoordinateMapper
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.EditorElement
import com.allay.photoeditor.model.ShapeElement
import com.allay.photoeditor.model.TextElement
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Owns selection-handle geometry, hit testing and selection-handle rendering.
 *
 * PhotoEditorView remains the public editor facade. This controller only
 * handles the visual/geometry layer around an already selected element.
 */
class EditorSelectionHandleController(
    private val coordinateMapper: EditorCoordinateMapper,
    private val getSelectedElement: () -> EditorElement?,
    private val drawTextTransformHandles: (
        canvas: Canvas,
        topLeft: PointF,
        topRight: PointF,
        bottomLeft: PointF,
        bottomRight: PointF,
        rotationHandle: PointF,
        resizeHandle: PointF
    ) -> Unit,
    private val rotationHandleDistance: Float,
    private val shapeDeleteHandleDistance: Float,
    private val shapeDeleteButtonRadius: Float,
    private val shapeDeleteButtonTouchRadius: Float,
    private val textDeleteHandleDistance: Float,
    private val textDeleteButtonRadius: Float,
    private val textDeleteButtonTouchRadius: Float,
    private val annotationDeleteHandleDistance: Float,
    private val annotationDeleteButtonRadius: Float,
    private val annotationDeleteButtonTouchRadius: Float,
    private val handleTouchRadius: Float
) {

    private fun imageToScreen(imageX: Float, imageY: Float): PointF? =
        coordinateMapper.imageToScreen(imageX, imageY)

    private fun imageToScreenOrOrigin(imageX: Float, imageY: Float): PointF =
        imageToScreen(imageX, imageY) ?: PointF()

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        hypot(x2 - x1, y2 - y1)

    // ---------------------------------------------------------------------
    // Annotation selection
    // ---------------------------------------------------------------------

    fun getAnnotationDeleteHandlePosition(annotation: AnnotationElement): PointF {
        val bounds = annotation.getBounds()
        val topRight = imageToScreen(bounds.right, bounds.top) ?: return PointF()
        return PointF(
            topRight.x + annotationDeleteHandleDistance,
            topRight.y - annotationDeleteHandleDistance
        )
    }

    fun isOnAnnotationDeleteHandle(eventX: Float, eventY: Float): Boolean {
        val annotation = getSelectedElement() as? AnnotationElement ?: return false
        val handle = getAnnotationDeleteHandlePosition(annotation)
        return distance(eventX, eventY, handle.x, handle.y) <= annotationDeleteButtonTouchRadius
    }

    fun drawAnnotationSelectionHandles(
        canvas: Canvas,
        annotation: AnnotationElement
    ) {
        val bounds = annotation.getBounds()
        val topLeft = imageToScreen(bounds.left, bounds.top) ?: return
        val topRight = imageToScreen(bounds.right, bounds.top) ?: return
        val bottomRight = imageToScreen(bounds.right, bounds.bottom) ?: return
        val bottomLeft = imageToScreen(bounds.left, bounds.bottom) ?: return
        val deleteHandle = getAnnotationDeleteHandlePosition(annotation)

        val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.WHITE
            alpha = 220
        }
        val selectionPath = Path().apply {
            moveTo(topLeft.x, topLeft.y)
            lineTo(topRight.x, topRight.y)
            lineTo(bottomRight.x, bottomRight.y)
            lineTo(bottomLeft.x, bottomLeft.y)
            close()
        }
        canvas.drawPath(selectionPath, selectionPaint)
        canvas.drawLine(topRight.x, topRight.y, deleteHandle.x, deleteHandle.y, selectionPaint)

        val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(220, 45, 45)
        }
        val buttonStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            color = Color.WHITE
        }
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            color = Color.WHITE
        }

        canvas.drawCircle(deleteHandle.x, deleteHandle.y, annotationDeleteButtonRadius, buttonPaint)
        canvas.drawCircle(deleteHandle.x, deleteHandle.y, annotationDeleteButtonRadius, buttonStroke)

        val iconSize = annotationDeleteButtonRadius * 0.38f
        canvas.drawLine(
            deleteHandle.x - iconSize,
            deleteHandle.y - iconSize,
            deleteHandle.x + iconSize,
            deleteHandle.y + iconSize,
            iconPaint
        )
        canvas.drawLine(
            deleteHandle.x + iconSize,
            deleteHandle.y - iconSize,
            deleteHandle.x - iconSize,
            deleteHandle.y + iconSize,
            iconPaint
        )
    }

    // ---------------------------------------------------------------------
    // Shape selection
    // ---------------------------------------------------------------------

    fun getShapeResizeHandlePosition(shape: ShapeElement): PointF =
        transformShapePoint(shape, PointF(shape.width / 2f, shape.height / 2f))

    fun getShapeRotationHandlePosition(shape: ShapeElement): PointF =
        transformShapePoint(
            shape,
            PointF(0f, -shape.height / 2f - rotationHandleDistance)
        )

    private fun transformShapePoint(shape: ShapeElement, localPoint: PointF): PointF {
        val radians = Math.toRadians(shape.rotation.toDouble())
        val cosValue = cos(radians).toFloat()
        val sinValue = sin(radians).toFloat()
        val scaledX = localPoint.x * shape.scale
        val scaledY = localPoint.y * shape.scale
        val rotatedX = scaledX * cosValue - scaledY * sinValue
        val rotatedY = scaledX * sinValue + scaledY * cosValue
        return imageToScreenOrOrigin(
            shape.position.x + rotatedX,
            shape.position.y + rotatedY
        )
    }

    fun getShapeDeleteHandlePosition(shape: ShapeElement): PointF =
        transformShapePoint(
            shape,
            PointF(
                shape.width / 2f + shapeDeleteHandleDistance,
                -shape.height / 2f - shapeDeleteHandleDistance
            )
        )

    fun isOnShapeRotationHandle(eventX: Float, eventY: Float): Boolean {
        val shape = getSelectedElement() as? ShapeElement ?: return false
        val handle = getShapeRotationHandlePosition(shape)
        return distance(eventX, eventY, handle.x, handle.y) <= shapeDeleteButtonTouchRadius
    }

    fun isOnShapeDeleteHandle(eventX: Float, eventY: Float): Boolean {
        val shape = getSelectedElement() as? ShapeElement ?: return false
        val handle = getShapeDeleteHandlePosition(shape)
        return distance(eventX, eventY, handle.x, handle.y) <= handleTouchRadius
    }

    fun isOnShapeResizeHandle(eventX: Float, eventY: Float): Boolean {
        val shape = getSelectedElement() as? ShapeElement ?: return false
        val handle = getShapeResizeHandlePosition(shape)
        return distance(eventX, eventY, handle.x, handle.y) <= handleTouchRadius
    }

    fun drawShapeSelectionHandles(canvas: Canvas, shape: ShapeElement) {
        val halfWidth = shape.width / 2f
        val halfHeight = shape.height / 2f
        val topLeft = transformShapePoint(shape, PointF(-halfWidth, -halfHeight))
        val topRight = transformShapePoint(shape, PointF(halfWidth, -halfHeight))
        val bottomLeft = transformShapePoint(shape, PointF(-halfWidth, halfHeight))
        val bottomRight = transformShapePoint(shape, PointF(halfWidth, halfHeight))
        val rotationHandle = getShapeRotationHandlePosition(shape)
        val resizeHandle = getShapeResizeHandlePosition(shape)
        val deleteHandle = getShapeDeleteHandlePosition(shape)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.WHITE
        }
        val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.BLACK
        }

        canvas.drawLine(topLeft.x, topLeft.y, topRight.x, topRight.y, paint)
        canvas.drawLine(topRight.x, topRight.y, bottomRight.x, bottomRight.y, paint)
        canvas.drawLine(bottomRight.x, bottomRight.y, bottomLeft.x, bottomLeft.y, paint)
        canvas.drawLine(bottomLeft.x, bottomLeft.y, topLeft.x, topLeft.y, paint)
        canvas.drawLine(
            (topLeft.x + topRight.x) / 2f,
            (topLeft.y + topRight.y) / 2f,
            rotationHandle.x,
            rotationHandle.y,
            paint
        )
        canvas.drawLine(topRight.x, topRight.y, deleteHandle.x, deleteHandle.y, paint)

        val radius = 11f
        listOf(topLeft, topRight, bottomLeft, bottomRight, rotationHandle, resizeHandle).forEach { point ->
            canvas.drawCircle(point.x, point.y, radius, handleFill)
            canvas.drawCircle(point.x, point.y, radius, handleStroke)
        }

        drawDeleteButton(canvas, deleteHandle, shapeDeleteButtonRadius)
    }

    // ---------------------------------------------------------------------
    // Text selection
    // ---------------------------------------------------------------------

    fun getTextDeleteHandlePosition(textElement: TextElement): PointF {
        val bounds = textElement.getBounds()
        val topRight = imageToScreen(bounds.right, bounds.top) ?: return PointF()
        return PointF(
            topRight.x + textDeleteHandleDistance,
            topRight.y - textDeleteHandleDistance
        )
    }

    fun isOnTextDeleteHandle(eventX: Float, eventY: Float): Boolean {
        val textElement = getSelectedElement() as? TextElement ?: return false
        val handle = getTextDeleteHandlePosition(textElement)
        return distance(eventX, eventY, handle.x, handle.y) <= textDeleteButtonTouchRadius
    }

    fun drawTextDeleteButton(canvas: Canvas, textElement: TextElement) {
        val deleteHandle = getTextDeleteHandlePosition(textElement)
        val bounds = textElement.getBounds()
        val topRight = imageToScreen(bounds.right, bounds.top) ?: return

        val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.WHITE
            alpha = 180
        }
        canvas.drawLine(topRight.x, topRight.y, deleteHandle.x, deleteHandle.y, connectorPaint)

        drawDeleteButton(canvas, deleteHandle, textDeleteButtonRadius)
    }

    fun getRotationHandlePosition(textElement: TextElement): PointF {
        val bounds = textElement.getBounds()
        val topCenter = imageToScreen(
            (bounds.left + bounds.right) / 2f,
            bounds.top
        ) ?: return PointF()
        return PointF(topCenter.x, topCenter.y - rotationHandleDistance)
    }

    fun getResizeHandlePosition(textElement: TextElement): PointF {
        val bounds = textElement.getBounds()
        return imageToScreenOrOrigin(bounds.right, bounds.bottom)
    }

    fun isOnRotationHandle(eventX: Float, eventY: Float): Boolean {
        val element = getSelectedElement() as? TextElement ?: return false
        val handle = getRotationHandlePosition(element)
        return distance(eventX, eventY, handle.x, handle.y) <= handleTouchRadius
    }

    fun isOnResizeHandle(eventX: Float, eventY: Float): Boolean {
        val element = getSelectedElement() as? TextElement ?: return false
        val handle = getResizeHandlePosition(element)
        return distance(eventX, eventY, handle.x, handle.y) <= handleTouchRadius
    }

    fun drawTextSelectionHandles(canvas: Canvas, textElement: TextElement) {
        val bounds = textElement.getBounds()
        val topLeft = imageToScreen(bounds.left, bounds.top) ?: PointF()
        val topRight = imageToScreen(bounds.right, bounds.top) ?: PointF()
        val bottomLeft = imageToScreen(bounds.left, bounds.bottom) ?: PointF()
        val bottomRight = imageToScreen(bounds.right, bounds.bottom) ?: PointF()
        val rotationHandle = getRotationHandlePosition(textElement)
        val resizeHandle = getResizeHandlePosition(textElement)

        drawTextTransformHandles(
            canvas,
            topLeft,
            topRight,
            bottomLeft,
            bottomRight,
            rotationHandle,
            resizeHandle
        )
    }

    private fun drawDeleteButton(
        canvas: Canvas,
        deleteHandle: PointF,
        radius: Float
    ) {
        val deleteShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.BLACK
            alpha = 150
        }
        val deleteButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.rgb(220, 45, 45)
        }
        val deleteButtonStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
            color = Color.WHITE
        }

        canvas.drawCircle(deleteHandle.x, deleteHandle.y + 3f, radius + 2f, deleteShadowPaint)
        canvas.drawCircle(deleteHandle.x, deleteHandle.y, radius, deleteButtonPaint)
        canvas.drawCircle(deleteHandle.x, deleteHandle.y, radius, deleteButtonStroke)

        val trashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        val trashBody = RectF(
            deleteHandle.x - 8f,
            deleteHandle.y - 6f,
            deleteHandle.x + 8f,
            deleteHandle.y + 9f
        )
        canvas.drawRoundRect(trashBody, 2f, 2f, trashPaint)
        canvas.drawRect(
            deleteHandle.x - 10f,
            deleteHandle.y - 10f,
            deleteHandle.x + 10f,
            deleteHandle.y - 6f,
            trashPaint
        )
        canvas.drawRoundRect(
            RectF(
                deleteHandle.x - 4f,
                deleteHandle.y - 13f,
                deleteHandle.x + 4f,
                deleteHandle.y - 9f
            ),
            1.5f,
            1.5f,
            trashPaint
        )

        val slotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.8f
            strokeCap = Paint.Cap.ROUND
            color = Color.rgb(220, 45, 45)
        }
        canvas.drawLine(
            deleteHandle.x - 3f,
            deleteHandle.y - 3f,
            deleteHandle.x - 3f,
            deleteHandle.y + 5f,
            slotPaint
        )
        canvas.drawLine(
            deleteHandle.x + 3f,
            deleteHandle.y - 3f,
            deleteHandle.x + 3f,
            deleteHandle.y + 5f,
            slotPaint
        )
    }
}

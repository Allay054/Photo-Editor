package com.allay.photoeditor.editor.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.util.Log
import com.allay.photoeditor.editor.annotation.EditorAnnotationEffectController
import com.allay.photoeditor.editor.drawing.EditorRenderer
import com.allay.photoeditor.model.AnnotationElement
import com.allay.photoeditor.model.AnnotationType
import com.allay.photoeditor.model.EditorElement

/**
 * Creates a bitmap containing the actual editor content without any editor UI.
 *
 * Export is deliberately isolated from PhotoEditorView's interactive state.
 * It never changes elements, selection, viewport state, the committed bitmap,
 * or Undo/Redo history.
 */
class EditorExportController(
    private val getBitmap: () -> Bitmap?,
    private val elements: List<EditorElement>,
    private val editorRenderer: EditorRenderer,
    private val annotationEffectController: EditorAnnotationEffectController,
    private val bitmapPaint: Paint
) {

    companion object {
        private const val TAG = "EditorExport"
    }

    /**
     * Generates the final editor bitmap at the source image resolution.
     *
     * The viewport zoom/pan and selection/crop UI are intentionally ignored.
     * All element positions remain in image coordinates, so an identity matrix
     * preserves the original image resolution and element quality.
     */
    fun createFinalBitmap(): Bitmap? {
        val sourceBitmap = getBitmap() ?: return null

        if (sourceBitmap.width <= 0 || sourceBitmap.height <= 0) {
            return null
        }

        return try {
            val outputBitmap = Bitmap.createBitmap(
                sourceBitmap.width,
                sourceBitmap.height,
                Bitmap.Config.ARGB_8888
            )

            val outputCanvas = Canvas(outputBitmap)

            // Start with the committed editor bitmap. No editor state is changed.
            outputCanvas.drawBitmap(
                sourceBitmap,
                0f,
                0f,
                bitmapPaint
            )

            val identityMatrix = Matrix()
            val annotationElements = elements
                .filterIsInstance<AnnotationElement>()
                .filter { it.isVisible }

            // Blur and Pixelate are rendered before normal elements so text,
            // shapes and regular annotations stay sharp, matching onDraw().
            drawBlurAnnotationsForExport(
                canvas = outputCanvas,
                sourceBitmap = sourceBitmap,
                annotations = annotationElements
            )

            drawPixelateAnnotationsForExport(
                canvas = outputCanvas,
                sourceBitmap = sourceBitmap,
                annotations = annotationElements
            )

            // Pixelate is already rendered as an image effect above. Exclude it
            // from normal element drawing. Eraser is an editing operation, not
            // a drawable layer of its own.
            val drawableElements = elements.filter { element ->
                element.isVisible &&
                        element !is AnnotationElement ||
                        (
                                element.isVisible &&
                                        element is AnnotationElement &&
                                        element.annotationType != AnnotationType.PIXELATE &&
                                        element.annotationType != AnnotationType.ERASER
                                )
            }

            editorRenderer.drawElements(
                canvas = outputCanvas,
                elements = drawableElements,
                imageToScreenMatrix = identityMatrix
            )

            outputBitmap
        } catch (exception: OutOfMemoryError) {
            Log.e(TAG, "Unable to create export bitmap: out of memory", exception)
            null
        } catch (exception: Exception) {
            Log.e(TAG, "Unable to create export bitmap", exception)
            null
        }
    }

    private fun drawBlurAnnotationsForExport(
        canvas: Canvas,
        sourceBitmap: Bitmap,
        annotations: List<AnnotationElement>
    ) {
        val blurAnnotations = annotations.filter {
            it.annotationType == AnnotationType.BLUR
        }
        if (blurAnnotations.isEmpty()) return

        val blurred = annotationEffectController
            .getOrCreateBlurredBitmap(sourceBitmap)
            ?: return

        val identityMatrix = Matrix()

        blurAnnotations.forEach { annotation ->
            annotationEffectController.blurMaskPaint.style = Paint.Style.STROKE
            annotationEffectController.blurMaskPaint.strokeWidth = annotation.strokeWidth
            annotationEffectController.blurMaskPaint.xfermode = null

            val maskPath = Path()
            annotationEffectController.blurMaskPaint.getFillPath(
                annotation.path,
                maskPath
            )

            canvas.saveLayer(null, null)
            canvas.drawBitmap(
                blurred,
                identityMatrix,
                bitmapPaint
            )

            annotationEffectController.blurMaskPaint.xfermode =
                android.graphics.PorterDuffXfermode(
                    android.graphics.PorterDuff.Mode.DST_IN
                )
            canvas.drawPath(maskPath, annotationEffectController.blurMaskPaint)
            annotationEffectController.blurMaskPaint.xfermode = null
            canvas.restore()
        }
    }

    private fun drawPixelateAnnotationsForExport(
        canvas: Canvas,
        sourceBitmap: Bitmap,
        annotations: List<AnnotationElement>
    ) {
        val pixelateAnnotations = annotations.filter {
            it.annotationType == AnnotationType.PIXELATE
        }
        if (pixelateAnnotations.isEmpty()) return

        val pixelated = annotationEffectController
            .getOrCreatePixelatedBitmap(sourceBitmap)
            ?: return

        val identityMatrix = Matrix()

        pixelateAnnotations.forEach { annotation ->
            annotationEffectController.blurMaskPaint.style = Paint.Style.STROKE
            annotationEffectController.blurMaskPaint.strokeWidth = annotation.strokeWidth
            annotationEffectController.blurMaskPaint.xfermode = null

            val maskPath = Path()
            annotationEffectController.blurMaskPaint.getFillPath(
                annotation.path,
                maskPath
            )

            canvas.save()
            canvas.clipPath(maskPath)
            canvas.drawBitmap(
                pixelated,
                identityMatrix,
                bitmapPaint
            )

            annotationEffectController.pixelateColorPaint.color = annotation.color
            canvas.drawPath(
                maskPath,
                annotationEffectController.pixelateColorPaint
            )
            canvas.restore()
        }
    }
}
